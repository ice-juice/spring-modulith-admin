import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { webcrypto } from 'node:crypto'
import { mount } from '@vue/test-utils'
import { createPinia, disposePinia } from 'pinia'
import { i18n } from '@/locales'
import { ApiError, get, post, put } from '@/api/http'
import { useAuth, type CurrentUser } from '@/stores/auth'
import { useCredentialIntents, useSessionLockIntents } from '@/stores/credential-intents'
import { getToken, saveToken } from '@/lib/token'
import { useSecurityControls } from '../useSecurityControls'
import { browserLocksFixture, credentialMarker, sessionLockMarker } from '@/lib/__tests__/browser-locks.fixture'
const { replace } = vi.hoisted(() => ({ replace: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ replace }) }))
vi.mock('@/api/http', async original => ({ ...await original<typeof import('@/api/http')>(), get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('@/lib/feedback', () => ({ notify: vi.fn() }))
const actor: CurrentUser = { id: '1', username: 'tester', name: 'Tester', portal: 'admin', superAdmin: false, permissions: [], menus: [], navigation: [] }
const pins: ReturnType<typeof createPinia>[] = []
const wrappers: { unmount: () => void }[] = []
async function harness(beforeExit: () => Promise<boolean> = async () => true, waitReady = true) {
  const pinia = createPinia(); pins.push(pinia)
  const auth = useAuth(pinia); saveToken('original-session-token', false); auth.user = { ...actor }
  let controls!: ReturnType<typeof useSecurityControls>
  const wrapper = mount({ setup() { controls = useSecurityControls(beforeExit); return {} }, template: '<div />' }, { global: { plugins: [pinia, i18n] } }); wrappers.push(wrapper)
  const locks = useSessionLockIntents(pinia), credentials = useCredentialIntents(pinia)
  if (waitReady) await vi.waitFor(() => expect(locks.ready && credentials.ready).toBe(true))
  return { controls, auth, wrapper, locks, credentials }
}
function deferred<T>() { let resolve!: (value: T) => void, reject!: (cause: unknown) => void; const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
function password(controls: ReturnType<typeof useSecurityControls>) { controls.openPassword(); controls.oldPassword.value = 'Historical!Password2026'; controls.newPassword.value = 'Abcd1234'; controls.confirmation.value = 'Abcd1234' }
beforeEach(() => { vi.clearAllMocks(); vi.stubGlobal('crypto', webcrypto); Object.defineProperty(navigator, 'locks', { configurable: true, value: browserLocksFixture().locks }); localStorage.clear(); sessionStorage.clear(); vi.mocked(post).mockResolvedValue(null); vi.mocked(put).mockResolvedValue(null); vi.mocked(get).mockResolvedValue({ ...actor, locked: false }) })
afterEach(() => { for (const wrapper of wrappers.splice(0)) wrapper.unmount(); for (const pinia of pins.splice(0)) disposePinia(pinia); vi.unstubAllGlobals() })

it('shields an existing lock marker during scope verification without falsely locking a clean first load', async () => {
  const digest = vi.spyOn(crypto.subtle, 'digest')
  for (const existing of [false, true]) {
    const pending = deferred<ArrayBuffer>(); digest.mockReturnValueOnce(pending.promise).mockReturnValueOnce(pending.promise)
    if (existing) sessionStorage.setItem('sma.session-lock-intent.v1', JSON.stringify({ schema: 1, scope: '0'.repeat(64), key: '11111111-1111-4111-8111-111111111111', state: 'UNKNOWN' }))
    const { controls, locks } = await harness(async () => true, false)
    expect(controls.locked.value).toBe(existing)
    pending.resolve(new ArrayBuffer(32)); await vi.waitFor(() => expect(locks.ready).toBe(true))
  }
  digest.mockRestore()
})

it('reserves before confirmation and uses the captured password and original key exactly once', async () => {
  const confirmation = deferred<boolean>(), exit = vi.fn(() => confirmation.promise), response = deferred<unknown>()
  const { controls, credentials } = await harness(exit); password(controls); vi.mocked(put).mockReturnValueOnce(response.promise)
  const save = controls.save(); for (let i = 0; i < 20; i++) void controls.save()
  await vi.waitFor(() => expect(exit).toHaveBeenCalledOnce())
  controls.newPassword.value = 'Modified999'; expect(put).not.toHaveBeenCalled()
  confirmation.resolve(true); await vi.waitFor(() => expect(put).toHaveBeenCalledOnce())
  const options = vi.mocked(put).mock.calls[0]![2]!
  expect(put).toHaveBeenCalledWith('/iam/users/me/password', { oldPassword: 'Historical!Password2026', newPassword: 'Abcd1234' },
    { expectedIdentity: 'original-session-token', idempotencyKey: options.idempotencyKey })
  expect(credentials.unresolved).toBe(true)
  response.resolve(null); await save
  expect(getToken()).toBeNull(); expect(controls.oldPassword.value).toBe(''); expect(credentialMarker()).toBeNull()
  expect(replace).toHaveBeenCalledWith('/login')
})
it('late confirmation after identity change cannot change a new account password or sign it out', async () => {
  const confirmation = deferred<boolean>(); const { controls, auth } = await harness(() => confirmation.promise); password(controls)
  const pending = controls.save(); await vi.waitFor(() => expect(controls.passwordBusy.value).toBe(true))
  auth.reset(); saveToken('replacement-token', false); auth.user = { ...actor, id: '2' }
  confirmation.resolve(true); await pending
  expect(put).not.toHaveBeenCalled(); expect(post).not.toHaveBeenCalled(); expect(getToken()).toBe('replacement-token'); expect(replace).not.toHaveBeenCalled()
})
it('a lock-unlock transition invalidates a password confirmation prepared before the screen was locked', async () => {
  const confirmation = deferred<boolean>(), exit = vi.fn(() => confirmation.promise)
  const { controls, auth } = await harness(exit); password(controls)
  const pending = controls.save(); await vi.waitFor(() => expect(exit).toHaveBeenCalledOnce())
  auth.user!.locked = true; auth.user!.locked = false
  confirmation.resolve(true); await pending
  expect(put).not.toHaveBeenCalled(); expect(controls.oldPassword.value).toBe('')
})
it('cancelled exit confirmation creates no credential marker and performs no password write', async () => {
  const { controls } = await harness(async () => false); password(controls); await controls.save()
  expect(put).not.toHaveBeenCalled(); expect(credentialMarker()).toBeNull()
})
it('unknown own-password writes only resolve from the matching authoritative original-key receipt', async () => {
  const { controls, credentials } = await harness(); password(controls)
  vi.mocked(put).mockRejectedValueOnce(new ApiError(503, null, 'Unknown'))
  await controls.save(); const key = credentials.pendingKey()
  expect(key).toBeTruthy(); expect(controls.passwordUnknown.value).toBe(true); expect(controls.oldPassword.value).toBe('')
  await controls.save(); expect(put).toHaveBeenCalledOnce()
  vi.mocked(post).mockRejectedValueOnce(new ApiError(404, null, 'Not observed'))
  await controls.checkPassword(); expect(credentials.unresolved).toBe(true)
  const receipt = { operationId: 'receipt-1', operation: 'other.operation', state: 'FAILED_FINAL', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' }
  vi.mocked(post).mockResolvedValueOnce(receipt); await controls.checkPassword(); expect(credentials.unresolved).toBe(true)
  vi.mocked(post).mockResolvedValueOnce({ ...receipt, operation: 'iam.users.9721c28fc095e11b25656509' }); await controls.checkPassword()
  expect(credentials.unresolved).toBe(false); expect(put).toHaveBeenCalledOnce(); expect(replace).not.toHaveBeenCalled()
  expect(post).toHaveBeenLastCalledWith('/common/requests/lookup', { key }, { expectedIdentity: 'original-session-token' })
})
it('lock hides the screen and clears secrets before the server responds, while unlock uses a fresh password once', async () => {
  const { controls } = await harness(); password(controls)
  const pending = deferred<unknown>(); vi.mocked(post).mockReturnValueOnce(pending.promise)
  const lock = controls.lock(); expect(controls.locked.value).toBe(true); expect(controls.oldPassword.value).toBe('')
  await vi.waitFor(() => expect(post).toHaveBeenCalledOnce()); await controls.lock(); expect(post).toHaveBeenCalledOnce()
  pending.resolve(null); await lock; expect(controls.lockUnknown.value).toBe(false)
  controls.unlockPassword.value = 'Wrong'; vi.mocked(post).mockRejectedValueOnce(new ApiError(400, null, 'Wrong password')); await controls.unlock()
  expect(controls.locked.value).toBe(true); expect(controls.unlockPassword.value).toBe(''); expect(controls.lockUnknown.value).toBe(false)
  controls.unlockPassword.value = 'Correct!Historical'; await controls.unlock(); expect(controls.locked.value).toBe(false)
  expect(post).toHaveBeenLastCalledWith('/auth/unlock', { password: 'Correct!Historical' }, expect.objectContaining({ expectedIdentity: 'original-session-token' }))
})
it('unknown locks survive reconstruction even when the current server view says unlocked, and never repeat unlock', async () => {
  const first = await harness(); vi.mocked(post).mockRejectedValueOnce(new ApiError(0, null, 'Lost lock result')); await first.controls.lock()
  expect(first.controls.lockUnknown.value).toBe(true)
  const restored = await harness(); expect(restored.auth.user!.locked).toBe(true); expect(restored.controls.locked.value).toBe(true)
  restored.controls.unlockPassword.value = 'NeverReplay'; await restored.controls.unlock(); expect(post).toHaveBeenCalledOnce()
  const marker = sessionLockMarker(); expect(marker).not.toMatch(/original-session-token|NeverReplay/)
  await restored.controls.logout(); expect(getToken()).toBeNull(); expect(replace).toHaveBeenCalledWith('/login')
})
it('a failed lock marker causes local sign-out without sending an unrecorded lock request', async () => {
  const { controls } = await harness(); const set = localStorage.setItem.bind(localStorage)
  const spy = vi.spyOn(localStorage, 'setItem').mockImplementation((key, value) => { if (key.startsWith('sma.session-lock-intent.v2.')) throw new Error('Unavailable'); set(key, value) })
  await controls.lock(); expect(post).not.toHaveBeenCalled(); expect(getToken()).toBeNull(); expect(controls.locked.value).toBe(true); expect(replace).toHaveBeenCalledWith('/login'); spy.mockRestore()
})
it('emergency locking is independent of an unresolved credential change', async () => {
  const { controls, credentials } = await harness(); const ticket = (await credentials.begin())!; await credentials.sent(ticket); await credentials.release(ticket)
  await controls.lock(); expect(post).toHaveBeenCalledWith('/auth/lock', undefined, expect.anything()); expect(credentials.unresolved).toBe(true)
})
it('logout confirmation is exclusive and a failure still exits only the original local identity', async () => {
  const confirmation = deferred<boolean>(), exit = vi.fn(() => confirmation.promise)
  const { controls } = await harness(exit); const logout = controls.logout()
  for (let i = 0; i < 20; i++) void controls.logout()
  await vi.waitFor(() => expect(exit).toHaveBeenCalledOnce()); expect(post).not.toHaveBeenCalled()
  vi.mocked(post).mockRejectedValueOnce(new ApiError(0, null, 'Offline')); confirmation.resolve(true); await logout
  expect(post).toHaveBeenCalledOnce(); expect(getToken()).toBeNull(); expect(controls.locked.value).toBe(true); expect(replace).toHaveBeenCalledWith('/login')
})
it('a late logout confirmation does not send a request using a replacement identity', async () => {
  const confirmation = deferred<boolean>(); const { controls, auth } = await harness(() => confirmation.promise)
  const logout = controls.logout(); auth.reset(); saveToken('new-session', false); auth.user = { ...actor, id: '2' }
  confirmation.resolve(true); await logout
  expect(post).not.toHaveBeenCalled(); expect(getToken()).toBe('new-session'); expect(replace).not.toHaveBeenCalled()
})

it('a live peer acknowledgement only unshields after an authorized current server read', async () => {
  const first = await harness(), peer = await harness()
  await first.controls.lock(); window.dispatchEvent(new Event('storage'))
  expect(peer.controls.locked.value).toBe(true); expect(peer.auth.user?.locked).toBe(true)
  const server = deferred<CurrentUser>(); vi.mocked(get).mockReturnValueOnce(server.promise)
  first.controls.unlockPassword.value = 'Correct!Historical'; await first.controls.unlock(); window.dispatchEvent(new Event('storage'))
  await vi.waitFor(() => expect(get).toHaveBeenCalledOnce()); expect(peer.controls.locked.value).toBe(true)
  server.resolve({ ...actor, locked: false }); await vi.waitFor(() => expect(peer.controls.locked.value).toBe(false))
  expect(get).toHaveBeenCalledWith('/auth/me', undefined, { expectedIdentity: 'original-session-token' })
})

it('an older unlocked server response cannot undo a newer shared lock', async () => {
  const first = await harness(), peer = await harness(); await first.controls.lock(); window.dispatchEvent(new Event('storage'))
  const server = deferred<CurrentUser>(); vi.mocked(get).mockReturnValueOnce(server.promise)
  first.controls.unlockPassword.value = 'Correct!Historical'; await first.controls.unlock(); window.dispatchEvent(new Event('storage'))
  await vi.waitFor(() => expect(get).toHaveBeenCalledOnce())
  await first.controls.lock(); window.dispatchEvent(new Event('storage')); server.resolve({ ...actor, locked: false })
  await new Promise(resolve => setTimeout(resolve, 0))
  expect(peer.controls.locked.value).toBe(true); expect(peer.auth.user?.locked).toBe(true)
})

it('historical unlock markers and failed peer verification cannot remove a server or local shield', async () => {
  const first = await harness(); await first.controls.lock()
  first.controls.unlockPassword.value = 'Correct!Historical'; await first.controls.unlock()
  const peer = await harness(); peer.auth.user!.locked = true
  window.dispatchEvent(new Event('storage')); expect(get).not.toHaveBeenCalled(); expect(peer.controls.locked.value).toBe(true)
  await first.controls.lock(); window.dispatchEvent(new Event('storage'))
  vi.mocked(get).mockRejectedValueOnce(new ApiError(0, null, 'Offline'))
  first.controls.unlockPassword.value = 'Correct!Historical'; await first.controls.unlock(); window.dispatchEvent(new Event('storage'))
  await vi.waitFor(() => expect(get).toHaveBeenCalledOnce()); expect(peer.controls.locked.value).toBe(true)
})

it('a local lock competing with an older peer unlock exits locally rather than exposing the page', async () => {
  const first = await harness(), peer = await harness(); await first.controls.lock()
  // This peer has not received the shared lock notification yet.
  const response = deferred<unknown>(); vi.mocked(post).mockReturnValueOnce(response.promise)
  first.controls.unlockPassword.value = 'Correct!Historical'; const unlocking = first.controls.unlock()
  await vi.waitFor(() => expect(post).toHaveBeenCalledTimes(2))
  await peer.controls.lock(); expect(peer.controls.locked.value).toBe(true); expect(getToken()).toBeNull()
  response.resolve(null); await unlocking; window.dispatchEvent(new Event('storage'))
  expect(peer.controls.locked.value).toBe(true); expect(replace).toHaveBeenCalledWith('/login')
})

it.each(['missing-state', 'locked', 'wrong-user', 'wrong-portal'])('does not reveal a peer page from an incomplete or mismatched server view: %s', async mode => {
  const first = await harness(), peer = await harness(); await first.controls.lock(); window.dispatchEvent(new Event('storage'))
  const server: CurrentUser = { ...actor, locked: false }
  if (mode === 'missing-state') delete server.locked
  if (mode === 'locked') server.locked = true
  if (mode === 'wrong-user') server.id = 'other'
  if (mode === 'wrong-portal') server.portal = 'other'
  vi.mocked(get).mockResolvedValueOnce(server)
  first.controls.unlockPassword.value = 'Correct!Historical'; await first.controls.unlock(); window.dispatchEvent(new Event('storage'))
  await vi.waitFor(() => expect(get).toHaveBeenCalledOnce()); expect(peer.controls.locked.value).toBe(true)
})

it('a session replacement after lock metadata is committed cannot be cleared by the old lock fallback', async () => {
  const { controls, auth } = await harness(), original = localStorage.setItem.bind(localStorage)
  const spy = vi.spyOn(localStorage, 'setItem').mockImplementation((key, value) => {
    original(key, value)
    if (key.startsWith('sma.session-lock-intent.v2.')) queueMicrotask(() => {
      auth.reset(); saveToken('replacement-after-lock-admission', false); auth.user = { ...actor, id: '2', locked: false }
    })
  })
  try {
    await controls.lock()
    expect(post).not.toHaveBeenCalled(); expect(replace).not.toHaveBeenCalled()
    expect(getToken()).toBe('replacement-after-lock-admission'); expect(auth.user?.id).toBe('2')
  } finally { spy.mockRestore() }
})
