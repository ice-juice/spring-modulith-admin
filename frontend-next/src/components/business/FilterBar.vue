<script setup lang="ts">
import { Search, RotateCcw } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { useQueryDebounce } from '@/composables/useQueryDebounce'
defineProps<{ busy?: boolean; resettable?: boolean }>()
const emit = defineEmits<{ search: []; reset: [] }>()
const { t } = useI18n()
const query = useQueryDebounce(() => emit('search'))
function input(event: Event) {
  const target = event.target
  if (target instanceof HTMLInputElement && ['text', 'search', 'email', 'number', 'tel'].includes(target.type)) query.schedule()
}
function reset() { query.cancel(); emit('reset') }
</script>
<template><form class="filter-bar mb-0 flex flex-wrap items-end gap-3 rounded-t-2xl border border-b-0 bg-card p-4 [&>input]:w-full sm:[&>input]:w-64" @input="input" @compositionstart="query.compositionStart" @compositionend="query.compositionEnd" @keydown.capture="query.keydown" @submit.prevent="query.trigger"><slot /><Button type="submit" variant="secondary" :disabled="busy"><Search class="size-4" />{{ t('common.search') }}</Button><Button v-if="resettable" type="button" variant="ghost" :disabled="busy" @click="reset"><RotateCcw class="size-4" />{{ t('common.reset') }}</Button><div class="ml-auto flex items-center gap-2"><slot name="actions" /></div></form></template>
