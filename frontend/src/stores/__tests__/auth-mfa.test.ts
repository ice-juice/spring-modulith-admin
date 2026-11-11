import { beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuth } from '../auth'
import { getToken } from '@/lib/token'
import { get, post } from '@/api/http'
vi.mock('@/api/http', () => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('../preferences', () => ({ usePreferences: () => ({ changeTimeZone: vi.fn(), changeLocale: vi.fn(), applyAppearance: vi.fn() }) }))
const input = { username: 'tester', password: 'Historical!LongPassword', portal: 'admin', captchaId: 'fixture', captcha: 'ABCDE', remember: true }
beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); sessionStorage.clear(); vi.clearAllMocks() })
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
  expect(post).toHaveBeenLastCalledWith('/auth/login-challenges/verify', { challengeToken: 'challenge', code: '012345' })
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
  const pending = auth.verifyMfa('012345'); auth.reset(); complete({ status: 'AUTHENTICATED', token: 'obsolete-token' })
  await expect(pending).rejects.toThrow(); expect(getToken()).toBeNull(); expect(get).not.toHaveBeenCalled()
})
