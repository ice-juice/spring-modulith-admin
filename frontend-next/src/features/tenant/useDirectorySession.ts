import { computed, onUnmounted, ref, watch } from 'vue'
import { getToken } from '@/lib/token'
import { useAuth } from '@/stores/auth'
import { useBranding } from '@/stores/branding'

/** Invalidate in-flight work on identity/permission changes; a same-identity locale refresh is not a new session. */
export function useDirectorySession(permission: string, clear: () => void, interrupt: () => void) {
  const auth = useAuth(), branding = useBranding()
  let epoch = 0
  const alive = ref(true)
  const identity = computed(() => auth.user ? [auth.user.portal, auth.user.id].join(':') : '')
  const actor = () => JSON.stringify([getToken(), identity.value, [...(auth.user?.permissions || [])].sort()])
  let tenantScope = branding.entry?.entry.tenantId
  const accessible = computed(() => alive.value && !!auth.user && !auth.user.locked && auth.hasPermission(permission))
  watch(identity, () => { epoch++; clear() }, { flush: 'sync' })
  // The shell briefly clears entry while refreshing branding. That is not an identity change.
  // Remember the last established tenant and invalidate only a different confirmed scope.
  watch(() => branding.entry?.entry.tenantId, tenantId => {
    if (!tenantId) return
    if (tenantScope !== undefined && tenantScope !== tenantId) { epoch++; clear() }
    tenantScope = tenantId
  })
  watch(() => auth.hasPermission(permission), allowed => { if (!allowed) { epoch++; clear() } })
  watch(() => auth.user?.locked, locked => { if (locked) { epoch++; interrupt() } })
  onUnmounted(() => { alive.value = false; epoch++; clear() })
  return { accessible, ticket: () => ({ epoch, actor: actor() }), valid: (ticket: { epoch: number; actor: string }) => ticket.epoch === epoch && ticket.actor === actor() && accessible.value }
}
