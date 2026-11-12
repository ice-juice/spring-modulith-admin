import { defineStore } from 'pinia'
import { onScopeDispose, ref, watch } from 'vue'
import { useAuth } from './auth'
import { getToken } from '@/lib/token'
import { i18n } from '@/locales'
import { BrowserOperationCoordinator, CoordinationError, type OperationLease } from '@/lib/browser-operation-coordinator'
import type { CredentialTicket } from './credential-intents'

const legacyKey = 'sma.credential-intent.v1', prefix = 'sma.credential-intent.v2.'
interface Marker { schema: 1; scope: string; key: string; state: 'UNKNOWN' }
interface Held { lease?: OperationLease; sent: boolean; scope: string }
function parse(raw: string | null): Marker | null {
  if (!raw) return null
  if (raw.length > 512) throw new Error('storage')
  const value = JSON.parse(raw) as Marker
  if (Object.keys(value).sort().join(',') !== 'key,schema,scope,state' || value.schema !== 1 || value.state !== 'UNKNOWN'
    || !/^[a-f0-9]{64}$/.test(value.scope) || !/^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/.test(value.key)) throw new Error('storage')
  return value
}

/** Shared metadata only; a new authenticated session has its own digest, never the old request body. */
export const useCredentialIntents = defineStore('credential-intents', () => {
  const auth = useAuth(), coordinator = new BrowserOperationCoordinator()
  const busy = ref(false), unresolved = ref(false), unavailable = ref(false), ready = ref(false), recovering = ref(false), problem = ref('')
  let generation = 0, scope = '', bound = '\u0000', active: CredentialTicket | null = null, live = true, initializing = Promise.resolve()
  const held = new Map<CredentialTicket, Held>()
  const identity = () => auth.user && getToken() ? JSON.stringify([window.location.origin, auth.user.portal, auth.user.id, getToken()]) : ''
  const target = (hash: string) => ({ owner: 'credential-session:' + hash, resource: 'credentials' })
  function fail(cause: unknown) {
    const reason = cause instanceof CoordinationError ? cause.reason : 'storage'
    problem.value = i18n.global.t('common.operation.' + reason)
    if (reason !== 'busy') unavailable.value = true
  }
  function marker(hash = scope) {
    const saved = parse(localStorage.getItem(prefix + hash)), old = parse(sessionStorage.getItem(legacyKey))
    if (saved && saved.scope !== hash) throw new Error('storage')
    if (saved && old?.scope === hash && old.key !== saved.key) throw new Error('storage')
    return saved || (old?.scope === hash ? old : null)
  }
  function synchronize() {
    if (!scope) return
    try { unresolved.value = !!marker() } catch (cause) { fail(cause) }
  }
  function putMarker(value: Marker) {
    if (localStorage.length > 10000) throw new Error('storage')
    let count = 0
    for (let index = 0; index < localStorage.length; index++) if (localStorage.key(index)?.startsWith(prefix)) count++
    if (!localStorage.getItem(prefix + value.scope) && count >= 256) throw new CoordinationError('capacity')
    localStorage.setItem(prefix + value.scope, JSON.stringify(value))
  }
  async function release(ticket: CredentialTicket) {
    const entry = held.get(ticket); held.delete(ticket)
    if (active === ticket) { active = null; busy.value = false }
    await entry?.lease?.release()
  }
  function refresh(value: string) {
    if (value === bound) return
    bound = value; const version = ++generation
    if (active && !held.get(active)?.sent) void release(active)
    active = null; scope = ''; busy.value = false; ready.value = false; unresolved.value = false; unavailable.value = false; problem.value = ''
    try { recovering.value = !!value && !!sessionStorage.getItem(legacyKey) } catch { recovering.value = !!value }
    initializing = (async () => {
      if (!value) return
      let lease: OperationLease | undefined
      try {
        const bytes = new TextEncoder().encode(value)
        let digest: ArrayBuffer
        try { digest = await crypto.subtle.digest('SHA-256', bytes) } finally { bytes.fill(0) }
        if (!live || version !== generation) return
        const hash = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('')
        scope = hash
        const old = parse(sessionStorage.getItem(legacyKey))
        if (old?.scope === hash) {
          lease = await coordinator.acquire(target(hash))
          await coordinator.transaction('credential-storage-v2', () => {
            if (!live || version !== generation) return
            const saved = marker(hash)
            if (!saved || saved.key !== old.key) throw new Error('storage')
            putMarker(old) // Persist first; a cleanup failure never loses the old unknown intent.
            sessionStorage.removeItem(legacyKey)
          })
        }
        if (!live || version !== generation) return
        synchronize(); ready.value = true
      } catch (cause) {
        if (version === generation) {
          fail(cause)
          // Another tab may hold the scope while this tab upgrades an old local marker.
          // Keep recovery available; the next explicit attempt migrates under its acquired lock.
          if (cause instanceof CoordinationError && cause.reason === 'busy' && scope) { synchronize(); ready.value = true }
        }
      }
      finally { await lease?.release() }
    })()
  }
  watch(identity, refresh, { immediate: true, flush: 'sync' })
  watch(() => auth.user?.locked, () => {
    if (!ready.value || !scope) { bound = '\u0000'; refresh(identity()); return }
    generation++
    if (active && !held.get(active)?.sent) void release(active)
    active = null; busy.value = false; synchronize()
  }, { flush: 'sync' })
  const changed = () => { if (identity() !== bound) refresh(identity()); else synchronize() }
  window.addEventListener('storage', changed)
  onScopeDispose(() => { live = false; generation++; if (active && !held.get(active)?.sent) void release(active); window.removeEventListener('storage', changed) })
  async function reserve(query = false): Promise<CredentialTicket | null> {
    changed()
    if (busy.value || !auth.user || auth.user.locked || !getToken()) return null
    const version = generation, token = getToken()!, snapshot = identity()
    const ticket: CredentialTicket = { key: crypto.randomUUID(), token, current: () => live && version === generation && snapshot === identity() }
    const entry: Held = { sent: false, scope: '' }; held.set(ticket, entry); active = ticket; busy.value = true; problem.value = ''
    try {
      await initializing
      if (!ticket.current() || !ready.value || unavailable.value || auth.user?.locked) { await release(ticket); return null }
      entry.scope = scope
      const lease = await coordinator.acquire(target(entry.scope))
      if (!ticket.current() || auth.user?.locked || held.get(ticket) !== entry) { await lease.release(); await release(ticket); return null }
      entry.lease = lease
      if (parse(sessionStorage.getItem(legacyKey))?.scope === entry.scope) {
        await coordinator.transaction('credential-storage-v2', () => {
          if (!ticket.current()) return
          const old = parse(sessionStorage.getItem(legacyKey)), saved = marker(entry.scope)
          if (old?.scope === entry.scope && saved?.key === old.key) { putMarker(old); sessionStorage.removeItem(legacyKey) }
        })
      }
      if (!ticket.current() || auth.user?.locked) { await release(ticket); return null }
      synchronize()
      if (unavailable.value || (!query && unresolved.value) || (query && !unresolved.value)) { await release(ticket); return null }
      return ticket
    } catch (cause) { if (ticket.current()) fail(cause); await release(ticket); return null }
  }
  const begin = () => reserve()
  async function sent(ticket: CredentialTicket) {
    const entry = held.get(ticket)
    if (active !== ticket || !ticket.current() || !entry?.lease || entry.sent || auth.user?.locked) return false
    try {
      await coordinator.transaction('credential-storage-v2', () => {
        if (active !== ticket || !ticket.current() || auth.user?.locked || marker(entry.scope)) throw new Error('storage')
        putMarker({ schema: 1, scope: entry.scope, key: ticket.key, state: 'UNKNOWN' })
        entry.sent = true; unresolved.value = true
      })
      return ticket.current() && !auth.user?.locked
    } catch (cause) { if (ticket.current()) fail(cause); return false }
  }
  function clearKnown(hash: string, key: string) {
    const saved = marker(hash)
    if (!saved || saved.key !== key) throw new Error('storage')
    localStorage.removeItem(prefix + hash)
    const old = parse(sessionStorage.getItem(legacyKey))
    if (old?.scope === hash && old.key === key) sessionStorage.removeItem(legacyKey)
    unresolved.value = false
  }
  function settle(ticket: CredentialTicket, confirmed: boolean) {
    const entry = held.get(ticket)
    if (active !== ticket || !ticket.current() || !entry?.lease || !entry.sent || !confirmed) return
    try { clearKnown(entry.scope, ticket.key) } catch (cause) { fail(cause) }
  }
  function pendingKey() { changed(); if (!ready.value || unavailable.value || !unresolved.value) return null; try { return marker()?.key || null } catch (cause) { fail(cause); return null } }
  /** Query and validate the original receipt under the same lock as mutation; never replay input. */
  async function check<T>(fetch: (key: string, token: string) => Promise<T>, completed: (value: T) => boolean) {
    const ticket = await reserve(true)
    if (!ticket) return undefined
    try {
      const key = marker()!.key, result = await fetch(key, ticket.token)
      if (!ticket.current() || auth.user?.locked) return undefined
      if (completed(result)) {
        try { clearKnown(scope, key) } catch (cause) { fail(cause); throw new Error(problem.value) }
      }
      return result
    } finally { await release(ticket) }
  }
  return { busy, unresolved, unavailable, ready, recovering, problem, begin, sent, settle, release, pendingKey, check }
})
