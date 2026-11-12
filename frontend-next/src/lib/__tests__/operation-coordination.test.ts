import { beforeEach, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import { ApiError } from '@/api/http'
import { useSubmission } from '@/composables/useSubmission'
import { BrowserOperationCoordinator, type BrowserLocks } from '../browser-operation-coordinator'
import { OperationRegistry, type OperationOwner } from '../operation-registry'

function manager() {
  const held = new Map<string, LockMode[]>(), waiting = new Map<string, (() => void)[]>()
  const locks: BrowserLocks = { async request(name, options, callback) {
    const conflict = () => (held.get(name) || []).some(mode => mode === 'exclusive' || options.mode === 'exclusive')
    while (conflict()) {
      if (options.ifAvailable) return callback(null)
      await new Promise<void>(resolve => { const queue = waiting.get(name) || []; queue.push(resolve); waiting.set(name, queue) })
    }
    const modes = held.get(name) || []; modes.push(options.mode); held.set(name, modes)
    try { return await callback({ name }) }
    finally {
      modes.splice(modes.indexOf(options.mode), 1); if (!modes.length) held.delete(name)
      const queue = waiting.get(name) || []; waiting.delete(name); queue.forEach(wake => wake())
    }
  } }
  return { locks, held }
}
let owner: OperationOwner | null
beforeEach(() => { localStorage.clear(); owner = { scope: ['https://site/api', 'tenant-1', 'admin', 'user-1'], session: 'private-token-in-memory' } })
const spec = (resource = 'users.1') => ({ operation: 'users.edit', resource, protocol: 'LOCAL' as const })
const receipt = (state = 'SUCCEEDED') => ({ operationId: 'receipt-1', operation: 'users.edit', state, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' })
function registry(locks: BrowserLocks | undefined, capacity = 100, storage = () => localStorage) { return new OperationRegistry(() => owner, storage, Date.now, () => crypto.randomUUID(), capacity, new BrowserOperationCoordinator(() => locks)) }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(yes => { resolve = yes }); return { promise, resolve } }

it('coordinates stale registries before confirmation and retains original unknown metadata across completion by a peer', async () => {
  const { locks, held } = manager(), a = registry(locks), b = registry(locks)
  a.list(); b.list()
  const first = a.reserve(spec()), duplicate = b.reserve(spec())
  await a.admit(first); await expect(b.admit(duplicate)).rejects.toMatchObject({ reason: 'busy' }); await b.cancel(duplicate)
  const response = deferred<void>(), send = vi.fn(() => response.promise)
  const pending = a.execute(first, send); await vi.waitFor(() => expect(send).toHaveBeenCalledOnce())
  const lookup = vi.fn(async () => receipt())
  await expect(b.lookupLocal(first.key, lookup)).rejects.toMatchObject({ reason: 'busy' }); expect(lookup).not.toHaveBeenCalled()
  response.resolve(); expect((await pending).kind).toBe('success')
  b.synchronize(); expect(b.list()).toEqual([])
  const lost = a.reserve(spec()); await a.execute(lost, async () => { throw new ApiError(0, null, 'Lost') })
  expect(() => b.reserve(spec())).toThrow()
  expect(b.list()[0]!.key).toBe(lost.key)
  expect(localStorage.getItem('sma.operation.v1.' + lost.key)).not.toContain('private-token-in-memory')
  await expect(b.lookupLocal(lost.key, async () => { throw new ApiError(404, null, 'Not found') })).rejects.toThrow()
  expect(b.list()).toHaveLength(1)
  await b.lookupLocal(lost.key, lookup); a.synchronize(); expect(a.list()).toEqual([]); expect(held.size).toBe(0)
  await expect(a.execute(lost, send)).rejects.toMatchObject({ reason: 'identity' })
})

it('atomically enforces the final durable slot across disjoint targets without holding the ledger over the network', async () => {
  const { locks, held } = manager(), a = registry(locks, 1), b = registry(locks, 1)
  const one = a.reserve(spec('items.1')), two = b.reserve(spec('items.2'))
  const send = vi.fn(async () => { throw new ApiError(0, null, 'Unknown') })
  const results = await Promise.allSettled([a.execute(one, send), b.execute(two, send)])
  expect(send).toHaveBeenCalledOnce()
  expect(results.filter(result => result.status === 'rejected')).toHaveLength(1)
  expect(results.find(result => result.status === 'rejected')).toMatchObject({ reason: { reason: 'capacity' } })
  await a.cancel(one); await b.cancel(two); a.synchronize(); b.synchronize()
  expect(a.list()).toHaveLength(1); expect(b.list()).toHaveLength(1); expect(held.size).toBe(0)
})

it('permits simultaneous independent writes but rejects duplicate execute while admission is still asynchronous', async () => {
  const { locks, held } = manager(), a = registry(locks), b = registry(locks)
  const one = a.reserve(spec('items.1')), two = b.reserve(spec('items.2')), response = deferred<void>()
  const send = vi.fn(() => response.promise), first = a.execute(one, send), second = b.execute(two, send)
  await expect(a.execute(one, send)).rejects.toMatchObject({ reason: 'busy' })
  await vi.waitFor(() => expect(send).toHaveBeenCalledTimes(2))
  expect([...held.keys()].some(name => name.startsWith('sma.operation-ledger'))).toBe(false)
  response.resolve(); await Promise.all([first, second]); expect(held.size).toBe(0)
})

it('does not confirm or dispatch when browser coordination is unavailable, and cancellation releases its reservation', async () => {
  const broken = registry(undefined), scope = effectScope(), action = scope.run(() => useSubmission(broken))!
  const prepare = vi.fn(async () => vi.fn(async () => true))
  expect((await action.run(spec(), prepare)).kind).toBe('blocked'); expect(prepare).not.toHaveBeenCalled(); expect(broken.list()).toEqual([]); scope.stop()
  const { locks, held } = manager(), valid = registry(locks), other = registry(locks), secondScope = effectScope()
  const submission = secondScope.run(() => useSubmission(valid))!
  expect((await submission.run(spec(), () => undefined)).kind).toBe('cancelled')
  const next = other.reserve(spec()); await other.admit(next); await other.cancel(next); expect(held.size).toBe(0); secondScope.stop()
})

it('storage failure releases browser locks without dispatch or erasing another original unknown record', async () => {
  const { locks, held } = manager(), good = registry(locks)
  const first = good.reserve(spec('items.1')); await good.execute(first, async () => { throw new ApiError(0, null, 'Lost') })
  const brokenStorage: Storage = { get length() { return localStorage.length }, key: index => localStorage.key(index), getItem: key => localStorage.getItem(key), setItem: () => { throw new Error('Full') }, removeItem: key => localStorage.removeItem(key), clear: () => localStorage.clear() }
  const broken = registry(locks, 100, () => brokenStorage), next = broken.reserve(spec('items.2')), send = vi.fn(async () => true)
  await expect(broken.execute(next, send)).rejects.toMatchObject({ reason: 'storage' }); await broken.cancel(next)
  expect(send).not.toHaveBeenCalled(); expect(localStorage.getItem('sma.operation.v1.' + first.key)).not.toBeNull(); expect(held.size).toBe(0)
})

it('serializes explicit native continuation and result lookup across restored registries using the original key', async () => {
  const { locks, held } = manager(), a = registry(locks), b = registry(locks)
  const first = a.reserve({ ...spec(), protocol: 'NATIVE', nativeId: 'native-task' })
  await a.execute(first, async () => { throw new ApiError(0, null, 'Lost') }, { success: () => 'completed' })
  b.synchronize(); const response = deferred<void>(), send = vi.fn(async (record, options) => { expect(record.key).toBe(first.key); expect(options.idempotencyKey).toBe(first.key); await response.promise; return true })
  const pending = a.continueNative(first.key, send, { success: () => 'completed' })
  await vi.waitFor(() => expect(send).toHaveBeenCalledOnce())
  await expect(b.continueNative(first.key, send, { success: () => 'completed' })).rejects.toMatchObject({ reason: 'busy' })
  await expect(b.lookupNative(first.key, async () => true, () => ({ id: 'native-task', state: 'SUCCEEDED' }))).rejects.toMatchObject({ reason: 'busy' })
  response.resolve(); await pending; b.synchronize(); expect(b.list()).toEqual([]); expect(held.size).toBe(0)
})

it('invalidates a pending admission and preserves already dispatched unknowns when the session changes', async () => {
  const { locks, held } = manager(), a = registry(locks)
  const first = a.reserve(spec()), admitting = a.admit(first)
  owner = { ...owner!, session: 'replacement-session' }; a.invalidate()
  await expect(admitting).rejects.toMatchObject({ reason: 'identity' }); expect(held.size).toBe(0)
  const next = a.reserve(spec()), response = deferred<void>(), send = vi.fn(() => response.promise), pending = a.execute(next, send)
  await vi.waitFor(() => expect(send).toHaveBeenCalledOnce())
  owner = { ...owner!, session: 'another-session' }; a.invalidate(); response.resolve()
  expect((await pending).kind).toBe('stale'); expect(a.list()[0]!.state).toBe('UNKNOWN'); expect(held.size).toBe(0)
})

it('does not dispatch if the identity changes after the durable admission transaction has committed', async () => {
  const { locks, held } = manager(), coordinator = new BrowserOperationCoordinator(() => locks)
  const a = new OperationRegistry(() => owner, () => localStorage, Date.now, () => crypto.randomUUID(), 100, {
    acquire: target => coordinator.acquire(target),
    transaction: async (scope, work) => { await coordinator.transaction(scope, work); owner = { ...owner!, session: 'changed-before-dispatch' }; a.invalidate() },
  })
  const ticket = a.reserve(spec()), send = vi.fn(async () => true)
  await expect(a.execute(ticket, send)).rejects.toMatchObject({ reason: 'identity' })
  expect(send).not.toHaveBeenCalled(); expect(a.list()[0]!.state).toBe('UNKNOWN')
  expect(localStorage.getItem('sma.operation.v1.' + ticket.key)).not.toBeNull(); expect(held.size).toBe(0)
})

it('does not erase a durable unknown when terminal receipt cleanup fails', async () => {
  const { locks, held } = manager()
  const storage: Storage = { get length() { return localStorage.length }, key: index => localStorage.key(index), getItem: key => localStorage.getItem(key), setItem: (key, value) => localStorage.setItem(key, value), removeItem: () => { throw new Error('Denied') }, clear: () => localStorage.clear() }
  const a = registry(locks, 100, () => storage), ticket = a.reserve(spec())
  await a.execute(ticket, async () => { throw new ApiError(0, null, 'Lost') })
  await expect(a.lookupLocal(ticket.key, async () => receipt())).rejects.toMatchObject({ reason: 'storage' })
  expect(localStorage.getItem('sma.operation.v1.' + ticket.key)).not.toBeNull(); expect(held.size).toBe(0)
  expect(() => a.reserve(spec('another.row'))).toThrow()
})
