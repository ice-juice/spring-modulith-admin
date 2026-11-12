import { defineStore } from 'pinia'
import { onScopeDispose, ref } from 'vue'
import { ApiError, get, post } from '@/api/http'
import { getToken, getSharedToken, clearToken, detachToken, saveToken } from '@/lib/token'
import { BrowserOperationCoordinator, CoordinationError, type OperationLease } from '@/lib/browser-operation-coordinator'
import { hasSessionExitMarkers, prepareSessionExit, sessionExitEvent, sessionHasExited, type SessionExit } from '@/lib/session-exit'
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
  const authenticating = ref(false)
  let attempt = 0
  let generation = 0
  let activeAuthentication: symbol | null = null
  const coordinator = new BrowserOperationCoordinator(), leases = new Map<symbol, OperationLease>()
  let releasing = Promise.resolve()
  let acceptedAuthenticationToken: string | null = null, challengeSharedToken: string | null = null
  let observedToken = getToken(), observedSharedToken = getSharedToken(), live = true
  let leaving: { token: string; attempt: number; exit?: SessionExit } | null = null
  const expired = () => new Error(i18n.global.t('auth.verificationExpired'))
  function reserveAuthentication() {
    if (authenticating.value) throw new Error(i18n.global.t('auth.authenticationBusy'))
  }
  function beginAuthentication() {
    const ticket = Symbol('authentication')
    activeAuthentication = ticket; authenticating.value = true
    return ticket
  }
  async function releaseAuthentication(ticket: symbol) {
    const lease = leases.get(ticket); leases.delete(ticket)
    if (lease) { const released = lease.release(); releasing = Promise.all([releasing, released]).then(() => {}); await released }
  }
  async function finishAuthentication(ticket: symbol) {
    if (activeAuthentication === ticket) { activeAuthentication = null; authenticating.value = false }
    await releaseAuthentication(ticket)
  }
  async function admit(ticket: symbol, current: number) {
    await releasing
    if (!live || attempt !== current || activeAuthentication !== ticket) throw expired()
    let lease: OperationLease
    try { lease = await coordinator.acquire({ owner: window.location.origin, resource: 'authentication.session-slot' }) }
    catch (cause) { throw new Error(i18n.global.t(cause instanceof CoordinationError && cause.reason === 'busy' ? 'auth.authenticationBusy' : 'common.operation.coordination')) }
    if (!live || attempt !== current || activeAuthentication !== ticket) { await lease.release(); throw expired() }
    leases.set(ticket, lease)
  }
  function invalidate() {
    attempt++; generation++
    if (activeAuthentication) void releaseAuthentication(activeAuthentication)
    void leaving?.exit?.release()
    activeAuthentication = null; authenticating.value = false; leaving = null
    pendingMfa.value = null; acceptedAuthenticationToken = null; challengeSharedToken = null
    user.value = null; ready.value = false
    usePreferences().applyAppearance({ skinOwner: 'guest' })
  }
  /** A page owns only the attempt it started, including its temporarily accepted token. */
  function captureAuthentication() {
    const current = attempt
    return () => {
      if (attempt !== current) return
      const token = acceptedAuthenticationToken
      invalidate()
      if (!clearToken(token)) detachToken()
      observedToken = getToken(); observedSharedToken = getSharedToken()
    }
  }
  function hasPermission(permission?: string | string[]) {
    if (!permission) return true
    const required = Array.isArray(permission) ? permission : [permission]
    return required.some((key) => user.value?.permissions.includes(key))
  }
  async function load(applyPreferences = true) {
    const token = getToken(), current = ++generation
    observedToken = token; observedSharedToken = getSharedToken()
    if (!token) {
      user.value = null
      ready.value = true
      return false
    }
    if (leaving?.token === token) return false
    if (hasSessionExitMarkers() && await sessionHasExited(token)) {
      if (generation === current && token === getToken()) { detachToken(); invalidate(); observedToken = null; observedSharedToken = getSharedToken() }
      return false
    }
    if (generation !== current || token !== getToken()) return false
    let actor: CurrentUser
    try { actor = await get<CurrentUser>('/auth/me', undefined, { expectedIdentity: token }) }
    catch (cause) {
      if (generation !== current || token !== getToken()) return false
      throw cause
    }
    if (generation !== current || token !== getToken()) return false
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
    return true
  }
  async function login(data: {
    username: string
    password: string
    captchaId: string
    captcha: string
    remember: boolean
    portal: string
  }) {
    reserveAuthentication(); invalidate(); detachToken()
    const current = attempt, ticket = beginAuthentication(), input = { ...data }
    const sharedToken = getSharedToken(), cancel = captureAuthentication()
    observedToken = null; observedSharedToken = sharedToken
    try {
      await admit(ticket, current)
      if (attempt !== current || getToken() !== null || getSharedToken() !== sharedToken) throw expired()
      const result = await post<LoginResult>('/auth/login', input, { expectedIdentity: null })
      if (attempt !== current || getToken() !== null || getSharedToken() !== sharedToken) throw expired()
      if (result.status === 'MFA_REQUIRED' && result.challengeToken && Number.isFinite(Date.parse(result.expiresAt))) {
        pendingMfa.value = { challengeToken: result.challengeToken, expiresAt: result.expiresAt }
        challengeSharedToken = sharedToken
        return 'MFA_REQUIRED' as const
      }
      if ((result.status && result.status !== 'AUTHENTICATED') || !result.token) throw expired()
      acceptedAuthenticationToken = result.token
      saveToken(result.token, input.remember && result.rememberAllowed !== false)
      observedToken = result.token; observedSharedToken = getSharedToken()
      const loaded = await load()
      if (!loaded || attempt !== current || result.token !== getToken()) throw expired()
      return 'AUTHENTICATED' as const
    } catch (cause) { cancel(); throw cause }
    finally { await finishAuthentication(ticket) }
  }
  async function verifyMfa(code: string) {
    reserveAuthentication()
    const pending = pendingMfa.value, current = attempt
    const cancel = captureAuthentication()
    if (!pending || Date.parse(pending.expiresAt) <= Date.now() || getSharedToken() !== challengeSharedToken) { cancel(); throw expired() }
    if (!/^[0-9]{6}$/.test(code)) throw new Error(i18n.global.t('auth.securityInputError'))
    const ticket = beginAuthentication()
    const sharedToken = challengeSharedToken
    let sent = false
    try {
      await admit(ticket, current)
      if (attempt !== current || pendingMfa.value !== pending || getToken() !== null || getSharedToken() !== sharedToken) throw expired()
      sent = true
      const result = await post<LoginResult>('/auth/login-challenges/verify', { challengeToken: pending.challengeToken, code }, { expectedIdentity: null })
      if (attempt !== current || pendingMfa.value?.challengeToken !== pending.challengeToken || getToken() !== null || getSharedToken() !== sharedToken) throw expired()
      if (result.status !== 'AUTHENTICATED' || !result.token) throw expired()
      pendingMfa.value = null
      acceptedAuthenticationToken = result.token
      saveToken(result.token, false)
      observedToken = result.token; observedSharedToken = getSharedToken()
      const loaded = await load()
      if (!loaded || attempt !== current || result.token !== getToken()) throw expired()
    } catch (cause) {
      // The challenge may already be consumed. Never replay an OTP after a transport/unknown outcome.
      if (attempt === current && (acceptedAuthenticationToken || getSharedToken() !== sharedToken
        || sent && (!(cause instanceof ApiError) || [0, 202, 408, 409].includes(cause.code) || cause.code >= 500))) cancel()
      throw cause
    } finally { await finishAuthentication(ticket) }
  }
  function reset() {
    clearToken()
    invalidate()
    observedToken = getToken(); observedSharedToken = getSharedToken()
  }
  async function logout(beforeExit: () => Promise<boolean> = async () => true) {
    const token = getToken(), current = attempt
    if (!token) {
      if (!await beforeExit() || attempt !== current || getToken() !== null) return false
      reset(); return true
    }
    if (leaving?.token === token && leaving.attempt === current) return false
    const operation: NonNullable<typeof leaving> = { token, attempt: current }; leaving = operation
    const owns = () => attempt === current && token === getToken()
    let exit: SessionExit | undefined, confirmed = false
    try {
      exit = await prepareSessionExit(token, owns)
      operation.exit = exit
      if (!owns() || !await beforeExit() || !owns()) return false
      confirmed = true
      const key = await exit.commit()
      if (!owns()) return false
      if (!key) { detachToken(); invalidate(); observedToken = null; observedSharedToken = getSharedToken(); return true }
      await post('/auth/logout', undefined, { expectedIdentity: token, idempotencyKey: key })
      if (!owns()) return false
      reset(); return true
    } catch (cause) {
      if (!owns()) return false
      if (!confirmed) throw cause
      reset(); throw cause
    } finally {
      if (leaving === operation) leaving = null
      await exit?.release()
    }
  }
  const storageChanged = async (event: StorageEvent) => {
    if (sessionExitEvent(event.key ?? '')) {
      const token = getToken(), current = attempt
      if (token && !(leaving?.token === token && leaving.exit?.exclusive)) {
        let retired = false
        try { retired = await sessionHasExited(token) } catch { retired = true }
        if (retired && attempt === current && getToken() === token && !(leaving?.token === token && leaving.exit?.exclusive)) {
          detachToken(); invalidate(); observedToken = null; observedSharedToken = getSharedToken()
          window.dispatchEvent(new Event('sma:unauthorized'))
          return
        }
      }
    }
    if (event.key !== null && event.key !== 'sma.access-token') return
    const effective = getToken(), shared = getSharedToken(), sharedChanged = shared !== observedSharedToken
    if (sharedChanged && (authenticating.value || pendingMfa.value)) {
      captureAuthentication()()
      window.dispatchEvent(new Event('sma:authentication-invalidated'))
    } else if (observedToken !== null && effective !== observedToken) {
      detachToken(); invalidate(); observedToken = null; observedSharedToken = shared
      window.dispatchEvent(new Event('sma:unauthorized'))
    } else observedSharedToken = shared
  }
  window.addEventListener('storage', storageChanged)
  onScopeDispose(() => {
    live = false; attempt++; generation++
    for (const ticket of [...leases.keys()]) void releaseAuthentication(ticket)
    void leaving?.exit?.release()
    // Pinia may already be destroying dependent stores. Cleanup cannot create a preferences store.
    window.removeEventListener('storage', storageChanged)
  })
  return { user, ready, pendingMfa, authenticating, hasPermission, load, login, verifyMfa, logout, reset, captureAuthentication }
})
