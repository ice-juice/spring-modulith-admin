import { expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus, { ElDialog } from 'element-plus'
import { i18n, loadLocaleModule, setLocale } from '@/locales'
import ApplicationsView from '@/views/openapi/ApplicationsView.vue'
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), remove: vi.fn() }))
vi.mock('@/api/http', () => api)
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
it('shows generated application credentials once and removes the secret when its dialog closes', async () => {
  const pinia = createPinia()
  setActivePinia(pinia)
  await loadLocaleModule('openapi')
  setLocale('en-US')
  api.get.mockImplementation(async (path: string) =>
    path.endsWith('/types') ? [{ value: '1', label: 'Web' }] : { items: [], total: '0' },
  )
  const secret = 'test-secret-shown-once'
  api.post.mockResolvedValue({ id: '9007199254740993', appId: 'a'.repeat(32), appSecret: secret })
  const wrapper = mount(ApplicationsView, {
    global: { plugins: [pinia, ElementPlus, i18n], directives: { permission: { mounted() {} } } },
  })
  await flushPromises()
  await wrapper
    .findAll('button')
    .find((button) => button.text() === 'Create application')!
    .trigger('click')
  await flushPromises()
  await wrapper.find('input[maxlength="128"]').setValue('New application')
  await wrapper
    .findAll('button')
    .find((button) => button.text() === 'Save')!
    .trigger('click')
  await flushPromises()
  expect(api.post).toHaveBeenCalledWith(
    '/openapi/apps',
    expect.objectContaining({ name: 'New application', signatureRequired: true, enabled: false }),
  )
  expect(wrapper.find('input[type="password"]').element).toHaveProperty('value', secret)
  const dialog = wrapper
    .findAllComponents(ElDialog)
    .find((dialog) => dialog.props('title') === 'Application credentials')!
  dialog.vm.$emit('close')
  await flushPromises()
  expect(
    wrapper.findAll('input').some((input) => (input.element as HTMLInputElement).value === secret),
  ).toBe(false)
  wrapper.unmount()
})
