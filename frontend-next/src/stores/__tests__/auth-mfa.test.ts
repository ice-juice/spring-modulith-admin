import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, disposePinia, getActivePinia, setActivePinia } from 'pinia'
import { useAuth } from '../auth'
import { getToken, saveToken } from '@/lib/token'
import { ApiError, get, post } from '@/api/http'
import { browserLocksFixture } from '@/lib/__tests__/browser-locks.fixture'
import { webcrypto } from 'node:crypto'
vi.mock('@/api/http', async importOriginal => ({ ...await importOriginal<typeof import('@/api/http')>(), get: vi.fn(), post: vi.fn() }))
vi.mock('../preferences', () => ({ usePreferences: () => ({ changeTimeZone: vi.fn(), changeLocale: vi.fn(), applyAppearance: vi.fn() }) }))
const input = { username: 'tester', password: 'Historical!LongPassword', portal: 'admin', captchaId: 'fixture', captcha: 'ABCDE', remember: true }
beforeEach(() => { setActivePinia(createPinia()); vi.stubGlobal('crypto', webcrypto); Object.defineProperty(navigator, 'locks', { configurable: true, value: browserLocksFixture().locks }); localStorage.clear(); sessionStorage.clear(); vi.resetAllMocks() })
afterEach(() => { disposePinia(getActivePinia()!); vi.unstubAllGlobals() })
it('keeps a challenge only in memory, without loading permissions or saving an access token', async () => {
  vi.mocked(post).mockResolvedValue({ status: 'MFA_REQUIRED', challengeToken: 'private-challenge', expiresAt: new Date(Date.now() + 180000).toISOString(), token: null })
  const auth = useAuth()
  expect(await auth.login(input)).toBe('MFA_REQUIRED')
  expect(auth.user).toBeNull(); expect(getToken()).toBeNull(); expect(get).not.toHaveBeenCalled()
  expect(JSON.stringify(localStorage)).not.toContain('private-challenge'); expect(JSON.stringify(sessionStorage)).not.toContain('private-challenge')
  expect(auth.pendingMfa?.challengeToken).toBe('private-challenge')
})
it('persists only a session token after MFA despite a remember-me request', async () => {
  vi.mocked(post).mockResolvedValueOnce({ status: 'MFA_REQUIRED', challengeToken: 'challenge', expiresAt: new Date(Date.now() + 180000).toISOString() })
    .mockResolvedValueOnce({ status: 'AUTHENTICATED', token: 'final-token', rememberAllowed: false })
  vi.mocked(get).mockResolvedValue({ id: '1', username: 'tester', portal: 'admin', permissions: [], menus: [], navigation: [] })
  const auth = useAuth(); await auth.login(input); await auth.verifyMfa('012345')
  expect(post).toHaveBeenLastCalledWith('/auth/login-challenges/verify', { challengeToken: 'challenge', code: '012345' }, { expectedIdentity: null })
  expect(localStorage.getItem('sma.access-token')).toBeNull(); expect(sessionStorage.getItem('sma.access-token')).toBe('final-token')
  expect(auth.pendingMfa).toBeNull(); expect(auth.user?.id).toBe('1')
})
it('rejects expired challenges without making a verification request', async () => {
  vi.mocked(post).mockResolvedValue({ status: 'MFA_REQUIRED', challengeToken: 'expired', expiresAt: new Date(Date.now() - 1000).toISOString() })
  const auth = useAuth(); await auth.login(input); await expect(auth.verifyMfa('012345')).rejects.toThrow()
  expect(post).toHaveBeenCalledTimes(1); expect(getToken()).toBeNull(); expect(auth.pendingMfa).toBeNull()
})
it('ignores a late verification response after cancellation', async () => {
  vi.mocked(post).mockResolvedValueOnce({ status: 'MFA_REQUIRED', challengeToken: 'cancelled', expiresAt: new Date(Date.now() + 180000).toISOString() })
  const auth = useAuth(); await auth.login(input)
  let complete!: (value: unknown) => void
  vi.mocked(post).mockReturnValueOnce(new Promise(resolve => { complete = resolve }))
  const pending = auth.verifyMfa('012345'); await vi.waitFor(() => expect(post).toHaveBeenCalledTimes(2)); auth.reset(); complete({ status: 'AUTHENTICATED', token: 'obsolete-token' })
  await expect(pending).rejects.toThrow(); expect(getToken()).toBeNull(); expect(get).not.toHaveBeenCalled()
})

