import { computed, onScopeDispose, shallowRef, watch } from 'vue'
import { i18n } from '@/locales'
import { upload, lookupUpload, uploadedAsset, uploadEvidence, uploadFailure, type Asset, type UploadReceipt } from '@/features/file/files'
import type { OperationRegistry } from '@/lib/operation-registry'
import { useSubmission } from './useSubmission'

/** Context names contain only object/slot identifiers. File bytes and the send intent stay in memory. */
export function useFileUpload(context: () => string, imageOnly: () => boolean, registry?: OperationRegistry) {
  const action = useSubmission(registry), receipt = shallowRef<UploadReceipt>()
  let live = true, generation = 0
  let original: { file: File; imageOnly: boolean; key: string; current: () => boolean } | undefined
  watch(context, resource => { generation++; original = undefined; receipt.value = undefined; action.bind(resource) }, { immediate: true, flush: 'sync' })
  const clear = () => { generation++; original = undefined; receipt.value = undefined }
  window.addEventListener('sma:locked', clear); window.addEventListener('sma:unauthorized', clear)
  onScopeDispose(() => { live = false; clear(); window.removeEventListener('sma:locked', clear); window.removeEventListener('sma:unauthorized', clear) })
  function guard() { const epoch = generation, owns = action.guard(); return () => live && epoch === generation && owns() }
  const canResume = computed(() => action.unresolved.value && receipt.value?.state === 'ACCEPTED' && receipt.value.resumable
    && !!original && original.key === action.record.value?.key && original.current())
  async function send(file: File, validate?: (file: File) => void) {
    if (action.busy.value || action.unresolved.value || action.querying.value) return
    const epoch = generation, images = imageOnly()
    let current = () => false
    const result = await action.run({ operation: images ? 'file.image-upload' : 'file.upload', resource: context(), protocol: 'UPLOAD' }, () => {
      current = guard()
      if (file.size > 20 * 1024 * 1024) throw new Error(i18n.global.t('file.tooLarge'))
      validate?.(file)
      original = { file, imageOnly: images, key: action.record.value!.key, current }; receipt.value = undefined
      return options => upload(file, images, options)
    }, { success: () => 'completed', failure: uploadFailure })
    if (!live || epoch !== generation || !current()) return
    if (result.kind === 'success') { original = undefined; return result.value }
  }
  async function check(): Promise<Asset | undefined> {
    if (action.busy.value || action.querying.value || !action.record.value) return
    const current = guard()
    const result = await action.checkNative(row => lookupUpload(row.key), uploadEvidence)
    if (!current()) return
    receipt.value = result?.value
    if (original && !original.current()) original = undefined
    if (result?.state === 'SUCCEEDED' && result.value.fileId) {
      original = undefined
      try { const asset = await uploadedAsset(result.value.fileId); if (current()) return asset }
      catch (cause) { if (current()) action.error.value = (cause as Error).message }
    }
  }
  async function resume(): Promise<Asset | undefined> {
    if (!canResume.value || action.busy.value || action.querying.value || !original) return
    const input = original, current = guard()
    const result = await action.continueNative(async (row, options) => {
      const status = await lookupUpload(row.key), evidence = uploadEvidence(status)
      if (row.uploadId && row.uploadId !== evidence.id) throw new Error(i18n.global.t('common.operation.invalid'))
      if (!current() || !input.current() || input.key !== row.key) throw new Error(i18n.global.t('common.operation.identity'))
      if (evidence.state === 'SUCCEEDED') return { status, asset: await uploadedAsset(status.fileId!) }
      if (status.state !== 'ACCEPTED' || !status.resumable) return { status }
      return { asset: await upload(input.file, input.imageOnly, options) }
    }, { success: value => value.asset ? 'completed' : value.status?.state === 'FAILED_FINAL' ? 'failed' : value.status?.state === 'UNKNOWN' ? 'unknown' : 'pending', failure: uploadFailure })
    if (!current()) return
    receipt.value = undefined
    if (result?.kind === 'success') { original = undefined; return result.value.asset }
    if (result?.kind === 'pending') receipt.value = result.value?.status
  }
  return { action, canResume, receipt, send, check, resume }
}
