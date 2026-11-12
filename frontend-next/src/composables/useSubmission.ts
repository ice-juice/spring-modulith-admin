import { computed, onScopeDispose, ref } from 'vue'
import { i18n } from '@/locales'
import { ApiError, post } from '@/api/http'
import { useOperations } from '@/lib/operations'
import { OperationBlocked, resourcesConflict, type OperationTicket, type OperationRecord, type OperationRegistry, type OperationSpec, type OperationSend, type OperationResult } from '@/lib/operation-registry'

type Prepared<T> = ((options: OperationSend) => Promise<T>) | undefined
export function useSubmission(supplied?: OperationRegistry) {
  const registry = supplied || useOperations().registry
  const revision = ref(0), key = ref(''), boundResource = ref(''), preparing = ref(false), querying = ref(false), error = ref('')
  let live = true
  let activeTicket: OperationTicket | undefined
  const unsubscribe = registry.subscribe(() => { revision.value++ })
  onScopeDispose(() => { live = false; if (activeTicket) registry.cancel(activeTicket); activeTicket = undefined; unsubscribe() })
  const record = computed(() => {
    void revision.value
    try {
      const rows = registry.list()
      return rows.find(row => row.key === key.value) || (boundResource.value ? rows.find(row => [row.resource, ...(row.resources || [])].some(value => resourcesConflict(value, boundResource.value))) : undefined)
    }
    catch { return undefined }
  })
  const busy = computed(() => preparing.value || ['PREPARING', 'SENDING', 'SLOW'].includes(record.value?.state || ''))
  const unresolved = computed(() => ['PENDING', 'UNKNOWN'].includes(record.value?.state || ''))
  const slow = computed(() => record.value?.state === 'SLOW')
  function message(cause: unknown) {
    return cause instanceof OperationBlocked ? i18n.global.t('common.operation.' + cause.reason)
      : cause instanceof Error ? cause.message : i18n.global.t('common.networkError')
  }
  async function run<T>(spec: OperationSpec, prepare: () => Prepared<T> | Promise<Prepared<T>>,
    native?: { success: (value: T) => 'completed' | 'pending' | 'unknown' | 'failed' | 'rejected'; failure?: (cause: unknown) => 'completed' | 'pending' | 'unknown' | 'failed' | 'rejected' }): Promise<OperationResult<T> | { kind: 'cancelled' | 'blocked' }> {
    if (!live || busy.value) return { kind: 'blocked' }
    error.value = ''
    let ticket
    try { ticket = registry.reserve(spec) }
    catch (cause) {
      error.value = message(cause)
      try { key.value = registry.list().find(row => [row.resource, ...(row.resources || [])].some(existing => [spec.resource, ...(spec.resources || [])].some(wanted => resourcesConflict(existing, wanted))))?.key || '' } catch { /* Keep the explicit storage error. */ }
      return { kind: 'blocked' }
    }
    key.value = ticket.key; boundResource.value = spec.resource; preparing.value = true
    activeTicket = ticket
    try {
      if (registry.coordinated) await registry.admit(ticket)
      if (!live || !ticket.current()) return { kind: 'cancelled' }
      const send = await prepare()
      if (!send || !live || !ticket.current()) return { kind: 'cancelled' }
      preparing.value = false
      const result = await registry.execute(ticket, send, native)
      if (!live || !ticket.current() || key.value !== ticket.key) return { kind: 'stale' }
      if (result.kind === 'unknown' || result.kind === 'pending') error.value = i18n.global.t('common.operation.unknown')
      else if (result.kind === 'rejected' || result.kind === 'failed') error.value = result.error ? message(result.error) : i18n.global.t('common.operation.failed')
      return result
    } catch (cause) {
      if (live) error.value = message(cause)
      return { kind: 'blocked' }
    } finally { preparing.value = false; await registry.cancel(ticket) }
  }
  async function check() {
    if (querying.value || record.value?.protocol !== 'LOCAL') return
    key.value = record.value.key
    const current = guard()
    querying.value = true
    try { const state = await registry.lookupLocal(key.value, row => post('/common/requests/lookup', { key: row.key })); if (!current()) return; error.value = state === 'SUCCEEDED' ? '' : i18n.global.t(state === 'FAILED_FINAL' ? 'common.operation.failed' : 'common.operation.unknown'); return state }
    catch (cause) { if (current()) error.value = cause instanceof ApiError && cause.code === 404 ? i18n.global.t('common.operation.notFound') : message(cause) }
    finally { querying.value = false }
  }
  async function checkNative<T>(fetch: (record: Readonly<OperationRecord>) => Promise<T>,
    evidence: (value: T) => { id: string; state: 'SUCCEEDED' | 'FAILED_FINAL' | 'PENDING' | 'UNKNOWN' }) {
    if (querying.value || !record.value) return
    key.value = record.value.key
    const current = guard()
    querying.value = true
    try {
      const result = await registry.lookupNative<T>(key.value, fetch, evidence)
      if (!current()) return
      error.value = result.state === 'SUCCEEDED' ? '' : i18n.global.t(result.state === 'FAILED_FINAL' ? 'common.operation.failed' : 'common.operation.unknown')
      return result
    } catch (cause) { if (current()) error.value = message(cause) }
    finally { querying.value = false }
  }
  function guard() { const current = registry.guard(), currentKey = record.value?.key || key.value; return () => live && current() && currentKey === (record.value?.key || key.value) }
  async function continueNative<T>(send: (record: Readonly<OperationRecord>, options: OperationSend) => Promise<T>,
    native: { success: (value: T) => 'completed' | 'pending' | 'unknown' | 'failed' | 'rejected'; failure?: (cause: unknown) => 'completed' | 'pending' | 'unknown' | 'failed' | 'rejected' }) {
    if (!live || busy.value || querying.value || !record.value) return
    key.value = record.value.key
    const current = guard(); error.value = ''
    try {
      const result = await registry.continueNative(key.value, send, native)
      if (!current()) return
      if (result.kind === 'unknown' || result.kind === 'pending') error.value = i18n.global.t('common.operation.unknown')
      else if (result.kind === 'failed' || result.kind === 'rejected') error.value = i18n.global.t('common.operation.failed')
      return result
    } catch (cause) { if (current()) error.value = message(cause) }
  }
  function resetView() { if (!busy.value && !querying.value) { key.value = ''; boundResource.value = ''; error.value = '' } }
  function bind(resource: string) { boundResource.value = resource; key.value = ''; revision.value++ }
  return { run, check, checkNative, continueNative, guard, resetView, bind, key, record, busy, unresolved, slow, querying, error }
}
