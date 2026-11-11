import { beforeEach, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { i18n } from '@/locales'
import { ApiError, get, post } from '@/api/http'
import QRCode from 'qrcode'
import { useAccountSecurity } from '../useAccountSecurity'
import { useAuth, type CurrentUser } from '@/stores/auth'
vi.mock('vue-router', () => ({ useRouter: () => ({ replace: vi.fn() }) }))
vi.mock('@/api/http', async importOriginal => ({ ...await importOriginal<typeof import('@/api/http')>(), get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('qrcode', () => ({ default: { toDataURL: vi.fn() } }))
const settings = { totpEnabled: false, totpPending: false, totpEnabledAt: null, securityPasswordSet: false, securityPasswordChangedAt: null, setupAvailable: true }
function harness(locked = false) {
  let security!: ReturnType<typeof useAccountSecurity>
  const pinia = createPinia(), auth = useAuth(pinia)
  if (locked) auth.user = { id: '1', username: 'tester', name: 'Tester', portal: 'admin', superAdmin: false, permissions: [], menus: [], navigation: [], locked: true } satisfies CurrentUser
  const wrapper = mount({ setup() { security = useAccountSecurity(); return {} }, template: '<div />' }, { global: { plugins: [pinia, i18n] } })
  return { wrapper, auth, get security() { return security } }
}
beforeEach(() => { vi.clearAllMocks(); localStorage.clear(); sessionStorage.clear(); vi.mocked(get).mockResolvedValue(settings) })
it('does not resubmit an uncertain write until explicit status refresh', async () => {
  const { wrapper, security } = harness(); await flushPromises()
  security.open('set'); Object.assign(security.form, { password: 'Historical!Password2025', newPassword: 'a1b2c3', confirmation: 'a1b2c3' })
  vi.mocked(post).mockRejectedValue(new ApiError(0, null, 'timeout'))
  await security.submit(); expect(security.uncertain.value).toBe(true); expect(security.form.password).toBe('')
  Object.assign(security.form, { password: 'Historical!Password2025', newPassword: 'a1b2c3', confirmation: 'a1b2c3' })
  await security.submit(); expect(post).toHaveBeenCalledTimes(1)
  await security.reload(); expect(security.uncertain.value).toBe(false); expect(security.mode.value).toBeNull(); expect(post).toHaveBeenCalledTimes(1)
  wrapper.unmount()
})
it('clears provisioning material on unmount and ignores late local QR completion', async () => {
  const { wrapper, security } = harness(); await flushPromises(); security.open('enroll'); security.form.password = 'Historical!Password2025'
  vi.mocked(post).mockResolvedValue({ challengeToken: 'private-challenge', secret: 'PRIVATESECRET', uri: 'otpauth://totp/test?secret=PRIVATESECRET', expiresAt: new Date(Date.now() + 600000).toISOString(), replacing: false })
  let complete!: (value: string) => void
  vi.mocked(QRCode.toDataURL).mockImplementationOnce((() => new Promise<string>(resolve => { complete = resolve })) as typeof QRCode.toDataURL)
  const pending = security.submit(); await flushPromises(); expect(security.pending.value?.secret).toBe('PRIVATESECRET')
  expect(JSON.stringify({ ...sessionStorage, ...localStorage })).not.toContain('PRIVATESECRET')
  wrapper.unmount(); complete('data:image/png;base64,fixture'); await pending
  expect(security.pending.value).toBeNull(); expect(security.qr.value).toBe(''); expect(security.form.password).toBe('')
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
