import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { get } from '@/api/http'
import type { Option } from './types'
export function useDictionaries(keys: string[]) {
  const { locale } = useI18n(),
    options = reactive<Record<string, Option[]>>({}),
    error = ref('')
  async function load() {
    try {
      await Promise.all(
        keys.map(async (key) => {
          options[key] = await get<Option[]>('/config/dictionaries/' + key + '/options')
        }),
      )
      error.value = ''
    } catch (e) {
      error.value = (e as Error).message
    }
  }
  watch(locale, load, { immediate: true })
  function label(key: string, value: string | number) {
    return options[key]?.find((option) => option.value === String(value))?.label || String(value)
  }
  return { options, label, error }
}
