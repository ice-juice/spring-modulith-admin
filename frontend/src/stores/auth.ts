import { defineStore } from 'pinia'
import { ref } from 'vue'
import { get, post } from '@/api/http'
import { getToken, clearToken, saveToken } from '@/lib/token'
import { usePreferences } from './preferences'
import { normalizeLocale } from '@/locales'

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
  superAdmin: boolean
  permissions: string[]
  menus: MenuItem[]
  navigation: { id: string; name: string; menuIds: string[] }[]
  locale?: string
  timeZone?: string
  tenantTimeZone?: string
  locked?: boolean
  passwordChangeSuggested?: boolean
}
export const useAuth = defineStore('auth', () => {
  const user = ref<CurrentUser | null>(null)
  const ready = ref(false)
  function hasPermission(permission?: string | string[]) {
    if (!permission) return true
    const required = Array.isArray(permission) ? permission : [permission]
    return required.some((key) => user.value?.permissions.includes(key))
  }
  async function load(applyPreferences = true) {
    if (!getToken()) {
      ready.value = true
      return
    }
    user.value = await get<CurrentUser>('/auth/me')
    const preferences = usePreferences()
    if (applyPreferences && user.value.timeZone) preferences.changeTimeZone(user.value.timeZone)
    preferences.tenantZone = user.value.tenantTimeZone || null
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
    const result = await post<{ token: string }>('/auth/login', data)
    saveToken(result.token, data.remember)
    await load()
  }
  function reset() {
    clearToken()
    user.value = null
    ready.value = false
  }
  async function logout() {
    try {
      await post('/auth/logout')
    } finally {
      reset()
    }
  }
  return { user, ready, hasPermission, load, login, logout, reset }
})
