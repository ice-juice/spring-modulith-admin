import { expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { i18n, loadLocaleModule, setLocale } from '@/locales'
import { useAuth } from '@/stores/auth'
import HomeView from '@/views/HomeView.vue'
const api = vi.hoisted(() => ({ get: vi.fn(), push: vi.fn() }))
vi.mock('@/api/http', () => ({ get: api.get }))
vi.mock('axios', () => ({ default: { get: async () => ({ data: { status: 'UP' } }) } }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: api.push }) }))
it('displays contributed statistics and opens the exact pending-work link', async () => {
  const pinia = createPinia()
  setActivePinia(pinia)
  await loadLocaleModule('workspace')
  setLocale('en-US')
  useAuth().user = {
    id: '1',
    username: 'user',
    name: 'User',
    portal: 'admin',
    superAdmin: false,
    permissions: [],
    menus: [],
    navigation: [],
  }
  const path = '/message/inbox?tab=tasks&focus=9007199254740993'
  api.get.mockResolvedValue({
    metrics: [
      { id: 'message.todos', name: 'Pending tasks', value: '1', path: '/message/inbox?tab=tasks' },
    ],
    entries: [{ kind: 'Task', title: 'Review request', path, time: '2026-11-10T08:00:00Z' }],
  })
  const wrapper = mount(HomeView, { global: { plugins: [pinia, ElementPlus, i18n] } })
  await flushPromises()
  expect(wrapper.text()).toContain('Pending tasks')
  await wrapper
    .findAll('button')
    .find((button) => button.text() === 'Review request')!
    .trigger('click')
  expect(api.push).toHaveBeenCalledWith(path)
  wrapper.unmount()
})
