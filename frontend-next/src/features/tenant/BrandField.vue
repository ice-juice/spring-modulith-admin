<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { inheritanceAllowed, type OverrideField, type OverrideMode } from './brands'
const props = defineProps<{ id: string; label: string; field?: OverrideField; mode?: OverrideMode; maxlength?: number; required?: boolean; disabled?: boolean; type?: string }>()
const model = defineModel<string | null>({ required: true })
const emit = defineEmits<{ mode: [value: OverrideMode] }>(), { t } = useI18n()
</script>
<template>
  <div class="space-y-2"><div class="flex flex-wrap items-center justify-between gap-2"><Label :for="id">{{ label }}</Label><select v-if="field" class="max-w-full rounded-md border bg-background px-2 py-1 text-xs" :value="mode || 'OVERRIDE'" :disabled="disabled" :aria-label="t('tenant.fieldMode', { field: label })" @change="emit('mode', ($event.target as HTMLSelectElement).value as OverrideMode)"><option value="OVERRIDE">{{ t('tenant.mode.OVERRIDE') }}</option><option v-if="inheritanceAllowed(props.field!)" value="INHERIT">{{ t('tenant.mode.INHERIT') }}</option><option value="HIDE">{{ t('tenant.mode.HIDE') }}</option></select></div><Input :id="id" :model-value="model || ''" :maxlength="maxlength" :required="required" :type="type || 'text'" :disabled="disabled || (mode && mode !== 'OVERRIDE')" @update:model-value="model = String($event)" /></div>
</template>
