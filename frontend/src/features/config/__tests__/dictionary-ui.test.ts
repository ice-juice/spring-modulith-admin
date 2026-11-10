import { expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus, { ElDialog } from 'element-plus'
import { i18n, loadLocaleModule, setLocale } from '@/locales'
import { useAuth, type CurrentUser } from '@/stores/auth'
import { permission } from '@/directives/permission'
import DictionaryView from '@/views/config/DictionaryView.vue'
import ManagementView from '@/views/iam/ManagementView.vue'

const api = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  remove: vi.fn(),
  replace: vi.fn(),
}))
vi.mock('@/api/http', () => api)
vi.mock('vue-router', () => ({
  useRoute: () => ({ meta: {}, query: {} }),
  useRouter: () => ({ replace: api.replace, push: vi.fn() }),
}))

it('opens a dictionary type editor without selecting its row and hiding the dialog', async () => {
  const pinia = createPinia()
  setActivePinia(pinia)
  await loadLocaleModule('config')
  setLocale('en-US')
  useAuth().user = {
    id: 'admin',
    permissions: ['config:dictionary:view', 'config:dictionary:update'],
    menus: [],
    navigation: [],
  } as unknown as CurrentUser
  api.get.mockImplementation(async (url: string) =>
    url.endsWith('/types')
      ? [{ id: 't1', name: 'Flags', typeKey: 'flags', tree: false, enabled: true, sort: 0 }]
      : [],
  )
  const wrapper = mount(DictionaryView, {
    attachTo: document.body,
    global: { plugins: [pinia, ElementPlus, i18n], directives: { permission } },
  })
  await flushPromises()
  await wrapper
    .findAll('[role="tab"]')
    .find((tab) => tab.text() === 'Dictionary types')!
    .trigger('click')
  await wrapper
    .find('#pane-types')
    .findAll('button')
    .find((button) => button.text() === 'Edit')!
    .trigger('click')
  await flushPromises()
  const types = wrapper
    .findAllComponents(ManagementView)
    .find((view) => view.props('resourceId') === 'dictionaryTypes')!
  expect(types.findComponent(ElDialog).props('modelValue')).toBe(true)
  expect(api.replace).not.toHaveBeenCalled()
  expect(wrapper.find('#tab-types').attributes('aria-selected')).toBe('true')
  wrapper.unmount()
})
