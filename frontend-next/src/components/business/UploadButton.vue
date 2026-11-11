<script setup lang="ts">
import { computed, ref } from 'vue'
import { Upload, LoaderCircle } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { upload, lookupUpload, uploadedAsset, uploadEvidence, uploadFailure, type Asset } from '@/features/file/files'
import { useAuth } from '@/stores/auth'
import { resolveIcon } from '@/lib/icons'
import AppIcon from './AppIcon.vue'
import { notify } from '@/lib/feedback'
import { useSubmission } from '@/composables/useSubmission'
import OperationStatus from './OperationStatus.vue'
const props = defineProps<{ label?: string; imageOnly?: boolean; disabled?: boolean }>(), emit = defineEmits<{ uploaded: [asset: Asset]; error: [message: string] }>()
const input = ref<HTMLInputElement>(), submission = useSubmission(), busy = submission.busy, { t } = useI18n()
const auth = useAuth()
const configuredIcon = computed(() => { const name = auth.user?.permissionIcons?.['file:file:upload']; return auth.hasPermission('file:file:upload') && (name === 'none' || resolveIcon(name)) ? name : undefined })
async function send(event: Event) {
  const element = event.target as HTMLInputElement, file = element.files?.[0], imageOnly = !!props.imageOnly
  if (!file || busy.value || props.disabled) return
  const result = await submission.run({ operation: imageOnly ? 'file.image-upload' : 'file.upload', resource: imageOnly ? 'file.images.upload' : 'file.files.upload', protocol: 'UPLOAD' }, () => {
    if (file.size > 20 * 1024 * 1024) throw new Error(t('file.tooLarge'))
    return options => upload(file, imageOnly, options)
  }, { success: () => 'completed', failure: uploadFailure })
  if (result.kind === 'success') emit('uploaded', result.value)
  else if (submission.error.value) { emit('error', submission.error.value); notify(submission.error.value, 'error') }
  if (!submission.unresolved.value) element.value = ''
}
async function check() {
  const current = submission.guard()
  const result = await submission.checkNative(row => lookupUpload(row.key), uploadEvidence)
  if (result?.state === 'SUCCEEDED' && result.value.fileId) {
    try { const asset = await uploadedAsset(result.value.fileId); if (current()) { if (input.value) input.value.value = ''; emit('uploaded', asset) } }
    catch (cause) { if (current()) emit('error', (cause as Error).message) }
  } else if (submission.error.value) emit('error', submission.error.value)
}
</script>
<template><span class="inline-flex"><input ref="input" class="hidden" type="file" :accept="imageOnly ? 'image/*' : undefined" :disabled="disabled || busy || submission.unresolved.value" :aria-label="label || t('file.upload')" @change="send" /><Button type="button" variant="outline" :disabled="disabled || busy || submission.unresolved.value" @click="input?.click()"><LoaderCircle v-if="busy" class="size-4 animate-spin" /><AppIcon v-else-if="configuredIcon" :name="configuredIcon" :size="18" /><Upload v-else class="size-4" />{{ label || t('file.upload') }}</Button><OperationStatus :slow="submission.slow.value" :unresolved="submission.unresolved.value" :querying="submission.querying.value" queryable @check="check" /></span></template>
