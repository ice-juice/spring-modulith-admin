import { defineStore } from 'pinia'
import { ref, computed, watch, onScopeDispose } from 'vue'
import { get } from '@/api/http'
import { i18n } from '@/locales'
import { clearToken } from '@/lib/token'
import { checkedEntry, legacyBrandImage, localizedBrandText, type TenantEntryDto } from '@/lib/tenant-branding'
import { usePreferences } from '@/stores/preferences'

export const useBranding = defineStore('branding', () => {
  const entry = ref<TenantEntryDto | null>(null)
  const loading = ref(false), error = ref(''), generation = ref(0)
  let lastScope = ''
  const preferences = usePreferences(), media = window.matchMedia('(prefers-color-scheme: dark)')
  const systemDark = ref(media.matches)
  const updateDark = (event: MediaQueryListEvent) => { systemDark.value = event.matches }
  media.addEventListener('change', updateDark)
  onScopeDispose(() => media.removeEventListener('change', updateDark))
  const dark = computed(() => preferences.theme === 'dark' || (preferences.theme === 'system' && systemDark.value))
  const template = computed(() => entry.value?.branding.login.templateId || 'login1')
  const welcomeTitle = computed(() => localizedBrandText(entry.value?.branding.login.title, i18n.global.locale.value))
  const welcomeDescription = computed(() => localizedBrandText(entry.value?.branding.login.description, i18n.global.locale.value))
  const logo = computed(() => entry.value?.branding.assets[dark.value ? 'LOGO_DARK' : 'LOGO_LIGHT'])
  const compactLogo = computed(() => entry.value?.branding.assets.LOGO_COMPACT || logo.value)
  const accent = computed(() => {
    const value = entry.value?.branding.appearance?.primaryColor
    return value && /^#[a-fA-F0-9]{6}$/.test(value) ? value : undefined
  })
  const ready = computed(() => entry.value !== null && !loading.value && !error.value)
  const portal = computed(() => entry.value?.entry.portal || '')
  const passwordEnabled = computed(() => ready.value && entry.value?.login.passwordEnabled === true)
  const captchaEnabled = computed(() => ready.value && entry.value?.login.captchaEnabled === true)
  const name = computed(() => {
    const identity = entry.value?.branding.identity
    return (i18n.global.locale.value.startsWith('en') && identity?.englishName?.trim() ? identity.englishName : identity?.systemName) || i18n.global.t(error.value ? 'common.entryUnavailableTitle' : 'common.brand')
  })
  const background = computed(() => entry.value?.branding.assets.LOGIN_BACKGROUND || legacyBrandImage(entry.value?.branding.legacyBackgroundUrl))
  watch(entry, value => preferences.setBrandPolicy(value ? value.entry.tenantId + '.' + value.entry.portal : undefined,
    value?.branding.appearance || { defaultSkin: 'iris', allowPersonalSkin: true }), { flush: 'sync' })
  watch([name, () => i18n.global.locale.value], () => { document.title = name.value }, { flush: 'post' })
  watch(() => entry.value?.branding.assets.FAVICON, source => {
    document.head.querySelector('link[data-sma-brand-icon]')?.remove()
    if (source) {
      const icon = document.createElement('link')
      icon.rel = 'icon'; icon.href = source; icon.dataset.smaBrandIcon = ''
      document.head.append(icon)
    }
  }, { immediate: true, flush: 'post' })
  // Explicit adapter for existing display components, never an arbitrary parameter map from the server.
  const values = computed((): Record<string, string> => {
    const brand = entry.value?.branding
    if (!brand) return {}
    return {
      'system.name': brand.identity.systemName,
      'system.copyrightCompany': brand.legal.copyrightOwner || '',
      'system.copyrightUrl': brand.legal.copyrightUrl || '',
      'system.recordInfo': brand.legal.icpRecord || '',
      'system.recordUrl': brand.legal.icpUrl || '',
      'login.backgroundUrl': background.value || '',
      'login.backgroundBlur': String(brand.login.backgroundBlur),
    }
  })
  function invalidateIdentity() {
    clearToken()
    window.dispatchEvent(new Event('sma:unauthorized'))
  }
  async function load(): Promise<boolean> {
    const current = ++generation.value
    loading.value = true
    entry.value = null
    error.value = ''
    document.title = i18n.global.t('common.brand')
    try {
      const result = checkedEntry(await get<unknown>('/tenant/entry'))
      if (current !== generation.value) return false
      const scope = result.entry.tenantId + ':' + result.entry.portal
      if (lastScope && lastScope !== scope) invalidateIdentity()
      lastScope = scope
      entry.value = result
      document.title = result.branding.identity.systemName
      return true
    } catch (cause) {
      if (current !== generation.value) return false
      error.value = i18n.global.t('common.entryUnavailable')
      document.title = i18n.global.t('common.entryUnavailableTitle')
      const code = (cause as { code?: number } | null)?.code
      if (code === 403 || code === 404) invalidateIdentity()
      return false
    } finally {
      if (current === generation.value) loading.value = false
    }
  }
  return { entry, values, name, portal, ready, passwordEnabled, captchaEnabled, background, loading, error, generation, template, welcomeTitle, welcomeDescription, logo, compactLogo, accent, dark, load }
})
