import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises } from '@vue/test-utils'
import { useAuth, type CurrentUser } from '@/stores/auth'
import { useMessages } from '@/stores/messages'
import { saveToken } from '@/lib/token'
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('@/api/http', () => api)
class Socket {
  static instances: Socket[] = []
  closed = false
  onopen: (() => unknown) | null = null
  onclose: (() => unknown) | null = null
  onerror: (() => unknown) | null = null
  onmessage: ((event: { data: string }) => unknown) | null = null
  constructor(public url: string) {
    Socket.instances.push(this)
  }
  close() {
    this.closed = true
    this.onclose?.()
  }
}
const actor: CurrentUser = {
  id: '9007199254740993',
  username: 'user',
  name: 'User',
  portal: 'admin',
  superAdmin: false,
  permissions: [],
  menus: [],
  navigation: [],
}
beforeEach(() => {
  setActivePinia(createPinia())
  vi.useFakeTimers()
  vi.stubGlobal('WebSocket', Socket)
  Socket.instances = []
  localStorage.clear()
  sessionStorage.clear()
  useAuth().user = { ...actor }
  api.get.mockImplementation(async (path: string) =>
    path === '/auth/me'
      ? { ...actor, locked: true }
      : { unreadNotices: '1', pendingTodos: '2', unreadMessages: '3' },
  )
  api.post.mockResolvedValue({ ticket: 'one-use-ticket' })
})
afterEach(() => {
  useMessages().stop()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.clearAllMocks()
})
it('uses a handshake ticket and closes without reconnecting after a forced logout', async () => {
  const store = useMessages()
  store.start()
  await flushPromises()
  const socket = Socket.instances[0]!
  expect(socket.url).toContain('/api/message/ws?ticket=one-use-ticket')
  expect(socket.url).not.toContain('Bearer')
  socket.onopen?.()
  await flushPromises()
  expect(store.state).toBe('connected')
  expect(store.summary.pendingTodos).toBe('2')
  await socket.onmessage?.({ data: JSON.stringify({ type: 'session-ended', reason: 'FORCED' }) })
  expect(useAuth().user).toBeNull()
  expect(socket.closed).toBe(true)
  await vi.advanceTimersByTimeAsync(40000)
  expect(Socket.instances).toHaveLength(1)
})
it('keeps the account and pauses when the server reports a locked session', async () => {
  saveToken('test-only-token', false)
  const store = useMessages()
  store.start()
  await flushPromises()
  const socket = Socket.instances[0]!
  await socket.onmessage?.({
    data: JSON.stringify({ type: 'session-ended', reason: 'SESSION_INVALID' }),
  })
  expect(useAuth().user?.id).toBe(actor.id)
  expect(useAuth().user?.locked).toBe(true)
  expect(socket.closed).toBe(true)
  await vi.advanceTimersByTimeAsync(40000)
  expect(Socket.instances).toHaveLength(1)
})
it('reconnects after a transport loss and refreshes message pages on a push', async () => {
  const store = useMessages()
  store.start()
  await flushPromises()
  const first = Socket.instances[0]!
  first.onopen?.()
  first.close()
  await vi.advanceTimersByTimeAsync(1000)
  await flushPromises()
  expect(Socket.instances).toHaveLength(2)
  const event = vi.fn()
  window.addEventListener('sma:message', event, { once: true })
  await Socket.instances[1]!.onmessage?.({
    data: JSON.stringify({ type: 'notice', id: '9007199254740995' }),
  })
  expect(event).toHaveBeenCalledOnce()
})
it('does not attach an obsolete handshake after stopping and starting for another account', async () => {
  let resolveOld!: (value: { ticket: string }) => void
  api.post.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
  const store = useMessages()
  store.start(); await flushPromises(); store.stop()
  useAuth().user = { ...actor, id: 'other-account' }
  store.start(); await flushPromises()
  expect(Socket.instances).toHaveLength(1)
  resolveOld({ ticket: 'obsolete-ticket' }); await flushPromises()
  expect(Socket.instances).toHaveLength(1)
  expect(Socket.instances[0]?.url).not.toContain('obsolete-ticket')
})
it('does not accept a summary belonging to a previous account', async () => {
  let resolveOld!: (value: { unreadNotices: string; pendingTodos: string; unreadMessages: string }) => void
  api.get.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
  const store = useMessages()
  const old = store.refresh()
  store.stop(); useAuth().user = { ...actor, id: 'other-account' }
  resolveOld({ unreadNotices: '999', pendingTodos: '999', unreadMessages: '999' }); await old
  expect(store.summary.unreadNotices).toBe('0')
})
