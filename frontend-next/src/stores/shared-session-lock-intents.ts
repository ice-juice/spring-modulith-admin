import { defineStore } from 'pinia'
import { onScopeDispose, ref, watch } from 'vue'
import { useAuth } from './auth'
import { getToken } from '@/lib/token'
import { i18n } from '@/locales'
import { BrowserOperationCoordinator, CoordinationError, type OperationLease } from '@/lib/browser-operation-coordinator'
import type { CredentialTicket } from './credential-intents'

type Action = 'LOCK' | 'UNLOCK'
interface Marker { schema: 2; scope: string; key: string; action: Action; state: 'UNKNOWN' | 'LOCKED' | 'UNLOCKED' }
interface Held { scope: string; action: Action; sent: boolean; lease?: OperationLease }
const prefix = 'sma.session-lock-intent.v2.', legacyKey = 'sma.session-lock-intent.v1'
const uuid = /^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/, hashPattern = /^[a-f0-9]{64}$/
function parse(raw: string | null): Marker | null {
  if (!raw) return null
  if (raw.length > 512) throw new Error('storage')
  const row = JSON.parse(raw) as Marker
  if (Object.keys(row).sort().join(',') !== 'action,key,schema,scope,state' || row.schema !== 2 || !hashPattern.test(row.scope) || !uuid.test(row.key)
    || !['LOCK', 'UNLOCK'].includes(row.action) || !['UNKNOWN', 'LOCKED', 'UNLOCKED'].includes(row.state) || row.state === 'UNLOCKED' && row.action !== 'UNLOCK') throw new Error('storage')
  return row
}
function legacy(): Marker | null {
  const raw = sessionStorage.getItem(legacyKey)
  if (!raw) return null
  if (raw.length > 512) throw new Error('storage')
  const row = JSON.parse(raw)
  if (Object.keys(row).sort().join(',') !== 'key,schema,scope,state' || row.schema !== 1 || row.state !== 'UNKNOWN' || !hashPattern.test(row.scope) || !uuid.test(row.key)) throw new Error('storage')
  return { schema: 2, scope: row.scope, key: row.key, action: 'LOCK', state: 'UNKNOWN' }
}

