import { computed, onScopeDispose, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ApiError, post, remove } from '@/api/http'
import { useAuth } from '@/stores/auth'
import { useOperations } from '@/lib/operations'
import { getToken } from '@/lib/token'
import { confirmAction } from '@/lib/feedback'
import { useSubmissionPool } from './useSubmissionPool'

/** Native telemetry reset / publication resubmission have no historical operation receipt. */
export function useMonitorCommands() {
  const auth = useAuth(), { t } = useI18n(), registry = useOperations().registry, pool = useSubmissionPool(registry)
  const revision = ref(0), error = ref('')
  const unsubscribe = registry.subscribe(() => { revision.value++ })
  const identity = () => JSON.stringify([getToken(), auth.user?.id, auth.user?.portal, auth.user?.locked, auth.user?.superAdmin, [...(auth.user?.permissions || [])].sort()])
  let epoch = 0, live = true
  const records = computed(() => {
    void revision.value
    try { return registry.list().filter(row => ['monitor.sql.reset', 'monitor.publication.retry'].includes(row.operation)) }
    catch { return [] }
  })
  const sqlResource = 'monitor.sql.statistics', publicationResource = (id: string) => 'monitor.publications.' + id
  function blocked(resource: string) { return records.value.some(row => row.resource === resource) }
  function unknown(operation: string) { return records.value.some(row => row.operation === operation && ['PENDING', 'UNKNOWN'].includes(row.state)) }
  function slow(operation: string) { return records.value.some(row => row.operation === operation && row.state === 'SLOW') }
  async function run(id?: string) {
    const permission = id === undefined ? 'monitor:sql:reset' : 'monitor:event:retry'
    if (!live || !auth.user || auth.user.locked || !auth.hasPermission(permission) || !getToken()) return false
    const token = getToken()!, version = epoch, owner = identity(), operation = id === undefined ? 'monitor.sql.reset' : 'monitor.publication.retry'
    const current = () => live && version === epoch && owner === identity()
    try {
      const action = pool.get(id === undefined ? sqlResource : publicationResource(id))
      const result = await action.run({ operation, resource: id === undefined ? sqlResource : publicationResource(id), protocol: 'NATIVE' }, async () => {
        if (!await confirmAction(t(id === undefined ? 'monitor.sqlResetConfirm' : 'monitor.retryConfirm'), t('common.confirm'), id === undefined) || !current()) return
        return options => id === undefined ? remove('/monitor/sql', { ...options, expectedIdentity: token })
          : post<void>('/monitor/publications/' + encodeURIComponent(id) + '/retry', undefined, { ...options, expectedIdentity: token })
      }, { success: () => 'completed', failure: cause => cause instanceof ApiError && [400, 401, 403, 404, 423, 429].includes(cause.code) ? 'rejected' : 'unknown' })
      if (!current()) return false
      error.value = action.error.value
      return result.kind === 'success' // Acknowledges this command only, not asynchronous listener completion.
    } catch (cause) { if (current()) error.value = cause instanceof Error ? cause.message : t('common.networkError'); return false }
  }
  watch(identity, () => { epoch++; error.value = '' }, { flush: 'sync' })
  onScopeDispose(() => { live = false; epoch++; unsubscribe() })
  return { error, identity, sqlResource, publicationResource, blocked, unknown, slow, run }
}
