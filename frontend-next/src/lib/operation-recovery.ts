import type { OperationRecord } from './operation-registry'

/** Destinations are code-owned. Persisted metadata must never supply a URL or query string. */
const destinations: readonly [string, string][] = [
  ['config.preferences.', '/preferences'], ['iam.profile.', '/profile'],
  ['iam.users.', '/iam/users'], ['iam.roles.', '/iam/roles'], ['iam.departments.', '/iam/departments'],
  ['iam.posts.', '/iam/posts'], ['iam.permissions.', '/iam/permissions'], ['iam.navigation.', '/iam/navigation'],
  ['config.parameters.', '/config/parameters'], ['config.dictionaries.', '/config/dictionaries'],
  ['config.shared.', '/config/shared'], ['auth.sessions.', '/auth/sessions'],
  ['tenant.brands.', '/tenant/brand'], ['tenant.domains.', '/tenant/domains'], ['tenant.tenants.', '/tenant/tenants'],
  ['monitor.caches.', '/monitor/caches'], ['monitor.cache-cancel.', '/monitor/caches'],
  ['monitor.sql.', '/monitor/sql'], ['monitor.publications.', '/monitor/events'],
  ['extension.', '/extension/manage'], ['openapi.apps.', '/openapi/apps'],
  ['file.avatar.', '/profile'], ['file.brand.', '/tenant/brand'], ['file.notice.', '/message/notices'], ['file.', '/file/files'],
  ['message.notices.manage.', '/message/notices'], ['message.notices.', '/message/inbox'],
  ['message.private.', '/message/inbox'], ['message.todos.', '/message/todos'],
]
export function operationRecoveryPath(row: Pick<OperationRecord, 'operation' | 'resource'>): string | undefined {
  if (row.resource.startsWith('rule.')) {
    if (row.operation.startsWith('rule.template-')) return '/rule/templates'
    if (row.operation.startsWith('rule.scene-')) return '/rule/scenes'
    if (row.operation.startsWith('rule.instance-') || ['rule.execute', 'rule.trial', 'rule.upgrade-draft'].includes(row.operation)) return '/rule/instances'
    if (row.operation === 'rule.batch' || row.resource.startsWith('rule.maintenance.')) return '/rule/operations'
    return undefined // An ambiguous asset kind cannot determine its authorized editor.
  }
  return destinations.find(([prefix]) => row.resource.startsWith(prefix))?.[1]
}

interface RecoveryRoute { matched: readonly unknown[]; meta: Record<string, unknown> }
export function permittedRecoveryPath(row: Pick<OperationRecord, 'operation' | 'resource'>,
  resolve: (path: string) => RecoveryRoute, allowed: (permission: string) => boolean): string | undefined {
  const path = operationRecoveryPath(row)
  if (!path) return
  const route = resolve(path)
  if (!route.matched.length || route.meta.public) return
  const permission = route.meta.permission, alternatives = route.meta.anyPermission
  if (typeof permission === 'string' && !allowed(permission)
    && !(Array.isArray(alternatives) && alternatives.some(value => typeof value === 'string' && allowed(value)))) return
  return path
}
