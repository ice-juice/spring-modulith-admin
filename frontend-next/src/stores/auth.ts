import { defineStore } from 'pinia'
import { ref } from 'vue'
import { get, post } from '@/api/http'
import { getToken, clearToken, saveToken } from '@/lib/token'
import { usePreferences } from './preferences'
import { normalizeLocale, i18n } from '@/locales'

export interface MenuItem {
  id: string
  parentId: string | null
  name: string
  path: string | null
  icon: string | null
  permission: string | null
  type: string
  openMode: string
  children: MenuItem[]
}
export interface CurrentUser {
  id: string
  username: string
  name: string
  portal: string
  avatarFileId?: string | null
  superAdmin: boolean
  permissions: string[]
  permissionIcons?: Record<string, string>
  menus: MenuItem[]
  navigation: { id: string; name: string; icon?: string | null; menuIds: string[] }[]
  locale?: string
  timeZone?: string
  tenantTimeZone?: string
  locked?: boolean
  passwordChangeSuggested?: boolean
  theme?: string
  skin?: string
  tabsEnabled?: boolean
  keepTabsAfterReload?: boolean
  features?: { departmentEnabled: boolean; postEnabled: boolean; extendedProfileEnabled: boolean }
}
interface LoginResult { status?: 'AUTHENTICATED' | 'MFA_REQUIRED'; token?: string | null; challengeToken?: string; expiresAt: string; rememberAllowed?: boolean }
export const useAuth = defineStore('auth', () => {
  const user = ref<CurrentUser | null>(null)
  const ready = ref(false)
  const pendingMfa = ref<{ challengeToken: string; expiresAt: string } | null>(null)
  let attempt = 0
  let generation = 0
  function hasPermission(permission?: string | string[]) {
    if (!permission) return true
    const required = Array.isArray(permission) ? permission : [permission]
    return required.some((key) => user.value?.permissions.includes(key))
  }
  async function load(applyPreferences = true) {
    const token = getToken(), current = ++generation
    if (!token) {
      ready.value = true
      return
    }
    const actor = await get<CurrentUser>('/auth/me')
    if (generation !== current || token !== getToken()) return
    user.value = actor
    const preferences = usePreferences()
    if (applyPreferences) {
      if (user.value.timeZone) preferences.changeTimeZone(user.value.timeZone)
      else preferences.userZone = null
    }
    preferences.tenantZone = user.value.tenantTimeZone || null
    preferences.applyAppearance({ ...user.value, skin: user.value.skin || 'iris', skinOwner: user.value.portal + '.' + user.value.id })
    if (applyPreferences && user.value.locale)
      preferences.changeLocale(normalizeLocale(user.value.locale))
    ready.value = true
  }
  async function login(data: {
    username: string
    password: string
    captchaId: string
    captcha: string
    remember: boolean
    portal: string
  }) {
    reset()
    const current = attempt
    const result = await post<LoginResult>('/auth/login', data)
    if (attempt !== current) throw new Error(i18n.global.t('auth.verificationExpired'))
    if (result.status === 'MFA_REQUIRED' && result.challengeToken && Number.isFinite(Date.parse(result.expiresAt))) {
      pendingMfa.value = { challengeToken: result.challengeToken, expiresAt: result.expiresAt }
      return 'MFA_REQUIRED' as const
    }
    if ((result.status && result.status !== 'AUTHENTICATED') || !result.token) throw new Error(i18n.global.t('auth.verificationExpired'))
    saveToken(result.token, data.remember && result.rememberAllowed !== false)
    await load()
    return 'AUTHENTICATED' as const
  }
  async function verifyMfa(code: string) {
    const pending = pendingMfa.value, current = attempt
    if (!pending || Date.parse(pending.expiresAt) <= Date.now()) { reset(); throw new Error(i18n.global.t('auth.verificationExpired')) }
    const result = await post<LoginResult>('/auth/login-challenges/verify', { challengeToken: pending.challengeToken, code })
    if (attempt !== current || pendingMfa.value?.challengeToken !== pending.challengeToken) throw new Error(i18n.global.t('auth.verificationExpired'))
    if (result.status !== 'AUTHENTICATED' || !result.token) throw new Error(i18n.global.t('auth.verificationExpired'))
    pendingMfa.value = null
    saveToken(result.token, false)
    await load()
  }
  function reset() {
    attempt++
    pendingMfa.value = null
    generation++
    clearToken()
    user.value = null
    usePreferences().applyAppearance({ skinOwner: 'guest' })
    ready.value = false
  }
  async function logout() {
    try {
      await post('/auth/logout')
    } finally {
      reset()
    }
  }
  return { user, ready, pendingMfa, hasPermission, load, login, verifyMfa, logout, reset }
})
