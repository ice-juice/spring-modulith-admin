import { beforeEach, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus, { ElInput } from 'element-plus'
import { i18n, loadLocaleModule, setLocale } from '@/locales'
import { get, put } from '@/api/http'
import { useAuth, type CurrentUser } from '@/stores/auth'
import { permission } from '@/directives/permission'
import ManagementView from '@/views/iam/ManagementView.vue'
vi.mock('vue-router', () => ({ useRoute: () => ({ meta: {}, query: {} }) }))
vi.mock('@/api/http', () => ({ get: vi.fn(), put: vi.fn(), post: vi.fn(), remove: vi.fn() }))
beforeEach(async () => {
  setActivePinia(createPinia())
  await loadLocaleModule('config')
  setLocale('en-US')
  useAuth().user = {
    id: 'admin',
    permissions: ['config:parameter:view', 'config:parameter:update'],
    menus: [],
    navigation: [],
  } as unknown as CurrentUser
  vi.mocked(get).mockImplementation(async (url) =>
    url === '/config/parameters/groups'
      ? [{ id: 'g1', groupKey: 'business', name: 'Business' }]
      : [
          {
            id: 'p1',
            name: 'API token',
            defaultName: 'API token',
            paramKey: 'api.secret',
            groupKey: 'business',
            valueType: 'STRING',
            value: null,
            secret: true,
            publicReadable: false,
            sort: 0,
            translations: {},
          },
        ],
  )
  vi.mocked(put).mockResolvedValue(undefined)
})
it('keeps a masked secret when only the parameter name changes', async () => {
  const wrapper = mount(ManagementView, {
    props: { resourceId: 'parameters' },
    attachTo: document.body,
    global: { plugins: [ElementPlus, i18n], directives: { permission } },
  })
  await flushPromises()
  expect(wrapper.text()).toContain('Sensitive value')
  await wrapper
    .findAll('button')
    .find((button) => button.text() === 'Edit')!
    .trigger('click')
  await flushPromises()
  const input = wrapper
    .findAllComponents(ElInput)
    .find((component) => component.props('modelValue') === 'API token')!
  input.vm.$emit('update:modelValue', 'Updated token name')
  const save = wrapper
    .findAllComponents({ name: 'ElButton' })
    .find((button) => button.text() === 'Save')!
  await save.trigger('click')
  await flushPromises()
  expect(put).toHaveBeenCalledWith(
    '/config/parameters/p1',
    expect.objectContaining({ name: 'Updated token name', value: null, secret: true }),
  )
  wrapper.unmount()
})
