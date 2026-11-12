import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { webcrypto } from 'node:crypto'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { i18n } from '@/locales'
import { ApiError, get, post } from '@/api/http'
import QRCode from 'qrcode'
import { useAccountSecurity } from '../useAccountSecurity'
import { useAuth, type CurrentUser } from '@/stores/auth'
import { getToken, saveToken } from '@/lib/token'
import { useCredentialIntents } from '@/stores/credential-intents'
import { browserLocksFixture, credentialMarker } from '@/lib/__tests__/browser-locks.fixture'
vi.mock('vue-router', () => ({ useRouter: () => ({ replace: vi.fn() }) }))
vi.mock('@/api/http', async importOriginal => ({ ...await importOriginal<typeof import('@/api/http')>(), get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('qrcode', () => ({ default: { toDataURL: vi.fn() } }))
const settings = { totpEnabled: false, totpPending: false, totpEnabledAt: null, securityPasswordSet: false, securityPasswordChangedAt: null, setupAvailable: true }
function harness(locked = false, supplied?: ReturnType<typeof createPinia>) {
  let security!: ReturnType<typeof useAccountSecurity>
  const pinia = supplied || createPinia(), auth = useAuth(pinia)
  saveToken('private-current-session', false)
  auth.user = { id: '1', username: 'tester', name: 'Tester', portal: 'admin', superAdmin: false, permissions: [], menus: [], navigation: [], locked } satisfies CurrentUser
  const wrapper = mount({ setup() { security = useAccountSecurity(); return {} }, template: '<div />' }, { global: { plugins: [pinia, i18n] } })
  return { wrapper, auth, pinia, get security() { return security } }
}
beforeEach(() => { vi.clearAllMocks(); vi.stubGlobal('crypto', webcrypto); Object.defineProperty(navigator, 'locks', { configurable: true, value: browserLocksFixture().locks }); localStorage.clear(); sessionStorage.clear(); vi.mocked(get).mockResolvedValue(settings) })
afterEach(() => { vi.unstubAllGlobals() })
it('does not resubmit an uncertain write after a successful status refresh or page reconstruction', async () => {
  const { wrapper, security } = harness(); await flushPromises()
  security.open('set'); Object.assign(security.form, { password: 'Historical!Password2025', newPassword: 'a1b2c3', confirmation: 'a1b2c3' })
  vi.mocked(post).mockRejectedValue(new ApiError(0, null, 'timeout'))
  await security.submit(); expect(security.uncertain.value).toBe(true); expect(security.form.password).toBe('')
  Object.assign(security.form, { password: 'Historical!Password2025', newPassword: 'a1b2c3', confirmation: 'a1b2c3' })
  await security.submit(); expect(post).toHaveBeenCalledTimes(1)
  await security.reload(); expect(security.uncertain.value).toBe(true); expect(security.mode.value).toBeNull(); expect(post).toHaveBeenCalledTimes(1)
  wrapper.unmount()
  const restored = harness(); await flushPromises(); await vi.waitFor(() => expect(restored.security.uncertain.value).toBe(true))
  restored.security.open('set'); expect(restored.security.mode.value).toBeNull()
  expect(post).toHaveBeenCalledTimes(1)
  const marker = credentialMarker()!
  expect(Object.keys(JSON.parse(marker)).sort()).toEqual(['key', 'schema', 'scope', 'state'])
  expect(marker).not.toMatch(/private-current-session|Historical!Password2025|a1b2c3|challengeToken/)
  restored.wrapper.unmount()
})
it('clears provisioning material on unmount and ignores late local QR completion', async () => {
  const { wrapper, security } = harness(); await flushPromises(); security.open('enroll'); security.form.password = 'Historical!Password2025'
  vi.mocked(post).mockResolvedValue({ challengeToken: 'private-challenge', secret: 'PRIVATESECRET', uri: 'otpauth://totp/test?secret=PRIVATESECRET', expiresAt: new Date(Date.now() + 600000).toISOString(), replacing: false })
  let complete!: (value: string) => void
  vi.mocked(QRCode.toDataURL).mockImplementationOnce((() => new Promise<string>(resolve => { complete = resolve })) as typeof QRCode.toDataURL)
  const pending = security.submit(); await vi.waitFor(() => expect(security.pending.value?.secret).toBe('PRIVATESECRET'))
  expect(JSON.stringify({ ...sessionStorage, ...localStorage })).not.toContain('PRIVATESECRET')
  wrapper.unmount(); complete('data:image/png;base64,fixture'); await pending
  expect(security.pending.value).toBeNull(); expect(security.qr.value).toBe(''); expect(security.form.password).toBe('')
})
function deferred<T>() {
  let resolve!: (value: T) => void, reject!: (cause: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function fill(security: ReturnType<typeof useAccountSecurity>) {
  security.open('set'); Object.assign(security.form, { password: 'Historical!Password2025', newPassword: 'a1b2c3', confirmation: 'a1b2c3' })
}
it('reserves before asynchronous preparation and binds the actual request to the captured identity', async () => {
  const { wrapper, security } = harness(); await flushPromises(); fill(security)
  const response = deferred<unknown>(); vi.mocked(post).mockReturnValueOnce(response.promise)
  const first = security.submit(); const duplicates = Array.from({ length: 20 }, () => security.submit())
  await vi.waitFor(() => expect(post).toHaveBeenCalledTimes(1))
  expect(post).toHaveBeenCalledWith('/auth/security-passwords', expect.objectContaining({ newPassword: 'a1b2c3' }),
    expect.objectContaining({ expectedIdentity: 'private-current-session', idempotencyKey: expect.any(String) }))
  expect(credentialMarker()).toContain('UNKNOWN')
  response.reject(new ApiError(400, 'auth.password.policy', 'Rejected'))
  await first; await Promise.all(duplicates)
  expect(security.uncertain.value).toBe(false); expect(credentialMarker()).toBeNull()
  wrapper.unmount()
})
it('refuses a mutation when its pending marker cannot be stored', async () => {
  const { wrapper, security } = harness(); await flushPromises(); fill(security)
  const original = localStorage.setItem.bind(localStorage)
  const spy = vi.spyOn(localStorage, 'setItem').mockImplementation((key, value) => { if (key.startsWith('sma.credential-intent.v2.')) throw new Error('Storage unavailable'); original(key, value) })
  await security.submit(); expect(post).not.toHaveBeenCalled(); expect(security.uncertain.value).toBe(true)
  spy.mockRestore(); wrapper.unmount()
})
it('cannot show old provisioning material or clear a new identity after a late credential response', async () => {
  const { wrapper, security, auth } = harness(); await flushPromises(); security.open('enroll'); security.form.password = 'Historical!Password2025'
  const response = deferred<unknown>(); vi.mocked(post).mockReturnValueOnce(response.promise)
  const pending = security.submit(); await vi.waitFor(() => expect(post).toHaveBeenCalledOnce())
  auth.reset(); saveToken('new-session', false); auth.user = { id: '2', username: 'second', name: 'Second', portal: 'admin', superAdmin: false, permissions: [], menus: [], navigation: [] }
  response.resolve({ challengeToken: 'old-challenge', secret: 'OLDSECRET', uri: 'otpauth://totp/test?secret=OLDSECRET', expiresAt: new Date(Date.now() + 600000).toISOString() })
  await pending
  expect(getToken()).toBe('new-session'); expect(auth.user.id).toBe('2'); expect(security.pending.value).toBeNull(); expect(security.qr.value).toBe('')
  expect(QRCode.toDataURL).not.toHaveBeenCalled(); wrapper.unmount()
})
it('a local QR failure does not turn an acknowledged enrollment into an unknown native mutation', async () => {
  const { wrapper, security } = harness(); await flushPromises(); security.open('enroll'); security.form.password = 'Historical!Password2025'
  vi.mocked(post).mockResolvedValue({ challengeToken: 'confirmed-enrollment', secret: 'PRIVATESECRET', uri: 'otpauth://totp/test?secret=PRIVATESECRET', expiresAt: new Date(Date.now() + 600000).toISOString() })
  vi.mocked(QRCode.toDataURL).mockRejectedValueOnce(new Error('Local QR rendering failed'))
  await security.submit(); expect(security.pending.value?.secret).toBe('PRIVATESECRET'); expect(security.uncertain.value).toBe(false)
  expect(credentialMarker()).toBeNull(); wrapper.unmount()
})
it('new authenticated sessions get a new scope without claiming the prior unknown operation completed', async () => {
  const { wrapper, security, auth, pinia } = harness(); await flushPromises(); fill(security)
  vi.mocked(post).mockRejectedValueOnce(new ApiError(503, null, 'Unknown'))
  await security.submit(); const original = credentialMarker()
  auth.reset(); saveToken('freshly-authenticated-session', false)
  auth.user = { id: '1', username: 'tester', name: 'Tester', portal: 'admin', superAdmin: false, permissions: [], menus: [], navigation: [] }
  const intents = useCredentialIntents(pinia); await vi.waitFor(() => expect(intents.ready).toBe(true))
  expect(intents.unresolved).toBe(false); expect(credentialMarker()).toBe(original)
  wrapper.unmount()
})
it('separate consumers in one authenticated application share the credential mutation gate', async () => {
  const first = harness(), second = harness(false, first.pinia); await flushPromises()
  fill(first.security); fill(second.security)
  const response = deferred<unknown>(); vi.mocked(post).mockReturnValueOnce(response.promise)
  const active = first.security.submit(); await vi.waitFor(() => expect(post).toHaveBeenCalledOnce())
  await second.security.submit(); expect(post).toHaveBeenCalledOnce(); expect(second.security.busy.value).toBe(true)
  response.reject(new ApiError(503, null, 'Unknown')); await active
  expect(first.security.uncertain.value).toBe(true); expect(second.security.uncertain.value).toBe(true)
  first.wrapper.unmount(); second.wrapper.unmount()
})
it('corrupt pending metadata refuses credential writes rather than silently forgetting it', async () => {
  sessionStorage.setItem('sma.credential-intent.v1', '{invalid')
  const { wrapper, security } = harness(); await flushPromises(); await vi.waitFor(() => expect(security.uncertain.value).toBe(true))
  fill(security); await security.submit(); expect(post).not.toHaveBeenCalled(); expect(security.error.value).not.toBe('')
  expect(sessionStorage.getItem('sma.credential-intent.v1')).toBe('{invalid'); wrapper.unmount()
})
it('rejects mismatched security passwords without submitting', async () => {
  const { wrapper, security } = harness(); await flushPromises(); security.open('set')
  Object.assign(security.form, { password: 'Historical!Password2025', newPassword: 'a1b2c3', confirmation: 'a1b2c4' })
  await security.submit(); expect(post).not.toHaveBeenCalled(); expect(security.error.value).not.toBe(''); wrapper.unmount()
})
it('reloads after password-only unlock and does not clear settings on same-account preference refresh', async () => {
  const { wrapper, security, auth } = harness(true); await flushPromises()
  expect(get).not.toHaveBeenCalled(); expect(security.settings.value).toBeNull()
  auth.user!.locked = false; await flushPromises()
  expect(get).toHaveBeenCalledTimes(1); expect(security.settings.value?.setupAvailable).toBe(true)
  security.open('set'); security.form.newPassword = 'a1b2c3'
  auth.user = { ...auth.user!, theme: 'dark', locale: 'en-US' }; await flushPromises()
  expect(security.settings.value?.setupAvailable).toBe(true); expect(security.form.newPassword).toBe('a1b2c3')
  auth.user.locked = true; await flushPromises()
  expect(security.form.newPassword).toBe(''); expect(security.settings.value).toBeNull(); wrapper.unmount()
})
