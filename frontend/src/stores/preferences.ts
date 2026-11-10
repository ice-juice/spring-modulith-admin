import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { i18n, setLocale, type AppLocale } from '@/locales'
import { resolveTimeZone } from '@/lib/time'

export const usePreferences = defineStore('preferences', () => {
  const userZone = ref<string | null>(localStorage.getItem('sma.time-zone'))
  const tenantZone = ref<string | null>(null)
  const timeZone = computed(() => resolveTimeZone(userZone.value, tenantZone.value))
  const locale = computed(() => i18n.global.locale.value)
  function changeLocale(value: AppLocale) {
    setLocale(value)
  }
  function changeTimeZone(value: string) {
    userZone.value = resolveTimeZone(value)
    localStorage.setItem('sma.time-zone', userZone.value)
  }
  return { userZone, tenantZone, timeZone, locale, changeLocale, changeTimeZone }
})
