<script setup lang="ts">
import { useI18n } from 'vue-i18n'
const range = defineModel<[string, string] | undefined>()
defineProps<{ fromLabel?: string; toLabel?: string; disabled?: boolean }>()
const { t } = useI18n()
function update(index: number, value: string) { const next: [string, string] = [...(range.value || ['', ''])]; next[index] = value; range.value = next.some(Boolean) ? next : undefined }
</script>
<template><div class="flex max-w-full flex-wrap items-center gap-2"><input type="datetime-local" :value="range?.[0] || ''" :disabled="disabled" :aria-label="fromLabel || t('common.fromDate')" class="h-9 min-w-0 max-w-full rounded-md border bg-background px-3 text-sm" @input="update(0, ($event.target as HTMLInputElement).value)" /><span class="text-muted-foreground" aria-hidden="true">{{ t('common.rangeSeparator') }}</span><input type="datetime-local" :value="range?.[1] || ''" :disabled="disabled" :aria-label="toLabel || t('common.toDate')" class="h-9 min-w-0 max-w-full rounded-md border bg-background px-3 text-sm" @input="update(1, ($event.target as HTMLInputElement).value)" /></div></template>
