import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { webcrypto } from 'node:crypto'
import { createPinia, disposePinia } from 'pinia'
import { useAuth, type CurrentUser } from '../auth'
import { useSessionLockIntents } from '../shared-session-lock-intents'
import { saveToken } from '@/lib/token'
import { browserLocksFixture, sessionLockMarker } from '@/lib/__tests__/browser-locks.fixture'
const actor: CurrentUser = { id: '1', username: 'fixture', name: 'Fixture', portal: 'admin', permissions: [], superAdmin: false, menus: [], navigation: [] }
const prefix = 'sma.session-lock-intent.v2.', legacy = 'sma.session-lock-intent.v1'
const pins: ReturnType<typeof createPinia>[] = []
let manager: ReturnType<typeof browserLocksFixture>
beforeEach(() => { vi.stubGlobal('crypto', webcrypto); localStorage.clear(); sessionStorage.clear(); manager = browserLocksFixture(); Object.defineProperty(navigator, 'locks', { configurable: true, value: manager.locks }) })
afterEach(() => { for (const pin of pins.splice(0)) disposePinia(pin); vi.restoreAllMocks(); vi.unstubAllGlobals() })
async function harness(token = 'private-lock-token') {
  const pin = createPinia(); pins.push(pin); const auth = useAuth(pin); saveToken(token, false); auth.user = { ...actor }
  const locks = useSessionLockIntents(pin); await vi.waitFor(() => expect(locks.ready || locks.unavailable).toBe(true))
  return { auth, locks, pin }
}
async function lock(first: Awaited<ReturnType<typeof harness>>) {
  const ticket = (await first.locks.begin('LOCK'))!; expect(ticket).not.toBeNull(); expect(await first.locks.sent(ticket)).toBe(true)
  expect(first.locks.settle(ticket, 'success')).toBe(true); await first.locks.release(ticket)
}
it('serializes lock and unlock across stores and shares only bounded state metadata', async () => {
  const a = await harness(), b = await harness(), ticket = (await a.locks.begin('LOCK'))!
  expect(await b.locks.begin('LOCK')).toBeNull(); expect(await b.locks.begin('UNLOCK')).toBeNull()
  await a.locks.sent(ticket); window.dispatchEvent(new Event('storage')); expect(b.locks.unresolved).toBe(true)
  a.locks.settle(ticket, 'success'); await a.locks.release(ticket); window.dispatchEvent(new Event('storage'))
  expect(b.locks.status?.state).toBe('LOCKED'); expect(b.locks.unresolved).toBe(false)
  const unlock = (await b.locks.begin('UNLOCK'))!; await b.locks.sent(unlock); b.locks.settle(unlock, 'success'); await b.locks.release(unlock)
  window.dispatchEvent(new Event('storage')); expect(a.locks.peerUnlockKey).toBe(unlock.key); expect(a.locks.confirmedUnlock(unlock.key)).toBe(true)
  const raw = sessionLockMarker()!; expect(Object.keys(JSON.parse(raw)).sort()).toEqual(['action', 'key', 'schema', 'scope', 'state'])
  expect(raw).not.toContain('private-lock-token'); expect(raw.length).toBeLessThanOrEqual(512); expect(manager.held.size).toBe(0)
})
it('unknown lock or unlock remains shielded after reconstruction and cannot be overwritten or settled by an unrelated ticket', async () => {
  for (const action of ['LOCK', 'UNLOCK'] as const) {
    localStorage.clear()
    const first = await harness(); if (action === 'UNLOCK') await lock(first)
    const ticket = (await first.locks.begin(action))!; await first.locks.sent(ticket); await first.locks.release(ticket)
    const restored = await harness(); expect(restored.locks.unresolved).toBe(true)
    expect(await restored.locks.begin('LOCK')).toBeNull(); expect(await restored.locks.begin('UNLOCK')).toBeNull()
    expect(restored.locks.settle(ticket, 'success')).toBe(false); expect(restored.locks.confirmedUnlock(ticket.key)).toBe(false)
  }
})
it('explicit wrong-password refusal remains locked and permits a fresh explicit unlock, while rejected lock stays unknown', async () => {
  const first = await harness(); await lock(first)
  const wrong = (await first.locks.begin('UNLOCK'))!; await first.locks.sent(wrong); expect(first.locks.settle(wrong, 'rejected')).toBe(true); await first.locks.release(wrong)
  expect(first.locks.status?.state).toBe('LOCKED'); expect(first.locks.peerUnlockKey).toBe('')
  const correct = (await first.locks.begin('UNLOCK'))!; await first.locks.sent(correct); first.locks.settle(correct, 'success'); await first.locks.release(correct)
  const rejected = (await first.locks.begin('LOCK'))!; await first.locks.sent(rejected); expect(first.locks.settle(rejected, 'rejected')).toBe(false); await first.locks.release(rejected)
  expect(first.locks.unresolved).toBe(true)
})
it('initial historical UNLOCKED is not a live peer unlock signal and a new lock invalidates the previous key', async () => {
  const a = await harness(); await lock(a)
  const ticket = (await a.locks.begin('UNLOCK'))!; await a.locks.sent(ticket); a.locks.settle(ticket, 'success'); await a.locks.release(ticket)
  const b = await harness(); expect(b.locks.status?.state).toBe('UNLOCKED'); expect(b.locks.peerUnlockKey).toBe('')
  await lock(a); window.dispatchEvent(new Event('storage')); expect(b.locks.confirmedUnlock(ticket.key)).toBe(false)
})
it('migrates legacy unknown without allowing an unlock or dropping a conflict', async () => {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(JSON.stringify([window.location.origin, 'admin', '1', 'private-lock-token'])))
  const scope = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('')
  const old = { schema: 1, scope, key: crypto.randomUUID(), state: 'UNKNOWN' }
  sessionStorage.setItem(legacy, JSON.stringify(old)); const a = await harness()
  expect(a.locks.unresolved).toBe(true); expect(a.locks.status?.key).toBe(old.key); expect(sessionStorage.getItem(legacy)).toBeNull()
  const conflict = { ...old, key: crypto.randomUUID() }; sessionStorage.setItem(legacy, JSON.stringify(conflict))
  const b = await harness(); expect(b.locks.unavailable).toBe(true); expect(await b.locks.begin('UNLOCK')).toBeNull(); expect(sessionStorage.getItem(legacy)).not.toBeNull()
})
it('fails closed for corrupt/missing pending metadata and unavailable browser locks', async () => {
  const a = await harness(), ticket = (await a.locks.begin('LOCK'))!; await a.locks.sent(ticket); await a.locks.release(ticket)
  localStorage.removeItem(Object.keys(localStorage).find(key => key.startsWith(prefix))!); window.dispatchEvent(new Event('storage'))
  expect(a.locks.unavailable).toBe(true); expect(a.locks.unresolved).toBe(true)
  localStorage.setItem(prefix + '0'.repeat(64), '{invalid'); const b = await harness()
  Object.defineProperty(navigator, 'locks', { configurable: true, value: undefined })
  expect(await b.locks.begin('LOCK')).toBeNull(); expect(b.locks.unavailable).toBe(true)
})
it('terminal storage failure retains UNKNOWN, while capacity never deletes existing lock state', async () => {
  const a = await harness(), ticket = (await a.locks.begin('LOCK'))!; await a.locks.sent(ticket)
  const spy = vi.spyOn(localStorage, 'setItem').mockImplementation(() => { throw new Error('Full') })
  try { expect(a.locks.settle(ticket, 'success')).toBe(false); expect(a.locks.unresolved).toBe(true) } finally { spy.mockRestore(); await a.locks.release(ticket) }
  localStorage.clear(); const b = await harness('new-session'), next = (await b.locks.begin('LOCK'))!
  for (let i = 0; i < 256; i++) { const scope = i.toString(16).padStart(64, '0'); localStorage.setItem(prefix + scope, JSON.stringify({ schema: 2, scope, key: crypto.randomUUID(), action: 'LOCK', state: 'UNKNOWN' })) }
  expect(await b.locks.sent(next)).toBe(false); await b.locks.release(next); expect(Object.keys(localStorage).filter(key => key.startsWith(prefix))).toHaveLength(256)
})
it('releases a lock granted after identity replacement and never settles an earlier session into a new one', async () => {
  const a = await harness(); let resume!: () => void; const wait = new Promise<void>(resolve => { resume = resolve })
  Object.defineProperty(navigator, 'locks', { configurable: true, value: { request: (name: string, options: { mode: LockMode; ifAvailable?: true }, callback: (lock: unknown | null) => Promise<void>) => manager.locks.request(name, options, async lock => { await wait; await callback(lock) }) } })
  const pending = a.locks.begin('LOCK'); await vi.waitFor(() => expect(manager.held.size).toBeGreaterThan(0))
  saveToken('new-session', false); a.auth.user = { ...actor, id: '2' }; resume(); expect(await pending).toBeNull(); expect(manager.held.size).toBe(0)
})