function deferred<T>() {
  let resolve!: (value: T) => void, reject!: (cause: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const actor = { id: '1', username: 'tester', name: 'Tester', portal: 'admin', superAdmin: false, permissions: [], menus: [], navigation: [] }
const challenge = () => ({ status: 'MFA_REQUIRED', challengeToken: 'single-use-challenge', expiresAt: new Date(Date.now() + 180000).toISOString() })
it('takes the authentication gate before dispatch and does not reset the first intent on a duplicate click', async () => {
  const pending = deferred<unknown>(); vi.mocked(post).mockReturnValueOnce(pending.promise)
  const auth = useAuth(), original = { ...input }, first = auth.login(original)
  original.password = 'mutated-after-click'
  await expect(auth.login(input)).rejects.toThrow()
  await vi.waitFor(() => expect(post).toHaveBeenCalledTimes(1)); expect(auth.authenticating).toBe(true)
  expect(post).toHaveBeenCalledWith('/auth/login', expect.objectContaining({ password: input.password }), { expectedIdentity: null })
  pending.resolve(challenge()); expect(await first).toBe('MFA_REQUIRED'); expect(auth.authenticating).toBe(false)
})
it('sends an OTP only once concurrently and keeps its challenge after an explicit input refusal', async () => {
  vi.mocked(post).mockResolvedValueOnce(challenge())
  const auth = useAuth(); await auth.login(input)
  const verification = deferred<unknown>(); vi.mocked(post).mockReturnValueOnce(verification.promise)
  const first = auth.verifyMfa('012345')
  await expect(auth.verifyMfa('012345')).rejects.toThrow()
  await vi.waitFor(() => expect(post).toHaveBeenCalledTimes(2))
  verification.reject(new ApiError(400, 'auth.code.invalid', 'Wrong code'))
  await expect(first).rejects.toMatchObject({ code: 400 })
  expect(auth.pendingMfa?.challengeToken).toBe('single-use-challenge'); expect(auth.authenticating).toBe(false)
})
it('does not reuse an OTP challenge after an uncertain transport result or persist authentication material', async () => {
  vi.mocked(post).mockResolvedValueOnce(challenge()).mockRejectedValueOnce(new ApiError(0, null, 'Network lost'))
  const auth = useAuth(); await auth.login(input)
  await expect(auth.verifyMfa('012345')).rejects.toThrow()
  expect(auth.pendingMfa).toBeNull(); expect(auth.authenticating).toBe(false)
  await expect(auth.verifyMfa('012345')).rejects.toThrow(); expect(post).toHaveBeenCalledTimes(2)
  const stored = JSON.stringify([localStorage, sessionStorage])
  for (const secret of ['single-use-challenge', '012345', input.password]) expect(stored).not.toContain(secret)
})
it('rejects malformed OTP values before dispatch without consuming the valid challenge', async () => {
  vi.mocked(post).mockResolvedValueOnce(challenge()); const auth = useAuth(); await auth.login(input)
  for (const code of ['12345', '1234567', '１２３４５６', 'ab1234']) await expect(auth.verifyMfa(code)).rejects.toThrow()
  expect(post).toHaveBeenCalledTimes(1); expect(auth.pendingMfa?.challengeToken).toBe('single-use-challenge')
})
it('does not report login completion if its identity changes during the permission read', async () => {
  vi.mocked(post).mockResolvedValueOnce({ status: 'AUTHENTICATED', token: 'first-token' })
  const permissions = deferred<unknown>(); vi.mocked(get).mockReturnValueOnce(permissions.promise)
  const auth = useAuth(), pending = auth.login(input)
  await vi.waitFor(() => expect(get).toHaveBeenCalledOnce())
  auth.reset(); saveToken('second-token', false); auth.user = { ...actor, id: '2' }
  permissions.resolve(actor)
  await expect(pending).rejects.toThrow()
  expect(getToken()).toBe('second-token'); expect(auth.user?.id).toBe('2'); expect(auth.authenticating).toBe(false)
})
it('an old authentication finally cannot release the admission gate of a newer login', async () => {
  const old = deferred<unknown>(), next = deferred<unknown>()
  vi.mocked(post).mockReturnValueOnce(old.promise).mockReturnValueOnce(next.promise)
  const auth = useAuth(), first = auth.login(input)
  await vi.waitFor(() => expect(post).toHaveBeenCalledTimes(1))
  auth.reset(); const second = auth.login(input)
  await vi.waitFor(() => expect(post).toHaveBeenCalledTimes(2))
  old.resolve(challenge()); await expect(first).rejects.toThrow()
  expect(auth.authenticating).toBe(true); await expect(auth.login(input)).rejects.toThrow(); expect(post).toHaveBeenCalledTimes(2)
  next.resolve(challenge()); await second; expect(auth.authenticating).toBe(false)
})
it('discards an old permission-read failure rather than letting its caller reset a new session', async () => {
  saveToken('old-token', false); const query = deferred<unknown>(); vi.mocked(get).mockReturnValueOnce(query.promise)
  const auth = useAuth(), pending = auth.load()
  auth.reset(); saveToken('new-token', false); auth.user = { ...actor, id: '2' }
  query.reject(new ApiError(401, null, 'Old identity expired'))
  expect(await pending).toBe(false); expect(getToken()).toBe('new-token'); expect(auth.user?.id).toBe('2')
})
it('late logout success or failure cannot clear a replacement identity', async () => {
  for (const failure of [false, true]) {
    const oldToken = 'old-token-' + failure
    const auth = useAuth(); auth.reset(); saveToken(oldToken, false); auth.user = actor
    const response = deferred<unknown>(); vi.mocked(post).mockReturnValueOnce(response.promise)
    const pending = auth.logout(); expect(await auth.logout()).toBe(false)
    await vi.waitFor(() => expect(post).toHaveBeenLastCalledWith('/auth/logout', undefined, expect.objectContaining({ expectedIdentity: oldToken, idempotencyKey: expect.any(String) })))
    auth.reset(); saveToken('new-token', false); auth.user = { ...actor, id: '2' }
    if (failure) response.reject(new ApiError(500, null, 'Unknown old logout')); else response.resolve(undefined)
    expect(await pending).toBe(false); expect(getToken()).toBe('new-token'); expect(auth.user?.id).toBe('2')
  }
})
it('clears only the current local session on logout failure without retrying its request', async () => {
  saveToken('current-token', false); vi.mocked(post).mockRejectedValueOnce(new ApiError(0, null, 'Offline'))
  const auth = useAuth(); auth.user = actor
  await expect(auth.logout()).rejects.toMatchObject({ code: 0 })
  expect(getToken()).toBeNull(); expect(auth.user).toBeNull(); expect(post).toHaveBeenCalledTimes(1)
})
