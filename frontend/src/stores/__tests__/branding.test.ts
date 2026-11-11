import { nextTick } from 'vue'
import { i18n } from '@/locales'
import { usePreferences } from '@/stores/preferences'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, disposePinia, getActivePinia, setActivePinia } from 'pinia'
import { get } from '@/api/http'
import { useBranding } from '@/stores/branding'
import { getToken, saveToken } from '@/lib/token'
import { checkedEntry, legacyBrandImage, type TenantEntryDto } from '@/lib/tenant-branding'
vi.mock('@/api/http', () => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
function entry(name = 'Tenant A', portal = 'partner'): TenantEntryDto {
  return {
    entry: { tenantId: '9007199254740993123', domainId: '8', portal },
    branding: {
      revisionId: '20', revision: '1', identity: { systemName: name },
      login: { templateId: 'login2', backgroundBlur: true },
      legal: { copyrightOwner: 'Tenant owner' },
      assets: { LOGIN_BACKGROUND: '/api/tenant/brand-images/20/LOGIN_BACKGROUND' },
      legacyBackgroundUrl: '/images/old.png',
    },
    login: { passwordEnabled: true, captchaEnabled: false },
  }
}
beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); sessionStorage.clear(); vi.mocked(get).mockReset() })
it('uses the bound portal and typed published fields without losing long IDs', async () => {
  vi.mocked(get).mockResolvedValue(entry())
  const brand = useBranding()
  expect(await brand.load()).toBe(true)
  expect(get).toHaveBeenCalledWith('/tenant/entry')
  expect(brand.entry?.entry.tenantId).toBe('9007199254740993123')
  expect(brand.portal).toBe('partner')
  expect(brand.passwordEnabled).toBe(true)
  expect(brand.captchaEnabled).toBe(false)
  expect(brand.background).toBe('/api/tenant/brand-images/20/LOGIN_BACKGROUND')
  expect(brand.values['system.copyrightCompany']).toBe('Tenant owner')
})
it('unavailable entry clears previous branding and disables authentication', async () => {
  const brand = useBranding()
  vi.mocked(get).mockResolvedValueOnce(entry())
  await brand.load()
  saveToken('old-scope-token', false)
  vi.mocked(get).mockRejectedValueOnce({ code: 404 })
  expect(await brand.load()).toBe(false)
  expect(brand.entry).toBeNull()
  expect(brand.values).toEqual({})
  expect(brand.background).toBeUndefined()
  expect(brand.ready).toBe(false)
  expect(brand.passwordEnabled).toBe(false)
  expect(brand.captchaEnabled).toBe(false)
  expect(document.title).not.toBe('Tenant A')
  expect(brand.error).not.toBe('')
  expect(getToken()).toBeFalsy()
})
it('a late success cannot replace a newer unavailable result', async () => {
  let resolve!: (value: TenantEntryDto) => void
  vi.mocked(get).mockReturnValueOnce(new Promise<TenantEntryDto>(done => { resolve = done }))
  const brand = useBranding(), old = brand.load()
  vi.mocked(get).mockRejectedValueOnce({ code: 404 })
  await brand.load()
  const title = document.title
  resolve(entry('Stale brand'))
  expect(await old).toBe(false)
  expect(brand.entry).toBeNull()
  expect(document.title).toBe(title)
})
it('a late failure cannot clear a newer entry and scope changes invalidate old identity', async () => {
  let reject!: (reason: unknown) => void
  vi.mocked(get).mockReturnValueOnce(new Promise<TenantEntryDto>((_, fail) => { reject = fail }))
  const brand = useBranding(), old = brand.load()
  vi.mocked(get).mockResolvedValueOnce(entry('Current'))
  await brand.load()
  reject({ code: 404 })
  await old
  expect(brand.name).toBe('Current')
  expect(brand.ready).toBe(true)
  saveToken('same-scope-token', false)
  vi.mocked(get).mockResolvedValueOnce(entry('Updated'))
  await brand.load()
  expect(getToken()).toBe('same-scope-token')
  vi.mocked(get).mockResolvedValueOnce(entry('Other portal', 'admin'))
  await brand.load()
  expect(getToken()).toBeFalsy()
  expect(brand.portal).toBe('admin')
})
it('rejects old maps, malformed scopes and private file paths instead of allowing fallback login', async () => {
  expect(() => checkedEntry({ 'system.name': 'old response' })).toThrow()
  const invalid = entry()
  invalid.branding.assets.LOGIN_BACKGROUND = '/api/file/files/123/content'
  expect(() => checkedEntry(invalid)).toThrow()
  const malformed = entry()
  malformed.entry.portal = '../admin'
  expect(() => checkedEntry(malformed)).toThrow()
  vi.mocked(get).mockResolvedValueOnce({ branding: {} })
  const brand = useBranding()
  expect(await brand.load()).toBe(false)
  expect(brand.passwordEnabled).toBe(false)
  for (const url of ['//evil.example/a', 'javascript:alert(1)', 'https://user:password@example.com/a', '/%2e%2e/a']) expect(legacyBrandImage(url)).toBeUndefined()
  expect(legacyBrandImage('/images/old.png')).toBe('/images/old.png')
})

afterEach(() => { const pinia = getActivePinia(); if (pinia) disposePinia(pinia) })
it('published language changes preserve the tenant title and use only the selected theme logo', async () => {
  const data = entry('Published tenant')
  data.branding.identity.englishName = 'Published English name'
  data.branding.login.title = { zhCN: '欢迎租户', enUS: 'Welcome tenant' }
  data.branding.login.description = { zhCN: '备用说明', enUS: null }
  data.branding.assets.LOGO_LIGHT = '/api/tenant/brand-images/20/LOGO_LIGHT'
  vi.mocked(get).mockResolvedValue(data)
  const brand = useBranding(), preferences = usePreferences()
  await brand.load()
  preferences.applyAppearance({ theme: 'light' })
  i18n.global.locale.value = 'zh-CN'
  await nextTick()
  expect(brand.welcomeTitle).toBe('欢迎租户')
  expect(brand.logo).toBe(data.branding.assets.LOGO_LIGHT)
  preferences.applyAppearance({ theme: 'dark' })
  expect(brand.logo).toBeUndefined()
  i18n.global.locale.value = 'en-US'
  document.title = 'Default title'
  await nextTick()
  expect(brand.welcomeTitle).toBe('Welcome tenant')
  expect(brand.welcomeDescription).toBe('备用说明')
  expect(document.title).toBe('Published English name')
  preferences.changeLocale('en-US')
  await nextTick()
  expect(document.title).toBe('Published English name')
  expect(brand.ready).toBe(true)
})
it('removes a published favicon when the entry becomes unavailable, retaining the built-in icon', async () => {
  const defaultIcon = document.createElement('link'); defaultIcon.rel = 'icon'; defaultIcon.href = '/favicon.ico'; document.head.append(defaultIcon)
  const data = entry(); data.branding.assets.FAVICON = '/api/tenant/brand-images/20/FAVICON'
  const brand = useBranding()
  vi.mocked(get).mockResolvedValueOnce(data)
  await brand.load(); await nextTick()
  expect(document.head.querySelector('link[data-sma-brand-icon]')?.getAttribute('href')).toBe(data.branding.assets.FAVICON)
  vi.mocked(get).mockRejectedValueOnce({ code: 404 })
  await brand.load(); await nextTick()
  expect(document.head.querySelector('link[data-sma-brand-icon]')).toBeNull()
  expect(defaultIcon.isConnected).toBe(true)
  defaultIcon.remove()
})
