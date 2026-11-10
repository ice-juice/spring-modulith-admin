import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { get } from '@/api/http'
import type { Option } from './types'
import { useRequestGate } from '@/composables/useRequestGate'
export function useDictionaries(keys: string[]) {
  const { locale } = useI18n(),
    options = reactive<Record<string, Option[]>>({}),
    error = ref('')
  const requestGate = useRequestGate()
  async function load() {
    const current = requestGate()
    try {
      const results = await Promise.all(keys.map(async key => [key, await get<Option[]>('/config/dictionaries/' + key + '/options')] as const))
      if (!current()) return
      for (const [key, values] of results) options[key] = values
      error.value = ''
    } catch (e) {
      if (!current()) return
      error.value = (e as Error).message
    }
  }
  watch(locale, load, { immediate: true })
  function label(key: string, value: string | number) {
    return options[key]?.find((option) => option.value === String(value))?.label || String(value)
  }
  return { options, label, error }
}
