import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { webcrypto } from 'node:crypto'
import { createPinia, disposePinia } from 'pinia'
import { useAuth, type CurrentUser } from '../auth'
import { useCredentialIntents } from '../shared-credential-intents'
import { saveToken } from '@/lib/token'
import { browserLocksFixture, credentialMarker } from '@/lib/__tests__/browser-locks.fixture'
import { BrowserOperationCoordinator } from '@/lib/browser-operation-coordinator'
const actor: CurrentUser = { id: '1', username: 'fixture', name: 'Fixture', portal: 'admin', permissions: [], superAdmin: false, menus: [], navigation: [] }
const pins: ReturnType<typeof createPinia>[] = []
let manager: ReturnType<typeof browserLocksFixture>
beforeEach(() => { vi.stubGlobal('crypto', webcrypto); localStorage.clear(); sessionStorage.clear(); manager = browserLocksFixture(); Object.defineProperty(navigator, 'locks', { configurable: true, value: manager.locks }) })
afterEach(() => { for (const pin of pins.splice(0)) disposePinia(pin); vi.restoreAllMocks(); vi.unstubAllGlobals() })
async function harness(token = 'private-credential-token', wait = true) {
  const pin = createPinia(); pins.push(pin); const auth = useAuth(pin); saveToken(token, false); auth.user = { ...actor }
  const intents = useCredentialIntents(pin)
  if (wait) await vi.waitFor(() => expect(intents.ready || intents.unavailable).toBe(true))
  return { auth, intents, pin }
}
const prefix = 'sma.credential-intent.v2.'
const legacy = 'sma.credential-intent.v1'
async function oldMarker() {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(JSON.stringify([window.location.origin, 'admin', '1', 'private-credential-token'])))
  return { schema: 1, scope: Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join(''), key: crypto.randomUUID(), state: 'UNKNOWN' }
}
it('coordinates before submission across independent stores, and retains shared unknown after the sender is disposed', async () => {
  const first = await harness(), peer = await harness(), ticket = (await first.intents.begin())!
  expect(ticket).not.toBeNull(); expect(await peer.intents.begin()).toBeNull(); expect(peer.intents.problem).toBeTruthy()
  expect(await first.intents.sent(ticket)).toBe(true); await first.intents.release(ticket); disposePinia(first.pin)
  window.dispatchEvent(new Event('storage')); expect(peer.intents.unresolved).toBe(true)
  expect(await peer.intents.begin()).toBeNull(); expect(peer.intents.pendingKey()).toBe(ticket.key)
  expect(Object.keys(JSON.parse(credentialMarker()!)).sort()).toEqual(['key', 'schema', 'scope', 'state'])
  expect(credentialMarker()).not.toContain('private-credential-token'); expect(manager.held.size).toBe(0)
})
it('holds the same lock for original-key lookup, only clears authoritative completion and never repeats the write', async () => {
  const first = await harness(), peer = await harness(), ticket = (await first.intents.begin())!
  await first.intents.sent(ticket)
  const lookup = vi.fn(async () => ({ state: 'SUCCEEDED' }))
  expect(await peer.intents.check(lookup, () => true)).toBeUndefined(); expect(lookup).not.toHaveBeenCalled()
  await first.intents.release(ticket)
  await peer.intents.check(async key => { expect(key).toBe(ticket.key); return 'NOT_FOUND' }, state => state === 'SUCCEEDED')
  expect(peer.intents.unresolved).toBe(true)
  await peer.intents.check(async key => { expect(key).toBe(ticket.key); return 'FAILED_FINAL' }, state => state === 'FAILED_FINAL')
  window.dispatchEvent(new Event('storage')); expect(first.intents.unresolved).toBe(false); expect(credentialMarker()).toBeNull()
  expect(manager.held.size).toBe(0)
})
it('migrates matching legacy metadata before allowing new work, preserving the original key and scope', async () => {
  const old = await oldMarker(); sessionStorage.setItem(legacy, JSON.stringify(old))
  const { intents } = await harness()
  expect(intents.unresolved).toBe(true); expect(intents.pendingKey()).toBe(old.key)
  expect(JSON.parse(localStorage.getItem(prefix + old.scope)!)).toEqual(old); expect(sessionStorage.getItem(legacy)).toBeNull()
  expect(await intents.begin()).toBeNull()
})

