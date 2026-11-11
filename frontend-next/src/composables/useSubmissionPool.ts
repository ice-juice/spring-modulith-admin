import { effectScope, onScopeDispose, ref, shallowRef, type EffectScope } from 'vue'
import { useOperations } from '@/lib/operations'
import { OperationBlocked, resourcesConflict, type OperationRegistry } from '@/lib/operation-registry'
import { useSubmission } from './useSubmission'

/** Independent row flows with one authoritative registry; same-object actions still conflict. */
export function useSubmissionPool(supplied?: OperationRegistry) {
  const registry = supplied || useOperations().registry, revision = ref(0)
  const workers = new Map<string, { scope: EffectScope; action: ReturnType<typeof useSubmission> }>()
  const last = shallowRef<ReturnType<typeof useSubmission>>()
  const unsubscribe = registry.subscribe(() => { revision.value++ })
  let live = true
  onScopeDispose(() => { live = false; unsubscribe(); for (const worker of workers.values()) worker.scope.stop(); workers.clear(); last.value = undefined })
  function get(resource: string) {
    if (!live) throw new OperationBlocked('identity')
    let worker = workers.get(resource)
    if (!worker) {
      if (workers.size >= 128) {
        const disposable = [...workers].find(([, value]) => !value.action.busy.value && !value.action.unresolved.value && !value.action.querying.value)
        if (!disposable) throw new OperationBlocked('capacity')
        disposable[1].scope.stop(); workers.delete(disposable[0])
      }
      const scope = effectScope(true), action = scope.run(() => useSubmission(registry))!
      action.bind(resource)
      worker = { scope, action }; workers.set(resource, worker)
    }
    last.value = worker.action
    return worker.action
  }
  function busy(resource: string) {
    void revision.value
    try { return registry.list().some(row => [row.resource, ...(row.resources || [])].some(value => resourcesConflict(value, resource)) && ['PREPARING', 'SENDING', 'SLOW'].includes(row.state)) }
    catch { return false } // reserve remains closed on storage failure and reports the error.
  }
  return { get, busy, last }
}
