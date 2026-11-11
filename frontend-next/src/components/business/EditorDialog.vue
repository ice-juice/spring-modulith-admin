<script setup lang="ts">
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useI18n } from 'vue-i18n'
import { confirmAction } from '@/lib/feedback'
import { computed, nextTick } from 'vue'
import { useUnsavedChanges } from '@/composables/useUnsavedChanges'
const props = defineProps<{ title: string; description?: string; busy?: boolean; submitDisabled?: boolean; dirty?: boolean; wide?: boolean; saveLabel?: string }>()
const open = defineModel<boolean>({ required: true })
const emit = defineEmits<{ save: [] }>()
const { t } = useI18n()
useUnsavedChanges(computed(() => open.value && !!props.dirty), computed(() => open.value && !!props.busy))
let emitting = false, closing = false
function save() { if (props.busy || props.submitDisabled || emitting) return; emitting = true; emit('save'); void nextTick(() => { emitting = false }) }
async function change(value: boolean) {
  if (props.busy || closing) return
  closing = true
  try { if (!value && props.dirty && !await confirmAction(t('common.unsavedConfirm'), t('common.confirm'))) return; if (!props.busy) open.value = value }
  finally { closing = false }
}
</script>
<template><Dialog :open="open" @update:open="change"><DialogContent class="flex max-h-[min(90%,900px)] flex-col gap-0 overflow-hidden p-0" :class="wide ? 'sm:max-w-3xl' : 'sm:max-w-xl'"><DialogHeader class="shrink-0 border-b bg-accent/25 px-6 py-6 pr-12"><DialogTitle>{{ title }}</DialogTitle><DialogDescription>{{ description || t('common.editorHint') }}</DialogDescription></DialogHeader><div class="min-h-0 overflow-y-auto bg-card px-6 py-6"><slot /></div><DialogFooter class="shrink-0 border-t bg-muted/30 px-6 py-4"><slot name="footer"><Button variant="outline" :disabled="busy" @click="change(false)">{{ t('common.cancel') }}</Button><Button :disabled="busy || submitDisabled" @click="save">{{ busy ? t('common.loading') : saveLabel || t('common.save') }}</Button></slot></DialogFooter></DialogContent></Dialog></template>