/** Browser intent evidence does not authorize access; UNKNOWN never becomes unlocked from a state read. */
export const useSessionLockIntents = defineStore('session-lock-intents', () => {
  const auth = useAuth(), coordinator = new BrowserOperationCoordinator()
  const busy = ref(false), unresolved = ref(false), unavailable = ref(false), ready = ref(false), recovering = ref(false), problem = ref(''), status = ref<Marker | null>(null)
  const peerUnlockKey = ref('')
  let scope = '', bound = '\u0000', generation = 0, live = true, active: CredentialTicket | null = null, initializing = Promise.resolve()
  const held = new Map<CredentialTicket, Held>()
  const identity = () => auth.user && getToken() ? JSON.stringify([window.location.origin, auth.user.portal, auth.user.id, getToken()]) : ''
  const target = (hash: string) => ({ owner: 'session-lock:' + hash, resource: 'lock' })
  function fail(cause: unknown) { const reason = cause instanceof CoordinationError ? cause.reason : 'storage'; problem.value = i18n.global.t('common.operation.' + reason); if (reason !== 'busy') unavailable.value = true }
  function read(hash = scope) {
    const row = parse(localStorage.getItem(prefix + hash)), old = legacy()
    if (row && row.scope !== hash || row && old?.scope === hash && (row.key !== old.key || row.state !== 'UNKNOWN')) throw new Error('storage')
    return row || (old?.scope === hash ? old : null)
  }
  function synchronize() {
    if (!scope) return
    try {
      const row = read()
      if (!row && status.value && status.value.state !== 'UNLOCKED') throw new Error('storage')
      status.value = row; unresolved.value = row?.state === 'UNKNOWN'
    } catch (cause) { fail(cause) }
  }
  function put(row: Marker) {
    if (localStorage.length > 10000) throw new Error('storage')
    let count = 0
    for (let i = 0; i < localStorage.length; i++) if (localStorage.key(i)?.startsWith(prefix)) count++
    if (!localStorage.getItem(prefix + row.scope) && count >= 256) throw new CoordinationError('capacity')
    localStorage.setItem(prefix + row.scope, JSON.stringify(row))
  }
  async function release(ticket: CredentialTicket) { const item = held.get(ticket); held.delete(ticket); if (active === ticket) { active = null; busy.value = false }; await item?.lease?.release() }
  async function migrate(hash: string, current: () => boolean) {
    if (legacy()?.scope !== hash) return
    await coordinator.transaction('session-lock-storage-v2', () => {
      if (!current()) return
      const old = legacy(), row = read(hash)
      if (old?.scope === hash && row?.key === old.key) { put(old); sessionStorage.removeItem(legacyKey) }
    })
  }
  function refresh(value: string) {
    if (value === bound) return
    bound = value; const version = ++generation
    if (active && !held.get(active)?.sent) void release(active)
    active = null; busy.value = false; ready.value = false; unavailable.value = false; unresolved.value = false; problem.value = ''; scope = ''; status.value = null; peerUnlockKey.value = ''
    try {
      recovering.value = !!value && !!legacy()
      if (value && !recovering.value) {
        if (localStorage.length > 10000) throw new Error('storage')
        for (let i = 0; i < localStorage.length; i++) {
          const key = localStorage.key(i)
          if (key?.startsWith(prefix) && parse(localStorage.getItem(key))?.state !== 'UNLOCKED') { recovering.value = true; break }
        }
      }
    } catch { recovering.value = !!value }
    initializing = (async () => {
      if (!value) return
      let lease: OperationLease | undefined
      const current = () => live && version === generation
      try {
        const bytes = new TextEncoder().encode(value); let digest: ArrayBuffer
        try { digest = await crypto.subtle.digest('SHA-256', bytes) } finally { bytes.fill(0) }
        if (!current()) return
        scope = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('')
        if (legacy()?.scope === scope) { lease = await coordinator.acquire(target(scope)); await migrate(scope, current) }
        if (current()) { synchronize(); ready.value = true }
      } catch (cause) { if (current()) { fail(cause); if (cause instanceof CoordinationError && cause.reason === 'busy' && scope) { synchronize(); ready.value = true } } }
      finally { await lease?.release() }
    })()
  }
  watch(identity, refresh, { immediate: true, flush: 'sync' })
  const changed = () => { if (identity() !== bound) refresh(identity()); else synchronize() }
  const notified = () => {
    const previous = status.value
    changed()
    if (ready.value && !unavailable.value && status.value?.state === 'UNLOCKED'
      && (previous?.key !== status.value.key || previous.state !== 'UNLOCKED')) peerUnlockKey.value = status.value.key
  }
  window.addEventListener('storage', notified)
  onScopeDispose(() => { live = false; generation++; if (active && !held.get(active)?.sent) void release(active); window.removeEventListener('storage', notified) })
  async function begin(action: Action): Promise<CredentialTicket | null> {
    changed()
    if (busy.value || !auth.user || !getToken()) return null
    const version = generation, snapshot = identity(), token = getToken()!
    const ticket: CredentialTicket = { key: crypto.randomUUID(), token, current: () => live && version === generation && snapshot === identity() }
    const item: Held = { scope: '', action, sent: false }; held.set(ticket, item); active = ticket; busy.value = true; problem.value = ''
    try {
      await initializing
      if (!ticket.current() || !ready.value || unavailable.value) { await release(ticket); return null }
      item.scope = scope
      const lease = await coordinator.acquire(target(scope))
      if (!ticket.current() || held.get(ticket) !== item) { await lease.release(); await release(ticket); return null }
      item.lease = lease; await migrate(item.scope, ticket.current)
      if (!ticket.current()) { await release(ticket); return null }
      synchronize()
      if (unavailable.value || unresolved.value || action === 'LOCK' && status.value?.state === 'LOCKED') { await release(ticket); return null }
      return ticket
    } catch (cause) { if (ticket.current()) fail(cause); await release(ticket); return null }
  }
  async function sent(ticket: CredentialTicket) {
    const item = held.get(ticket)
    if (active !== ticket || !ticket.current() || !item?.lease || item.sent) return false
    try {
      await coordinator.transaction('session-lock-storage-v2', () => {
        if (!ticket.current() || active !== ticket || read(item.scope)?.state === 'UNKNOWN') throw new Error('storage')
        const row: Marker = { schema: 2, scope: item.scope, key: ticket.key, action: item.action, state: 'UNKNOWN' }
        put(row); item.sent = true; status.value = row; unresolved.value = true
      })
      return ticket.current()
    } catch (cause) { if (ticket.current()) fail(cause); return false }
  }
  function settle(ticket: CredentialTicket, outcome: 'success' | 'rejected') {
    const item = held.get(ticket)
    if (active !== ticket || !ticket.current() || !item?.lease || !item.sent) return false
    if (item.action === 'LOCK' && outcome !== 'success') return false
    try {
      const row = read(item.scope)
      if (!row || row.key !== ticket.key || row.action !== item.action || row.state !== 'UNKNOWN') throw new Error('storage')
      const completed: Marker = { ...row, state: item.action === 'UNLOCK' && outcome === 'success' ? 'UNLOCKED' : 'LOCKED' }
      put(completed); unresolved.value = false; status.value = completed
      return true
    } catch (cause) { fail(cause); return false }
  }
  function confirmedUnlock(key: string) {
    changed()
    return ready.value && !unavailable.value && !unresolved.value && status.value?.key === key && status.value.state === 'UNLOCKED'
  }
  return { busy, unresolved, unavailable, ready, recovering, problem, status, peerUnlockKey, begin, sent, settle, release, confirmedUnlock }
})
