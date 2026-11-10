import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { get } from '@/api/http'
import { i18n } from '@/locales'
export const useBranding = defineStore('branding', () => {
  const values = ref<Record<string, string>>({})
  const name = computed(() => values.value['system.name'] || i18n.global.t('common.brand'))
  const captchaEnabled = computed(() => values.value['login.captchaEnabled'] !== 'false')
  const background = computed(() => {
    const value = values.value['login.backgroundUrl']
    return value && /^(https?:\/\/|\/)/.test(value) ? value : undefined
  })
  async function load() {
    try {
      values.value = await get('/config/parameters/public')
      document.title = name.value
    } catch {
      /* The default branding works when public settings are unavailable. */
    }
  }
  return { values, name, captchaEnabled, background, load }
})
