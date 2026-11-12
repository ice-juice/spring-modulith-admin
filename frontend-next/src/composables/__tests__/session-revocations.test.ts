import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, disposePinia } from 'pinia'
import { i18n } from '@/locales'
import { ApiError, get, post, type PageResult } from '@/api/http'
import { useAuth, type CurrentUser } from '@/stores/auth'
import { saveToken, getToken } from '@/lib/token'
import { confirmAction } from '@/lib/feedback'
import { OperationRegistry } from '@/lib/operation-registry'
import { operationOwner } from '@/lib/operation-owner'
import { useSessionRevocations, type SessionView } from '../useSessionRevocations'
vi.mock('@/api/http', async original => ({ ...await original<typeof import('@/api/http')>(), get: vi.fn(), post: vi.fn() }))
vi.mock('@/lib/feedback', () => ({ confirmAction: vi.fn() }))
const actor: CurrentUser = { id: '1', username: 'tester', name: 'Tester', portal: 'admin', superAdmin: false, permissions: ['auth:session:view', 'auth:session:revoke'], menus: [], navigation: [] }
const row: SessionView = { id: '9007199254740993123', username: 'private-target-name', name: 'Target', ip: '192.0.2.44', userAgent: 'Private device', lastSeenAt: '', expiresAt: '', locked: false, current: false }
const pageOf = (items: SessionView[]): PageResult<SessionView> => ({ items, total: String(items.length), page: '1', size: '20' })
const wrappers: { unmount: () => void }[] = [], pins: ReturnType<typeof createPinia>[] = []
function deferred<T>() { let resolve!: (value: T) => void, reject!: (cause: unknown) => void; const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
function harness(storage: () => Storage = () => localStorage) {
  const pinia = createPinia(); pins.push(pinia)
  const auth = useAuth(pinia); saveToken('private-session-token', false); auth.user = { ...actor, permissions: [...actor.permissions] }
  const registry = new OperationRegistry(() => operationOwner(auth.user, { tenantId: '10', portal: 'admin' }, getToken(), 'https://site/api'), storage)
  let sessions!: ReturnType<typeof useSessionRevocations>
  const wrapper = mount({ setup() { sessions = useSessionRevocations(registry); return {} }, template: '<div />' }, { global: { plugins: [pinia, i18n] } }); wrappers.push(wrapper)
  return { auth, registry, sessions, wrapper }
}
beforeEach(() => { vi.clearAllMocks(); localStorage.clear(); sessionStorage.clear(); vi.mocked(get).mockResolvedValue(pageOf([row])); vi.mocked(post).mockResolvedValue(null); vi.mocked(confirmAction).mockResolvedValue(true) })
afterEach(() => { for (const wrapper of wrappers.splice(0)) wrapper.unmount(); for (const pin of pins.splice(0)) disposePinia(pin) })

it('reserves before confirmation, keeps the original long id, and sends once for twenty calls', async () => {
  const answer = deferred<boolean>(); vi.mocked(confirmAction).mockReturnValueOnce(answer.promise)
  const { sessions } = harness(); await flushPromises()
  const target = { ...row }, pending = sessions.revoke(target)
  for (let i = 0; i < 20; i++) void sessions.revoke(target)
  expect(confirmAction).toHaveBeenCalledOnce(); expect(post).not.toHaveBeenCalled(); expect(sessions.blocked(row.id)).toBe(true)
  target.id = 'replacement'; answer.resolve(true); await pending
  expect(post).toHaveBeenCalledExactlyOnceWith('/auth/sessions/' + row.id + '/revoke', undefined, expect.objectContaining({ expectedIdentity: 'private-session-token', idempotencyKey: expect.any(String) }))
  expect(sessions.blocked(row.id)).toBe(false)
})
it('cancelled confirmation sends nothing and permits a fresh explicit intention', async () => {
  const { sessions, registry } = harness(); vi.mocked(confirmAction).mockResolvedValueOnce(false)
  await sessions.revoke(row); expect(post).not.toHaveBeenCalled(); expect(registry.list()).toEqual([])
  await sessions.revoke(row); expect(post).toHaveBeenCalledOnce()
})
it('unknown revocation survives list absence and reconstruction, while a different target remains usable', async () => {
  const first = harness(); vi.mocked(post).mockRejectedValueOnce(new ApiError(0, null, 'Lost response'))
  await first.sessions.revoke(row); const key = first.registry.list()[0]!.key
  vi.mocked(get).mockResolvedValue(pageOf([])); await first.sessions.refresh()
  expect(first.sessions.unresolved.value).toBe(true); first.wrapper.unmount()
  const restored = harness(); await flushPromises()
  expect(restored.sessions.unresolved.value).toBe(true); await restored.sessions.revoke(row)
  expect(post).toHaveBeenCalledOnce(); expect(restored.registry.list()[0]!.key).toBe(key)
  await restored.sessions.revoke({ ...row, id: 'second' }); expect(post).toHaveBeenCalledTimes(2)
  const metadata = localStorage.getItem('sma.operation.v1.' + key)!
  expect(metadata).not.toMatch(/private-session-token|private-target-name|192\.0\.2\.44|Private device/)
})
it.each(['identity', 'lock', 'permissions', 'dispose'])('invalidates an old confirmation after %s changes', async change => {
  const answer = deferred<boolean>(); vi.mocked(confirmAction).mockReturnValueOnce(answer.promise)
  const { sessions, auth, wrapper } = harness(); const pending = sessions.revoke(row)
  if (change === 'identity') { saveToken('new-token', false); auth.user = { ...actor, id: '2' } }
  if (change === 'lock') { auth.user!.locked = true; auth.user!.locked = false }
  if (change === 'permissions') { auth.user!.permissions = []; auth.user!.permissions = [...actor.permissions] }
  if (change === 'dispose') wrapper.unmount()
  answer.resolve(true); await pending; expect(post).not.toHaveBeenCalled()
})
it('discards late list data and failures, clears private rows immediately on permission loss', async () => {
  const { sessions, auth } = harness(); await flushPromises(); expect(sessions.rows.value).toHaveLength(1)
  const pending = deferred<PageResult<SessionView>>(); vi.mocked(get).mockReturnValueOnce(pending.promise)
  const load = sessions.refresh(); auth.user!.permissions = []
  expect(sessions.rows.value).toEqual([]); expect(sessions.total.value).toBe(0)
  pending.resolve(pageOf([row])); await load; expect(sessions.rows.value).toEqual([])
  auth.user!.permissions = [...actor.permissions]; await flushPromises()
  const failure = deferred<PageResult<SessionView>>(); vi.mocked(get).mockReturnValueOnce(failure.promise)
  const read = sessions.refresh(); auth.user!.locked = true
  failure.reject(new Error('Old private error')); await read
  expect(sessions.rows.value).toEqual([]); expect(sessions.request.error.value).toBe('')
})
it('late revocation success cannot refresh or overwrite a replacement identity', async () => {
  const pending = deferred<void>(); vi.mocked(post).mockReturnValueOnce(pending.promise)
  const { sessions, auth, registry } = harness(); const write = sessions.revoke(row)
  await vi.waitFor(() => expect(post).toHaveBeenCalledOnce())
  auth.user!.locked = true; auth.user!.locked = false; await flushPromises()
  const readCount = vi.mocked(get).mock.calls.length
  pending.resolve(); await write
  expect(get).toHaveBeenCalledTimes(readCount); expect(registry.list()[0]!.state).toBe('UNKNOWN')
})
it('storage failure, current session and missing permission never dispatch a revocation', async () => {
  const broken = harness(() => { throw new Error('Storage unavailable') }); await broken.sessions.revoke(row)
  expect(post).not.toHaveBeenCalled(); expect(broken.sessions.error.value).toBeTruthy()
  const valid = harness(); await valid.sessions.revoke({ ...row, current: true }); valid.auth.user!.permissions = ['auth:session:view']; await valid.sessions.revoke(row)
  expect(post).not.toHaveBeenCalled()
})
it('only explicit refusal releases a new attempt; server and conflict errors remain unknown', async () => {
  const { sessions } = harness()
  vi.mocked(post).mockRejectedValueOnce(new ApiError(403, null, 'Denied')); await sessions.revoke(row)
  expect(sessions.blocked(row.id)).toBe(false)
  vi.mocked(post).mockRejectedValueOnce(new ApiError(503, null, 'Uncertain')); await sessions.revoke(row)
  expect(sessions.blocked(row.id)).toBe(true)
  vi.mocked(post).mockRejectedValueOnce(new ApiError(409, null, 'Uncertain')); await sessions.revoke({ ...row, id: 'other' })
  expect(sessions.blocked('other')).toBe(true)
})
