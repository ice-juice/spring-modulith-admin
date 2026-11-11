import { ref, computed, onScopeDispose } from 'vue'
import { getActivePinia } from 'pinia'
import { useI18n } from 'vue-i18n'
import { getToken } from '@/lib/token'
import { ApiError } from '@/api/http'
import { useAuth } from '@/stores/auth'
import { useSubmission } from '@/composables/useSubmission'
import type { OperationRegistry, OperationSpec } from '@/lib/operation-registry'
import { cloneExact } from './exact'
import { ruleGet, ruleWrite } from './api'

interface Command<T> {
  operation: string; resource: string; resources?: readonly string[]; path: string; body: unknown
  method?: 'POST' | 'PUT'; protocol?: OperationSpec['protocol']; operationKey?: boolean
  confirm?: () => boolean | Promise<boolean>; completed?: () => void | Promise<void>
  native?: { success: (value: T) => 'completed' | 'pending' | 'unknown' | 'failed'; failure?: (cause: unknown) => 'completed' | 'pending' | 'unknown' | 'failed' | 'rejected' }
}
export function useRuleTask(registry?: OperationRegistry) {
  const reading = ref(false), error = ref(''), legacyUncertain = ref(false), recovered = ref<unknown>(), { t } = useI18n()
  const submission = useSubmission(registry)
  const busy = computed(() => reading.value || submission.busy.value || submission.querying.value)
  // Old read handlers may reset their view flag, but can never discard an unresolved registered intent.
  const uncertain = computed({ get: () => legacyUncertain.value || submission.unresolved.value, set: value => { legacyUncertain.value = value } })
  const owner = () => { const actor = getActivePinia() ? useAuth().user : null; return JSON.stringify([getToken(), actor?.id, actor?.portal, actor?.locked, [...(actor?.permissions || [])].sort()]) }
  let epoch = 0, live = true
  const invalidate = () => { epoch++; reading.value = false }
  window.addEventListener('sma:locked', invalidate); window.addEventListener('sma:unauthorized', invalidate)
  onScopeDispose(() => { live = false; invalidate(); window.removeEventListener('sma:locked', invalidate); window.removeEventListener('sma:unauthorized', invalidate) })
  async function run<T>(load: () => Promise<T>, apply: (value: T) => void, write = false, isCurrent: () => boolean = () => true) {
    if (busy.value || write && uncertain.value) return false
    const request = ++epoch, actor = owner(); reading.value = true; error.value = ''
    try {
      const result = await load()
      if (!live || request !== epoch || actor !== owner() || !isCurrent()) return false
      apply(result); return true
    } catch (cause) {
      if (live && request === epoch && actor === owner() && isCurrent()) {
        error.value = cause instanceof ApiError ? cause.message : t('rule.invalidJson')
        if (write && cause instanceof ApiError && (cause.code === 0 || cause.code >= 500)) legacyUncertain.value = true
      }
      return false
    } finally { if (live && request === epoch) reading.value = false }
  }
  async function command<T>(input: Command<T>, apply: (value: T) => void) {
    if (busy.value) return false
    error.value = ''; const snapshot = cloneExact(input.body)
    let received: T | undefined
    const result = await submission.run<T>({ operation: input.operation, resource: input.resource, resources: input.resources, protocol: input.protocol || 'LOCAL' }, async () => {
      if (input.confirm && !await input.confirm()) return
      return async options => {
        const body = input.operationKey ? { ...(snapshot as Record<string, unknown>), operationKey: options.idempotencyKey } : snapshot
        received = await ruleWrite<T>(input.path, body, input.method, options)
        return received
      }
    }, input.native)
    if (!live || result.kind === 'stale') return false
    if (result.kind === 'success') { apply(result.value); return true }
    if (result.kind === 'completed') { if (input.completed) await input.completed(); else error.value = t('common.operation.previousFinished'); return false }
    if (result.kind === 'pending' && result.value !== undefined) apply(result.value)
    if (result.kind === 'failed' && input.native && received !== undefined) apply(received)
    if (submission.error.value) error.value = submission.error.value
    return false
  }
  async function check() {
    const record = submission.record.value
    if (!record) return
    recovered.value = undefined
    if (record.protocol === 'LOCAL') { const state = await submission.check(); error.value = submission.error.value; return state }
    if (record.operation === 'rule.template-publish') {
      const id = record.resource.slice('rule.assets.'.length)
      const result = await submission.checkNative(() => ruleGet<{ operationKey: string; release: { assetId: string; action: string } }>('/templates/' + encodeURIComponent(id) + '/publications/' + record.key), value => {
        if (value.operationKey !== record.key || value.release.assetId !== id || value.release.action !== 'PUBLISH') throw new Error(t('rule.uncertain'))
        return { id: record.key, state: 'SUCCEEDED' }
      })
      error.value = submission.error.value; recovered.value = result?.value; return result?.state
    }
    if (record.operation === 'rule.batch') {
      const result = await submission.checkNative(() => ruleGet<{ operationKey: string; report: { state: string } }>('/migration-batches/operations/' + record.key), value => {
        if (value.operationKey !== record.key) throw new Error(t('rule.uncertain'))
        return { id: record.key, state: value.report.state === 'COMPLETED' ? 'SUCCEEDED' : value.report.state === 'COMPLETED_WITH_ERRORS' ? 'FAILED_FINAL' : value.report.state === 'RUNNING' ? 'PENDING' : 'UNKNOWN' }
      })
      error.value = submission.error.value; recovered.value = result?.value; return result?.state
    }
    // Runtime cache eviction has no durable completion receipt. Observation cannot prove completion.
    if (record.operation === 'rule.resources-clear') await run(() => ruleGet('/resources'), value => { recovered.value = value })
    error.value = t('rule.uncertain'); return 'UNKNOWN'
  }
  return { busy, error, uncertain, run, command, check, recovered, submission, bind: submission.bind }
}
