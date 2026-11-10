import { onScopeDispose, ref } from 'vue'
/** Only the latest live request may mutate view state. Writes are never retried. */
export function useLatestRequest() {
  const loading = ref(false), error = ref('')
  let generation = 0, disposed = false
  onScopeDispose(() => { disposed = true; generation++ })
  async function run<T>(request: () => Promise<T>, accept: (value: T) => void) {
    const current = ++generation
    loading.value = true; error.value = ''
    try { const value = await request(); if (!disposed && current === generation) accept(value) }
    catch (cause) { if (!disposed && current === generation) error.value = cause instanceof Error ? cause.message : String(cause) }
    finally { if (!disposed && current === generation) loading.value = false }
  }
  return { loading, error, run }
}
