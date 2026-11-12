import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, disposePinia, getActivePinia, setActivePinia } from 'pinia'
import { flushPromises } from '@vue/test-utils'
import { useAuth, type CurrentUser } from '@/stores/auth'
import { useMessages } from '@/stores/messages'
import { getToken, saveToken } from '@/lib/token'
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('@/api/http', () => api)
class Socket {
  static instances: Socket[] = []
  closed = false
  onopen: (() => unknown) | null = null
  onclose: ((event?: { code: number }) => unknown) | null = null
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
  saveToken('original-message-session', false)
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
  disposePinia(getActivePinia()!)
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

it('keeps polling authorized REST state while the socket is offline and ticket issuance fails', async () => {
  const store = useMessages()
  store.start(); await flushPromises()
  const socket = Socket.instances[0]!
  socket.onopen?.(); await flushPromises()
  api.post.mockRejectedValue(new Error('test-only delivery outage'))
  socket.close()
  api.get.mockResolvedValue({ unreadNotices: '4', pendingTodos: '5', unreadMessages: '9' })
  await vi.advanceTimersByTimeAsync(30000); await flushPromises()
  expect(Socket.instances).toHaveLength(1)
  expect(store.state).toBe('offline')
  expect(store.summary.unreadMessages).toBe('9')
  expect(useAuth().user?.id).toBe(actor.id)
})

it('rejects a late handshake from an older token of the same account and binds each request before dispatch', async () => {
  let resolveOld!: (value: { ticket: string }) => void
  api.post.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
  const store = useMessages(); store.start(); await flushPromises()
  expect(api.post).toHaveBeenCalledWith('/message/socket-ticket', undefined, { expectedIdentity: 'original-message-session' })
  saveToken('replacement-message-session', false); window.dispatchEvent(new Event('storage')); await flushPromises()
  expect(Socket.instances).toHaveLength(1)
  resolveOld({ ticket: 'obsolete-same-account-ticket' }); await flushPromises()
  expect(Socket.instances).toHaveLength(1); expect(Socket.instances[0]!.url).not.toContain('obsolete-same-account-ticket')
  expect(api.post).toHaveBeenLastCalledWith('/message/socket-ticket', undefined, { expectedIdentity: 'replacement-message-session' })
})

it('old forced-logout socket events cannot reset a replacement session for the same user', async () => {
  const store = useMessages(); store.start(); await flushPromises()
  const old = Socket.instances[0]!
  saveToken('replacement-message-session', false); useAuth().user = { ...actor }; await flushPromises()
  expect(old.closed).toBe(true); expect(Socket.instances).toHaveLength(2)
  await old.onmessage?.({ data: JSON.stringify({ type: 'session-ended', reason: 'FORCED' }) })
  expect(useAuth().user?.id).toBe(actor.id); expect(Socket.instances[1]!.closed).toBe(false)
})

it('late session-invalid validation cannot stop or close the new connection', async () => {
  const store = useMessages(); store.start(); await flushPromises()
  const old = Socket.instances[0]!
  let complete!: (value: CurrentUser) => void
  api.get.mockImplementationOnce(() => new Promise(resolve => { complete = resolve }))
  const pending = old.onmessage?.({ data: JSON.stringify({ type: 'session-ended', reason: 'SESSION_INVALID' }) })
  saveToken('replacement-message-session', false); useAuth().user = { ...actor }; await flushPromises()
  complete({ ...actor }); await pending
  expect(Socket.instances).toHaveLength(2); expect(Socket.instances[1]!.closed).toBe(false); expect(useAuth().user?.id).toBe(actor.id)
})

it('clears private counts on locking and rejects the old summary even after unlocking', async () => {
  const store = useMessages(); store.start(); await flushPromises(); expect(store.summary.unreadNotices).toBe('1')
  let complete!: (value: { unreadNotices: string; pendingTodos: string; unreadMessages: string }) => void
  api.get.mockImplementationOnce(() => new Promise(resolve => { complete = resolve }))
  const pending = store.refresh(); useAuth().user!.locked = true
  expect(store.summary.unreadNotices).toBe('0'); expect(Socket.instances[0]!.closed).toBe(true)
  useAuth().user!.locked = false; await flushPromises()
  complete({ unreadNotices: '999', pendingTodos: '999', unreadMessages: '999' }); await pending
  expect(store.summary.unreadNotices).toBe('1')
  expect(api.get).toHaveBeenCalledWith('/message/summary', undefined, { expectedIdentity: 'original-message-session' })
})

it('synchronously gates twenty connection calls and never issues an anonymous ticket', async () => {
  const store = useMessages(); store.start()
  for (let i = 0; i < 20; i++) void store.connect()
  await flushPromises(); expect(api.post).toHaveBeenCalledOnce()
  useAuth().reset(); await store.connect(); expect(api.post).toHaveBeenCalledOnce()
})

it('keeps the shared login after deployment close 1001, obtains a new ticket and refreshes REST on reconnect', async () => {
  const store = useMessages()
  api.post.mockResolvedValueOnce({ ticket: 'before-drain' }).mockResolvedValueOnce({ ticket: 'after-drain' })
  store.start(); await flushPromises()
  const first = Socket.instances[0]!
  first.onopen?.(); await flushPromises()
  const reset = vi.spyOn(useAuth(), 'reset')
  first.onclose?.({ code: 1001 })
  expect(store.state).toBe('offline')
  expect(getToken()).toBe('original-message-session')
  expect(useAuth().user?.id).toBe(actor.id)
  await vi.advanceTimersByTimeAsync(999)
  expect(Socket.instances).toHaveLength(1)
  await vi.advanceTimersByTimeAsync(1); await flushPromises()
  expect(Socket.instances).toHaveLength(2)
  expect(Socket.instances[1]!.url).toContain('ticket=after-drain')
  expect(api.post).toHaveBeenCalledTimes(2)
  expect(api.post).toHaveBeenLastCalledWith('/message/socket-ticket', undefined, { expectedIdentity: 'original-message-session' })
  api.get.mockResolvedValue({ unreadNotices: '7', pendingTodos: '8', unreadMessages: '9' })
  Socket.instances[1]!.onopen?.(); await flushPromises()
  expect(store.state).toBe('connected'); expect(store.summary.unreadMessages).toBe('9')
  expect(reset).not.toHaveBeenCalled()
  reset.mockRestore()
})
