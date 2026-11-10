<script setup lang="ts">
import { Search, RotateCcw } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
defineProps<{ busy?: boolean; resettable?: boolean }>()
const emit = defineEmits<{ search: []; reset: [] }>()
const { t } = useI18n()
</script>
<template><form class="mb-4 flex flex-wrap items-end gap-2 rounded-lg border bg-card p-3 [&>input]:w-full sm:[&>input]:w-64" @submit.prevent="emit('search')"><slot /><Button type="submit" variant="secondary" :disabled="busy"><Search class="size-4" />{{ t('common.search') }}</Button><Button v-if="resettable" type="button" variant="ghost" :disabled="busy" @click="emit('reset')"><RotateCcw class="size-4" />{{ t('common.reset') }}</Button><div class="ml-auto flex items-center gap-2"><slot name="actions" /></div></form></template>
