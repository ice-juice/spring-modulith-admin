import { computed, onScopeDispose, ref } from 'vue'
import { post } from '@/api/http'
import { useOperations } from '@/lib/operations'
import { OperationBlocked, type OperationRecord, type OperationRegistry } from '@/lib/operation-registry'
import { lookupUpload, uploadEvidence } from '@/features/file/files'

/** A read-only projection. No send closure, input reconstruction or record deletion API. */
export function useOperationPanel(supplied?: OperationRegistry) {
  const registry = supplied || useOperations().registry
  const rows = ref<OperationRecord[]>([]), error = ref(''), result = ref(''), querying = ref('')
  const unavailable = ref(false)
  let live = true, generation = 0, viewCurrent: (() => boolean) | undefined
  function refresh() {
    if (!viewCurrent?.()) {
      generation++; querying.value = ''; error.value = ''; result.value = ''
    }
    try {
      viewCurrent = registry.guard()
      rows.value = registry.list().sort((a, b) => b.createdAt - a.createdAt || a.key.localeCompare(b.key))
      unavailable.value = false
    } catch (cause) {
      rows.value = []
      unavailable.value = !(cause instanceof OperationBlocked && cause.reason === 'identity')
      if (unavailable.value) error.value = 'common.operation.storage'
    }
  }
  const unsubscribe = registry.subscribe(refresh)
  refresh()
  const count = computed(() => rows.value.length)
  const attentionCount = computed(() => rows.value.filter(row => ['SLOW', 'PENDING', 'UNKNOWN'].includes(row.state)).length)
  function queryable(row: OperationRecord) { return row.protocol === 'LOCAL' || row.protocol === 'UPLOAD' && ['file.upload', 'file.image-upload'].includes(row.operation) }
  async function check(key: string) {
    if (querying.value || !live) return
    const row = rows.value.find(row => row.key === key)
    if (!row || !queryable(row) || !['UNKNOWN', 'PENDING'].includes(row.state)) return
    if (row.retryAt && row.retryAt > Date.now()) { error.value = 'common.operation.retry'; return }
    let current: () => boolean
    try { current = registry.guard() } catch { refresh(); return }
    const attempt = generation
    querying.value = key; error.value = ''; result.value = ''
    try {
      const state = row.protocol === 'LOCAL'
        ? await registry.lookupLocal(key, record => post('/common/requests/lookup', { key: record.key }))
        : (await registry.lookupNative(key, record => lookupUpload(record.key), uploadEvidence)).state
      if (live && current() && attempt === generation) result.value = 'common.operationPanel.results.' + (state === 'PENDING' ? 'RUNNING' : state)
    } catch (cause) {
      if (live && current() && attempt === generation) error.value = cause instanceof OperationBlocked && cause.reason !== 'invalid'
        ? 'common.operation.' + cause.reason : 'common.operationPanel.lookupFailed'
    } finally {
      if (live && current() && attempt === generation) { querying.value = ''; refresh() }
    }
  }
  onScopeDispose(() => { live = false; generation++; unsubscribe() })
  return { rows, count, attentionCount, error, result, querying, unavailable, queryable, check }
}
