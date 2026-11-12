import { http, post } from '@/api/http'
import { useAuth } from '@/stores/auth'
import { useBranding } from '@/stores/branding'
import { getToken } from './token'
import { operationOwner } from './operation-owner'
import { OperationRegistry } from './operation-registry'
import { BrowserOperationCoordinator } from './browser-operation-coordinator'
import { defineStore } from 'pinia'
import { markRaw, onScopeDispose, watch } from 'vue'

export const useOperations = defineStore('operations', () => {
  const auth = useAuth(), branding = useBranding()
  const owner = () => {
    if (!auth.ready || !branding.ready) return null
    return operationOwner(auth.user, branding.entry?.entry || null, getToken(), new URL(http.defaults.baseURL || '/api', window.location.href).href)
  }
  const registry = markRaw(new OperationRegistry(owner, () => window.localStorage, Date.now, () => crypto.randomUUID(), 100, new BrowserOperationCoordinator()))
  let boundOwner = JSON.stringify(owner())
  const ownerChanged = () => { const next = JSON.stringify(owner()); if (next !== boundOwner) { boundOwner = next; registry.invalidate() } }
  watch(() => JSON.stringify(owner()), ownerChanged, { flush: 'sync' })
  const invalidate = () => registry.invalidate()
  const storageChanged = (event: StorageEvent) => {
    ownerChanged()
    if (event.key === null || event.key?.startsWith('sma.operation.v1.')) {
      try { registry.synchronize() } catch { /* The registry retains its fail-closed storage state. */ }
    }
  }
  window.addEventListener('storage', storageChanged)
  window.addEventListener('sma:locked', invalidate)
  window.addEventListener('sma:unauthorized', invalidate)
  onScopeDispose(() => { window.removeEventListener('storage', storageChanged); window.removeEventListener('sma:locked', invalidate); window.removeEventListener('sma:unauthorized', invalidate); registry.invalidate() })
  return { registry }
})

/** Read-only result lookup. It never reconstructs or repeats the original mutation. */
export function lookupLocalOperation(key: string) {
  return useOperations().registry.lookupLocal(key, record => post('/common/requests/lookup', { key: record.key }))
}
