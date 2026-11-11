import { get, put } from '@/api/http'
import { useLatestRequest } from '@/composables/useLatestRequest'
import { useSubmissionPool } from '@/composables/useSubmissionPool'
import { useOperations } from '@/lib/operations'
import { onScopeDispose } from 'vue'

/** Reading an unread item is a write intent; completion receipts are followed by an authorized GET. */
export function useMessageDetail<T>(actions: ReturnType<typeof useSubmissionPool>, namespace: string,
  accept: (value: T) => void, refresh: () => Promise<void>, report: (message: string) => void) {
  const query = useLatestRequest(), operations = useOperations()
  let generation = 0
  onScopeDispose(() => { generation++; query.cancel() })
  async function open(id: string, path: string, markRead: boolean) {
    const resource = namespace + '.' + id
    // A duplicate click must not invalidate the first legitimate detail response.
    if (actions.busy(resource)) return
    const current = ++generation, owns = operations.registry.guard()
    query.cancel()
    if (markRead) {
      const action = actions.get(resource)
      const result = await action.run({ operation: namespace + '.read', resource, protocol: 'LOCAL' },
        () => options => put(path + '/read', undefined, options))
      if (!owns() || current !== generation) return
      if (result.kind !== 'success' && result.kind !== 'completed') { report(action.error.value); return }
    }
    if (!owns() || current !== generation) return
    await query.run(signal => get<T>(path, undefined, { signal }), accept)
    if (!owns() || current !== generation) return
    if (query.error.value) report(query.error.value)
    if (markRead) await refresh()
  }
  return { open }
}