it('keeps legacy recovery available after migration encounters a busy peer', async () => {
  const old = await oldMarker(); sessionStorage.setItem(legacy, JSON.stringify(old))
  const lease = await new BrowserOperationCoordinator().acquire({ owner: 'credential-session:' + old.scope, resource: 'credentials' })
  const { intents } = await harness(); expect(intents.ready).toBe(true); expect(intents.unresolved).toBe(true)
  await lease.release()
  const result = await intents.check(async key => { expect(key).toBe(old.key); expect(sessionStorage.getItem(legacy)).toBeNull(); return 'SUCCEEDED' }, state => state === 'SUCCEEDED')
  expect(result).toBe('SUCCEEDED'); expect(intents.unresolved).toBe(false); expect(manager.held.size).toBe(0)
})
it('never overwrites conflicting old and shared markers and fails closed for corrupt metadata', async () => {
  const old = await oldMarker(), other = { ...old, key: crypto.randomUUID() }
  sessionStorage.setItem(legacy, JSON.stringify(old)); localStorage.setItem(prefix + old.scope, JSON.stringify(other))
  const first = await harness(); expect(first.intents.unavailable).toBe(true); expect(await first.intents.begin()).toBeNull()
  expect(JSON.parse(sessionStorage.getItem(legacy)!)).toEqual(old); expect(JSON.parse(localStorage.getItem(prefix + old.scope)!)).toEqual(other)
  disposePinia(first.pin); localStorage.clear(); sessionStorage.setItem(legacy, '{invalid')
  const second = await harness(); expect(second.intents.unavailable).toBe(true); expect(await second.intents.begin()).toBeNull(); expect(sessionStorage.getItem(legacy)).toBe('{invalid')
})
it('refuses unavailable Web Locks and storage persistence without creating a dispatchable ticket', async () => {
  const first = await harness(); Object.defineProperty(navigator, 'locks', { configurable: true, value: undefined })
  expect(await first.intents.begin()).toBeNull(); expect(first.intents.unavailable).toBe(true); expect(first.intents.problem).toBeTruthy()
  Object.defineProperty(navigator, 'locks', { configurable: true, value: manager.locks })
  const second = await harness(), ticket = (await second.intents.begin())!
  const spy = vi.spyOn(localStorage, 'setItem').mockImplementation(() => { throw new Error('Full') })
  try {
    expect(await second.intents.sent(ticket)).toBe(false); await second.intents.release(ticket)
    expect(second.intents.unavailable).toBe(true); expect(manager.held.size).toBe(0)
  } finally { spy.mockRestore() }
})
it('caps stored unknown sessions without deleting earlier unresolved records', async () => {
  const { intents } = await harness(), ticket = (await intents.begin())!
  for (let i = 0; i < 256; i++) { const scope = i.toString(16).padStart(64, '0'); localStorage.setItem(prefix + scope, JSON.stringify({ schema: 1, scope, key: crypto.randomUUID(), state: 'UNKNOWN' })) }
  expect(await intents.sent(ticket)).toBe(false); await intents.release(ticket)
  expect(Object.keys(localStorage).filter(key => key.startsWith(prefix))).toHaveLength(256); expect(intents.unavailable).toBe(true)
})
it('new authenticated sessions have independent scope without pretending the previous unknown completed', async () => {
  const first = await harness(), ticket = (await first.intents.begin())!; await first.intents.sent(ticket); await first.intents.release(ticket)
  const original = credentialMarker(), next = await harness('different-authenticated-session'), nextTicket = (await next.intents.begin())!
  expect(next.intents.unresolved).toBe(false); expect(nextTicket).not.toBeNull(); await next.intents.release(nextTicket)
  expect(credentialMarker()).toBe(original)
})
it('locking invalidates prepared tickets without forgetting already sent unknowns', async () => {
  const { auth, intents } = await harness(), prepared = (await intents.begin())!
  auth.user!.locked = true; auth.user!.locked = false
  expect(prepared.current()).toBe(false); expect(await intents.sent(prepared)).toBe(false); await intents.release(prepared)
  const sent = (await intents.begin())!; await intents.sent(sent); auth.user!.locked = true
  expect(intents.unresolved).toBe(true); intents.settle(sent, true); expect(intents.unresolved).toBe(true)
  await intents.release(sent); expect(manager.held.size).toBe(0)
})
it('failed terminal cleanup retains the shared unknown and blocks future submissions', async () => {
  const { intents } = await harness(), ticket = (await intents.begin())!; await intents.sent(ticket)
  const spy = vi.spyOn(localStorage, 'removeItem').mockImplementation(() => { throw new Error('Denied') })
  try {
    intents.settle(ticket, true); await intents.release(ticket)
    expect(intents.unavailable).toBe(true); expect(credentialMarker()).not.toBeNull(); expect(await intents.begin()).toBeNull(); expect(manager.held.size).toBe(0)
  } finally { spy.mockRestore() }
})

it('releases a browser lock granted after its pending ticket was invalidated or its store disposed', async () => {
  for (const mode of ['lock', 'dispose']) {
    const { intents, auth, pin } = await harness()
    let resume!: () => void
    const wait = new Promise<void>(resolve => { resume = resolve })
    Object.defineProperty(navigator, 'locks', { configurable: true, value: {
      request: (name: string, options: { mode: LockMode; ifAvailable?: true }, callback: (lock: unknown | null) => Promise<void>) => manager.locks.request(name, options, async lock => { await wait; await callback(lock) }),
    } })
    const pending = intents.begin()
    await vi.waitFor(() => expect(manager.held.size).toBeGreaterThan(0))
    if (mode === 'lock') auth.user!.locked = true
    else disposePinia(pin)
    resume(); expect(await pending).toBeNull(); expect(manager.held.size).toBe(0)
    Object.defineProperty(navigator, 'locks', { configurable: true, value: manager.locks })
  }
})
