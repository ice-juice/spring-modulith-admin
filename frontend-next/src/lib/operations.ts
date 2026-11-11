import { http, post } from '@/api/http'
import { useAuth } from '@/stores/auth'
import { useBranding } from '@/stores/branding'
import { getToken } from './token'
import { operationOwner } from './operation-owner'
import { OperationRegistry } from './operation-registry'
import { defineStore } from 'pinia'
import { markRaw, onScopeDispose, watch } from 'vue'

export const useOperations = defineStore('operations', () => {
  const auth = useAuth(), branding = useBranding()
  const owner = () => {
    if (!auth.ready || !branding.ready) return null
    return operationOwner(auth.user, branding.entry?.entry || null, getToken(), new URL(http.defaults.baseURL || '/api', window.location.href).href)
  }
  const registry = markRaw(new OperationRegistry(owner, () => window.localStorage))
  watch(() => JSON.stringify(owner()), () => registry.invalidate(), { flush: 'sync' })
  const invalidate = () => registry.invalidate()
  window.addEventListener('sma:locked', invalidate)
  window.addEventListener('sma:unauthorized', invalidate)
  onScopeDispose(() => { window.removeEventListener('sma:locked', invalidate); window.removeEventListener('sma:unauthorized', invalidate); registry.invalidate() })
  return { registry }
})

/** Read-only result lookup. It never reconstructs or repeats the original mutation. */
export function lookupLocalOperation(key: string) {
  return useOperations().registry.lookupLocal(key, record => post('/common/requests/lookup', { key: record.key }))
}
