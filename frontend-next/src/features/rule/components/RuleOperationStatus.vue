<script setup lang="ts">
import OperationStatus from '@/components/business/OperationStatus.vue'
import type { useRuleTask } from '../useRuleTask'
const props = defineProps<{ task: ReturnType<typeof useRuleTask> }>()
const emit = defineEmits<{ resolved: [state: 'SUCCEEDED' | 'FAILED_FINAL'] }>()
async function check() {
  const state = await props.task.check()
  if (state === 'SUCCEEDED' || state === 'FAILED_FINAL') emit('resolved', state)
}
</script>
<template><OperationStatus :slow="task.submission.slow.value" :unresolved="task.submission.unresolved.value" :querying="task.submission.querying.value" queryable @check="check" /></template>
