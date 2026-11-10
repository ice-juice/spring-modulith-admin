import { expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { i18n, loadLocaleModule, setLocale } from '@/locales'
import TasksView from '@/views/message/TasksView.vue'
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), remove: vi.fn() })),
  id = '9007199254740993'
vi.mock('@/api/http', () => api)
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ query: { focus: '9007199254740993', tab: 'tasks' } }),
}))
it.each([false, true])(
  'opens a snowflake task link with managed=%s through its correct API',
  async (managed) => {
    const pinia = createPinia()
    setActivePinia(pinia)
    await loadLocaleModule('message')
    setLocale('en-US')
    const path = '/message/todos/' + (managed ? 'managed/' : '') + id
    api.get.mockImplementation(async (url: string) =>
      url.includes('/config/')
        ? []
        : url === path
          ? { id, title: 'Deep task', source: {}, dueAt: '2026-11-10T08:00:00Z' }
          : { items: [], total: '0' },
    )
    const wrapper = mount(TasksView, {
      props: { managed },
      global: { plugins: [pinia, ElementPlus, i18n], directives: { permission: { mounted() {} } } },
    })
    await flushPromises()
    expect(api.get).toHaveBeenCalledWith(path)
    expect(wrapper.text()).toContain('Deep task')
    wrapper.unmount()
    vi.clearAllMocks()
  },
)
