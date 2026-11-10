import { defineStore } from 'pinia'
import { computed, ref, watchEffect, onScopeDispose } from 'vue'
import { i18n, setLocale, type AppLocale } from '@/locales'
import { resolveTimeZone } from '@/lib/time'

export const usePreferences = defineStore('preferences', () => {
  const userZone = ref<string | null>(localStorage.getItem('sma.time-zone'))
  const tenantZone = ref<string | null>(null)
  const theme = ref(localStorage.getItem('sma.theme') || 'light')
  const tabsEnabled = ref(true)
  const keepTabsAfterReload = ref(false)
  const media = window.matchMedia('(prefers-color-scheme: dark)')
  const systemDark = ref(media.matches)
  const updateMedia = (event: MediaQueryListEvent) => {
    systemDark.value = event.matches
  }
  media.addEventListener('change', updateMedia)
  onScopeDispose(() => media.removeEventListener('change', updateMedia))
  const timeZone = computed(() => resolveTimeZone(userZone.value, tenantZone.value))
  const locale = computed(() => i18n.global.locale.value)
  watchEffect(() => {
    document.documentElement.classList.toggle(
      'dark',
      theme.value === 'dark' || (theme.value === 'system' && systemDark.value),
    )
  })
  function changeLocale(value: AppLocale) {
    setLocale(value)
  }
  function changeTimeZone(value: string) {
    userZone.value = resolveTimeZone(value)
    localStorage.setItem('sma.time-zone', userZone.value)
  }
  function applyAppearance(value: {
    theme?: string
    tabsEnabled?: boolean
    keepTabsAfterReload?: boolean
  }) {
    if (value.theme && ['light', 'dark', 'system'].includes(value.theme)) {
      theme.value = value.theme
      localStorage.setItem('sma.theme', theme.value)
    }
    if (typeof value.tabsEnabled === 'boolean') tabsEnabled.value = value.tabsEnabled
    if (typeof value.keepTabsAfterReload === 'boolean')
      keepTabsAfterReload.value = value.keepTabsAfterReload
  }
  function snapshot() {
    return {
      locale: locale.value,
      timeZone: timeZone.value,
      theme: theme.value,
      tabsEnabled: tabsEnabled.value,
      keepTabsAfterReload: keepTabsAfterReload.value,
    }
  }
  return {
    userZone,
    tenantZone,
    timeZone,
    locale,
    theme,
    tabsEnabled,
    keepTabsAfterReload,
    changeLocale,
    changeTimeZone,
    applyAppearance,
    snapshot,
  }
})
