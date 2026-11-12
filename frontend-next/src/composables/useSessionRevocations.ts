import { computed, onScopeDispose, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ApiError, get, post, type PageResult } from '@/api/http'
import { useAuth } from '@/stores/auth'
import { getToken } from '@/lib/token'
import { confirmAction } from '@/lib/feedback'
import { useOperations } from '@/lib/operations'
import type { OperationRegistry } from '@/lib/operation-registry'
import { useSubmissionPool } from './useSubmissionPool'
import { useLatestRequest } from './useLatestRequest'

export interface SessionView {
  id: string; username: string; name: string; ip: string; userAgent: string
  lastSeenAt: string; expiresAt: string; locked: boolean; current: boolean
}
const prefix = 'auth.sessions.'
/** A list is current state, never a receipt for a previous native revocation. */
export function useSessionRevocations(supplied?: OperationRegistry) {
  const auth = useAuth(), { t, locale } = useI18n(), registry = supplied || useOperations().registry
  const pool = useSubmissionPool(registry), request = useLatestRequest()
  const rows = ref<SessionView[]>([]), keyword = ref(''), page = ref(1), total = ref(0), error = ref(''), revision = ref(0)
  let epoch = 0, live = true
  const unsubscribe = registry.subscribe(() => { revision.value++ })
  const available = () => !!auth.user && !auth.user.locked && !!getToken()
  const identity = () => JSON.stringify([getToken(), auth.user?.id, auth.user?.portal, auth.user?.locked,
    auth.user?.superAdmin, auth.user ? [...auth.user.permissions].sort() : []])
  const records = computed(() => {
    void revision.value
    try { return registry.list().filter(row => row.operation === 'auth.session.revoke' && row.resource.startsWith(prefix)) }
    catch { return [] } // reserve still fails closed and displays the storage error.
  })
  const unresolved = computed(() => records.value.some(row => ['PENDING', 'UNKNOWN'].includes(row.state)))
  const slow = computed(() => records.value.some(row => row.state === 'SLOW'))
  function blocked(id: string) { return records.value.some(row => row.resource === prefix + id) }
  async function load() {
    if (!available() || !auth.hasPermission('auth:session:view')) return
    const token = getToken()!, params = { page: page.value, size: 20, keyword: keyword.value }
    await request.run(signal => get<PageResult<SessionView>>('/auth/sessions', params, { signal, expectedIdentity: token }),
      result => { rows.value = result.items; total.value = Number(result.total) }, JSON.stringify([params, locale.value]))
  }
  function refresh() { request.cancel(); return load() }
  function search() { page.value = 1; return load() }
  async function revoke(row: SessionView) {
    if (!live || !available() || !auth.hasPermission('auth:session:revoke') || row.current) return
    const id = row.id, token = getToken()!, snapshot = identity(), version = epoch
    const current = () => live && version === epoch && snapshot === identity()
    try {
      const action = pool.get(prefix + id)
      const result = await action.run({ operation: 'auth.session.revoke', resource: prefix + id, protocol: 'NATIVE' }, async () => {
        if (!await confirmAction(t('auth.forceLogoutConfirm'), t('common.confirm'), true) || !current()) return
        return options => post<void>('/auth/sessions/' + encodeURIComponent(id) + '/revoke', undefined, { ...options, expectedIdentity: token })
      }, {
        success: () => 'completed',
        // Only explicit pre-execution refusals release this new attempt. Transport/5xx/409 stay unknown.
        failure: cause => cause instanceof ApiError && [400, 401, 403, 404, 423, 429].includes(cause.code) ? 'rejected' : 'unknown',
      })
      if (!current()) return
      error.value = action.error.value
      if (result.kind === 'success') await refresh()
    } catch (cause) { if (current()) error.value = cause instanceof Error ? cause.message : t('common.networkError') }
  }
  watch(identity, () => {
    epoch++; request.cancel(); registry.invalidate(); rows.value = []; total.value = 0; keyword.value = ''; page.value = 1; error.value = ''; request.error.value = ''
    void load()
  }, { flush: 'sync' })
  watch(locale, () => { void refresh() }, { immediate: true })
  onScopeDispose(() => { live = false; epoch++; unsubscribe() })
  return { rows, keyword, page, total, error, request, unresolved, slow, blocked, load, refresh, search, revoke }
}
