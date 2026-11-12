import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, disposePinia } from 'pinia'
import { useAuth, type CurrentUser } from '../auth'
import { getToken, getSharedToken, saveToken } from '@/lib/token'
import { ApiError, get, post } from '@/api/http'
import { browserLocksFixture } from '@/lib/__tests__/browser-locks.fixture'
vi.mock('@/api/http', async original => ({ ...await original<typeof import('@/api/http')>(), get: vi.fn(), post: vi.fn() }))
vi.mock('../preferences', () => ({ usePreferences: () => ({ changeTimeZone: vi.fn(), changeLocale: vi.fn(), applyAppearance: vi.fn() }) }))
const actor: CurrentUser = { id: '1', username: 'tester', name: 'Tester', portal: 'admin', superAdmin: false, permissions: [], menus: [], navigation: [] }
const input = { username: 'tester', password: 'Synthetic!PrivatePassword', portal: 'admin', captchaId: '', captcha: '', remember: true }
const challenge = () => ({ status: 'MFA_REQUIRED', challengeToken: 'private-challenge', expiresAt: new Date(Date.now() + 180000).toISOString() })
const pins: ReturnType<typeof createPinia>[] = []
let manager: ReturnType<typeof browserLocksFixture>
beforeEach(() => { vi.resetAllMocks(); localStorage.clear(); sessionStorage.clear(); manager = browserLocksFixture(); Object.defineProperty(navigator, 'locks', { configurable: true, value: manager.locks }); vi.mocked(get).mockResolvedValue(actor) })
afterEach(() => { for (const pin of pins.splice(0)) disposePinia(pin) })
function harness() { const pin = createPinia(); pins.push(pin); return { auth: useAuth(pin), pin } }
function deferred<T>() { let resolve!: (value: T) => void, reject!: (cause: unknown) => void; const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
function shared(value: string) { localStorage.setItem('sma.access-token', value); window.dispatchEvent(new StorageEvent('storage', { key: 'sma.access-token', newValue: value })) }

it('admits only one login across independent stores and never queues the blocked credentials', async () => {
  const a = harness(), b = harness(), response = deferred<unknown>(); vi.mocked(post).mockReturnValueOnce(response.promise)
  const first = a.auth.login(input); await vi.waitFor(() => expect(post).toHaveBeenCalledOnce())
  await expect(b.auth.login({ ...input, username: 'another' })).rejects.toThrow()
  expect(post).toHaveBeenCalledOnce(); expect(a.auth.authenticating).toBe(true)
  response.resolve(challenge()); await first; expect(manager.held.size).toBe(0); expect(post).toHaveBeenCalledOnce()
  expect(JSON.stringify([localStorage, sessionStorage])).not.toMatch(/private-challenge|Synthetic|tester|another/)
})
it('does not delete an unrelated remembered identity merely by starting or cancelling a private login attempt', async () => {
  saveToken('other-shared-session', true)
  const { auth } = harness(), response = deferred<unknown>(); vi.mocked(post).mockReturnValueOnce(response.promise)
  const pending = auth.login(input), cancel = auth.captureAuthentication()
  await vi.waitFor(() => expect(post).toHaveBeenCalledOnce()); expect(getSharedToken()).toBe('other-shared-session')
  cancel(); expect(getToken()).toBeNull(); expect(getSharedToken()).toBe('other-shared-session')
  response.resolve({ status: 'AUTHENTICATED', token: 'obsolete' }); await expect(pending).rejects.toThrow()
  expect(getSharedToken()).toBe('other-shared-session'); expect(manager.held.size).toBe(0)
})
it('a changed shared slot invalidates a pending MFA challenge without persisting or replaying it', async () => {
  const { auth } = harness(); vi.mocked(post).mockResolvedValueOnce(challenge()); await auth.login(input)
  shared('peer-new-session')
  expect(auth.pendingMfa).toBeNull(); expect(getToken()).toBeNull(); expect(getSharedToken()).toBe('peer-new-session')
  await expect(auth.verifyMfa('012345')).rejects.toThrow(); expect(post).toHaveBeenCalledOnce(); expect(getSharedToken()).toBe('peer-new-session')
})
it('a remembered replacement during permission loading cannot be cleared by the old response or page cancellation handle', async () => {
  const { auth } = harness(), query = deferred<unknown>()
  vi.mocked(post).mockResolvedValueOnce({ status: 'AUTHENTICATED', token: 'old-accepted' }); vi.mocked(get).mockReturnValueOnce(query.promise)
  const pending = auth.login(input), cancel = auth.captureAuthentication()
  await vi.waitFor(() => expect(get).toHaveBeenCalledOnce()); shared('peer-new-session'); cancel()
  query.resolve(actor); await expect(pending).rejects.toThrow()
  expect(getSharedToken()).toBe('peer-new-session'); expect(getToken()).toBeNull(); expect(auth.user).toBeNull()
})
it('shared changes discard an old shared identity, but do not invalidate an unrelated private identity', async () => {
  saveToken('old-shared', true); const a = harness(); await a.auth.load(); shared('new-shared')
  expect(a.auth.user).toBeNull(); expect(getToken()).toBeNull(); expect(getSharedToken()).toBe('new-shared')
  saveToken('private', false); const b = harness(); await b.auth.load(); shared('another-shared')
  expect(b.auth.user?.id).toBe(actor.id); expect(getToken()).toBe('private')
})
it('an old page cancellation cannot clear a later explicit login attempt', async () => {
  const { auth } = harness(), old = deferred<unknown>(), fresh = deferred<unknown>()
  vi.mocked(post).mockReturnValueOnce(old.promise).mockReturnValueOnce(fresh.promise)
  const first = auth.login(input), cancelOld = auth.captureAuthentication()
  await vi.waitFor(() => expect(post).toHaveBeenCalledOnce()); cancelOld()
  const next = auth.login(input); await vi.waitFor(() => expect(post).toHaveBeenCalledTimes(2)); cancelOld()
  old.resolve(challenge()); await expect(first).rejects.toThrow(); expect(auth.authenticating).toBe(true)
  fresh.resolve(challenge()); await next; expect(auth.pendingMfa?.challengeToken).toBe('private-challenge')
})
it('releases a late browser grant after cancellation without sending a login', async () => {
  const { auth } = harness(), granted = deferred<void>()
  Object.defineProperty(navigator, 'locks', { configurable: true, value: { request: (name: string, options: { mode: LockMode; ifAvailable?: true }, callback: (lock: unknown | null) => Promise<void>) => manager.locks.request(name, options, async lock => { await granted.promise; await callback(lock) }) } })
  const pending = auth.login(input), cancel = auth.captureAuthentication()
  await vi.waitFor(() => expect(manager.held.size).toBeGreaterThan(0)); cancel(); granted.resolve()
  await expect(pending).rejects.toThrow(); expect(post).not.toHaveBeenCalled(); expect(manager.held.size).toBe(0)
})
it('missing browser capability never dispatches and cannot delete another remembered identity', async () => {
  saveToken('other-shared', true); Object.defineProperty(navigator, 'locks', { configurable: true, value: undefined })
  const { auth } = harness(); await expect(auth.login(input)).rejects.toThrow(); expect(post).not.toHaveBeenCalled()
  expect(getToken()).toBeNull(); expect(getSharedToken()).toBe('other-shared')
})
it('MFA gate contention is not an unknown OTP execution, but an actual uncertain response discards the challenge', async () => {
  const a = harness(); vi.mocked(post).mockResolvedValueOnce(challenge()); await a.auth.login(input)
  const b = harness(), pending = deferred<unknown>(); vi.mocked(post).mockReturnValueOnce(pending.promise)
  const other = b.auth.login(input); await vi.waitFor(() => expect(post).toHaveBeenCalledTimes(2))
  await expect(a.auth.verifyMfa('012345')).rejects.toThrow(); expect(a.auth.pendingMfa).not.toBeNull(); expect(post).toHaveBeenCalledTimes(2)
  pending.resolve(challenge()); await other
  vi.mocked(post).mockRejectedValueOnce(new ApiError(409, 'auth.state', 'Unknown challenge consumption'))
  await expect(a.auth.verifyMfa('012345')).rejects.toThrow(); expect(a.auth.pendingMfa).toBeNull()
})
