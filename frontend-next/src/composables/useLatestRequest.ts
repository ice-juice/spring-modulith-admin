import { onScopeDispose, ref } from 'vue'
import { getToken } from '@/lib/token'
import { getActivePinia } from 'pinia'
import { useAuth } from '@/stores/auth'
/** Only the latest live request may mutate view state. Writes are never retried. */
export function useLatestRequest() {
  const loading = ref(false), error = ref('')
  let generation = 0, disposed = false
  let controller: AbortController | undefined
  const owner = () => {
    const actor = getActivePinia() ? useAuth().user : null
    return JSON.stringify([getToken(), actor?.id, actor?.portal, actor?.locked, actor ? [...actor.permissions].sort() : []])
  }
  let pending: { key: string; token: string | null; work: Promise<void> } | undefined
  function cancel() { generation++; controller?.abort(); controller = undefined; pending = undefined; loading.value = false }
  window.addEventListener('sma:locked', cancel)
  window.addEventListener('sma:unauthorized', cancel)
  onScopeDispose(() => { disposed = true; cancel(); window.removeEventListener('sma:locked', cancel); window.removeEventListener('sma:unauthorized', cancel) })
  function run<T>(request: (signal: AbortSignal) => Promise<T>, accept: (value: T) => void, key?: string): Promise<void> {
    if (disposed) return Promise.resolve()
    const token = owner()
    if (key !== undefined && pending?.key === key && pending.token === token) return pending.work
    controller?.abort(); controller = new AbortController()
    const signal = controller.signal
    const current = ++generation
    loading.value = true; error.value = ''
    const owns = () => !disposed && !signal.aborted && current === generation && token === owner()
    let settled = false
    const work = (async () => {
      try { const value = await request(signal); if (owns()) accept(value) }
      catch (cause) { if (owns()) error.value = cause instanceof Error ? cause.message : String(cause) }
      finally { settled = true; if (!disposed && current === generation) { loading.value = false; pending = undefined } }
    })()
    if (key !== undefined && !settled) pending = { key, token, work }
    return work
  }
  return { loading, error, run, cancel }
}
