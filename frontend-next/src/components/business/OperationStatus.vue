<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
defineProps<{ slow?: boolean; unresolved?: boolean; querying?: boolean; queryable?: boolean }>()
const emit = defineEmits<{ check: [] }>()
const { t } = useI18n()
</script>
<template><div v-if="slow || unresolved" class="mb-3 flex flex-wrap items-center gap-2 rounded-lg border p-3 text-sm" role="status" aria-live="polite"><span>{{ t(slow ? 'common.operation.slow' : 'common.operation.unknown') }}</span><Button v-if="unresolved && queryable" type="button" size="sm" variant="outline" :disabled="querying" @click="emit('check')">{{ t(querying ? 'common.loading' : 'common.operation.check') }}</Button></div></template>
