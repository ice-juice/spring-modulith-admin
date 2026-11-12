<script setup lang="ts">
import { computed, onScopeDispose, ref, watch } from 'vue'
import { Upload, LoaderCircle } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { loadLocaleModule } from '@/locales'
import { Button } from '@/components/ui/button'
import type { Asset } from '@/features/file/files'
import { useAuth } from '@/stores/auth'
import { resolveIcon } from '@/lib/icons'
import AppIcon from './AppIcon.vue'
import { notify } from '@/lib/feedback'
import { useFileUpload } from '@/composables/useFileUpload'
import OperationStatus from './OperationStatus.vue'
const props = defineProps<{ scope: string; label?: string; imageOnly?: boolean; disabled?: boolean; validate?: (file: File) => void }>(), emit = defineEmits<{ uploaded: [asset: Asset]; error: [message: string]; busy: [value: boolean] }>()
const input = ref<HTMLInputElement>(), task = useFileUpload(() => props.scope, () => !!props.imageOnly), submission = task.action, busy = submission.busy, { t } = useI18n()
watch(() => busy.value || submission.querying.value, value => emit('busy', value), { flush: 'sync' })
onScopeDispose(() => emit('busy', false))
const auth = useAuth()
void loadLocaleModule('file')
const configuredIcon = computed(() => { const name = auth.user?.permissionIcons?.['file:file:upload']; return auth.hasPermission('file:file:upload') && (name === 'none' || resolveIcon(name)) ? name : undefined })
async function send(event: Event) {
  const element = event.target as HTMLInputElement, file = element.files?.[0]
  if (!file || busy.value || props.disabled) return
  const asset = await task.send(file, props.validate)
  if (asset && !props.disabled) emit('uploaded', asset)
  else if (submission.error.value) { emit('error', submission.error.value); notify(submission.error.value, 'error') }
  if (!submission.unresolved.value) element.value = ''
}
async function check() {
  if (props.disabled) return
  finish(await task.check())
}
async function resume() { if (!props.disabled) finish(await task.resume()) }
function finish(asset?: Asset) { if (asset && !props.disabled) { if (input.value) input.value.value = ''; emit('uploaded', asset) } else if (submission.error.value) emit('error', submission.error.value) }
</script>
<template><span class="inline-flex flex-wrap items-center gap-2"><input ref="input" class="hidden" type="file" :accept="imageOnly ? 'image/*' : undefined" :disabled="disabled || busy || submission.unresolved.value" :aria-label="label || t('file.upload')" @change="send" /><Button type="button" variant="outline" :disabled="disabled || busy || submission.unresolved.value" @click="input?.click()"><LoaderCircle v-if="busy" class="size-4 animate-spin" /><AppIcon v-else-if="configuredIcon" :name="configuredIcon" :size="18" /><Upload v-else class="size-4" />{{ label || t('file.upload') }}</Button><OperationStatus :slow="submission.slow.value" :unresolved="submission.unresolved.value" :querying="disabled || submission.querying.value" queryable @check="check" /><Button v-if="task.canResume.value" type="button" variant="outline" :disabled="disabled || busy || submission.querying.value" @click="resume">{{ t('file.resumeOriginal') }}</Button><span v-else-if="task.receipt.value?.state === 'ACCEPTED'" class="text-sm text-muted-foreground">{{ t('file.originalUnavailable') }}</span></span></template>
