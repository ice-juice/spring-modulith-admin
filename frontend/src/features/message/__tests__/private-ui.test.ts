import { expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { i18n, loadLocaleModule, setLocale } from '@/locales'
import PrivateView from '@/views/message/PrivateView.vue'
import { useAuth } from '@/stores/auth'
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('@/api/http', () => api)
it('renders a private message as text and marks it read only after the recipient opens it', async () => {
  const pinia = createPinia()
  setActivePinia(pinia)
  await loadLocaleModule('message')
  setLocale('en-US')
  useAuth().user = {
    id: '9007199254740993',
    username: 'receiver',
    name: 'Receiver',
    portal: 'admin',
    superAdmin: false,
    permissions: [],
    menus: [],
    navigation: [],
  }
  const row = {
    id: '9007199254740995',
    senderId: '9007199254740997',
    recipientId: '9007199254740993',
    senderName: 'Sender',
    recipientName: 'Receiver',
    content: '<img src=x onerror=attack()>',
    createdAt: '2026-11-10T00:00:00Z',
    readAt: null,
  }
  api.get.mockImplementation(async (path: string) =>
    path === '/message/summary'
      ? { unreadNotices: '0', pendingTodos: '0', unreadMessages: '0' }
      : { items: [row], total: '1' },
  )
  api.put.mockResolvedValue({ ...row, readAt: '2026-11-10T01:00:00Z' })
  const wrapper = mount(PrivateView, { global: { plugins: [pinia, ElementPlus, i18n] } })
  await flushPromises()
  expect(wrapper.find('img').exists()).toBe(false)
  expect(api.put).not.toHaveBeenCalled()
  await wrapper
    .find('.el-table__body-wrapper')
    .findAll('button')
    .find((button) => button.text().includes('<img'))!
    .trigger('click')
  await flushPromises()
  expect(api.put).toHaveBeenCalledWith('/message/private/' + row.id + '/read')
  expect(wrapper.find('img').exists()).toBe(false)
  wrapper.unmount()
})
