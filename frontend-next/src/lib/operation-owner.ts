import type { OperationOwner } from './operation-registry'

interface Actor { id: string; portal: string; permissions: string[]; locked?: boolean }
interface Entry { tenantId: string; portal: string }
/** Scope comes from the authenticated user and validated entry, never a route tenant parameter. */
export function operationOwner(actor: Actor | null, entry: Entry | null, token: string | null, apiScope: string): OperationOwner | null {
  if (!actor || !entry || !token || actor.locked || actor.portal !== entry.portal || !actor.id || !entry.tenantId) return null
  return {
    scope: [apiScope, entry.tenantId, entry.portal, actor.id],
    // A locale/theme refresh preserves ownership. Revocation or a new session invalidates late responses.
    session: JSON.stringify([token, [...actor.permissions].sort()]),
  }
}
