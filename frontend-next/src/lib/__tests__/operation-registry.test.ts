import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { OperationRegistry, OperationBlocked, type OperationOwner, type OperationSend } from '../operation-registry'
import { ApiError } from '@/api/http'
import { localFailure } from '../operation-result'
import { operationOwner } from '../operation-owner'
import { uploadEvidence } from '@/features/file/files'

let owner: OperationOwner | null
const local = { operation: 'users.save', resource: 'users.123', protocol: 'LOCAL' as const }
const receipt = (state = 'SUCCEEDED') => ({ operationId: 'receipt-1', operation: 'users.save', state, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' })
const create = (storage: () => Storage = () => localStorage, capacity = 100) => new OperationRegistry(() => owner, storage, Date.now, () => crypto.randomUUID(), capacity)
beforeEach(() => { localStorage.clear(); owner = { scope: ['https://site.example/api', '10', 'admin', '20'], session: 'secret-token-one' } })
afterEach(() => { vi.useRealTimers() })

it('locks synchronously before confirmation, shares conflicting actions, and releases cancelled preparation', () => {
  const registry = create(), ticket = registry.reserve(local)
  expect(() => registry.reserve({ ...local, operation: 'users.delete' })).toThrow(OperationBlocked)
  const independent = registry.reserve({ ...local, resource: 'users.124' })
  expect(localStorage.length).toBe(0)
  registry.cancel(ticket); expect(() => registry.reserve(local)).not.toThrow(); registry.cancel(independent)
})
it('keeps only metadata across reload and blocks the same resource until an authorized receipt resolves it', async () => {
  const registry = create(), ticket = registry.reserve(local)
  await registry.execute(ticket, async () => { throw new ApiError(0, null, 'network') })
  const stored = localStorage.getItem(localStorage.key(0)!)!
  expect(stored).toContain(ticket.key); expect(stored).not.toContain('secret-token-one')
  expect(Object.keys(JSON.parse(stored)).sort()).toEqual(['createdAt', 'key', 'operation', 'owner', 'protocol', 'resource', 'state', 'updatedAt'])
  const reloaded = create()
  expect(reloaded.list()[0]).toMatchObject({ key: ticket.key, state: 'UNKNOWN' })
  expect(() => reloaded.reserve(local)).toThrow(OperationBlocked)
  await expect(reloaded.lookupLocal(ticket.key, async () => undefined)).rejects.toThrow(OperationBlocked)
  expect(() => reloaded.reserve(local)).toThrow(OperationBlocked)
  expect(await reloaded.lookupLocal(ticket.key, async () => receipt())).toBe('SUCCEEDED')
  expect(localStorage.length).toBe(0); expect(() => reloaded.reserve(local)).not.toThrow()
})
it('supports an explicit original-key LOCAL retry only with the same captured request and honors Retry-After', async () => {
  vi.useFakeTimers()
  const registry = create(), ticket = registry.reserve(local), keys: string[] = []
  let calls = 0
  const send = async (options: OperationSend) => {
    keys.push(options.idempotencyKey)
    if (++calls === 1) throw new ApiError(503, null, 'Unavailable', undefined, { status: 503, retryAfterSeconds: 2 })
    return { id: '123' }
  }
  expect((await registry.execute(ticket, send)).kind).toBe('unknown')
  await expect(registry.execute(ticket, send)).rejects.toMatchObject({ reason: 'retry' })
  await vi.advanceTimersByTimeAsync(2000)
  await expect(registry.execute(ticket, async () => ({ id: 'other-input' }))).rejects.toMatchObject({ reason: 'retry' })
  expect(await registry.execute(ticket, send)).toEqual({ kind: 'success', value: { id: '123' } })
  expect(keys).toEqual([ticket.key, ticket.key]); expect(registry.list()).toEqual([])
})
it('shows slow state without allowing duplicate dispatch or inventing completion', async () => {
  vi.useFakeTimers()
  const registry = create(), ticket = registry.reserve(local)
  let complete!: (value: number) => void
  const send = vi.fn(() => new Promise<number>(resolve => { complete = resolve }))
  const result = registry.execute(ticket, send)
  await expect(registry.execute(ticket, send)).rejects.toMatchObject({ reason: 'busy' })
  await vi.advanceTimersByTimeAsync(3000)
  expect(registry.list()[0]?.state).toBe('SLOW'); expect(send).toHaveBeenCalledOnce()
  complete(3); expect(await result).toEqual({ kind: 'success', value: 3 })
  expect(registry.list()).toEqual([])
})
it('rejects late results after a session switch, preserves old unresolved intent, and hides it from another owner', async () => {
  const registry = create(), ticket = registry.reserve(local), original = owner!
  let complete!: (value: number) => void
  const result = registry.execute(ticket, () => new Promise<number>(resolve => { complete = resolve }))
  owner = { ...original, session: 'new-session', scope: ['https://site.example/api', '11', 'admin', '21'] }
  complete(7); expect(await result).toEqual({ kind: 'stale' }); expect(registry.list()).toEqual([])
  await expect(registry.lookupLocal(ticket.key, async () => receipt())).rejects.toThrow(OperationBlocked)
  owner = { ...original, session: 'new-login-same-person' }
  expect(registry.list()[0]?.state).toBe('UNKNOWN')
  await expect(registry.execute(ticket, async () => 7)).rejects.toMatchObject({ reason: 'identity' })
  await registry.lookupLocal(ticket.key, async () => receipt())
})
it('does not clear an earlier ambiguous intent on a later permission denial or not-found response', async () => {
  const registry = create(), ticket = registry.reserve(local)
  let first = true
  const send = async () => { if (first) { first = false; throw new ApiError(0, null, 'timeout') }; throw new ApiError(403, 'forbidden', 'Forbidden') }
  await registry.execute(ticket, send)
  expect((await registry.execute(ticket, send)).kind).toBe('unknown')
  expect(registry.list()).toHaveLength(1)
  expect(localFailure(new ApiError(404, 'common.error.not-found', 'Not found'), true)).toBe('unknown')
})
it('distinguishes first definitive rejection, final rollback, and completed replay with no business DTO', async () => {
  const registry = create()
  expect((await registry.execute(registry.reserve(local), async () => { throw new ApiError(400, 'validation', 'Bad') })).kind).toBe('rejected')
  expect((await registry.execute(registry.reserve(local), async () => { throw new ApiError(409, 'common.request.failed', 'Failed', receipt('FAILED_FINAL')) })).kind).toBe('failed')
  expect(await registry.execute(registry.reserve(local), async () => { throw new ApiError(409, 'common.request.completed', 'Completed', receipt()) })).toEqual({ kind: 'completed' })
  expect(localFailure(new ApiError(409, 'common.request.completed', 'Malformed', { id: 'business-id' }))).toBe('unknown')
  expect(registry.list()).toEqual([])
})
it('does not infer native completion from a successful acceptance response or permit an unknown native retry', async () => {
  const registry = create(), ticket = registry.reserve({ ...local, protocol: 'UPLOAD' })
  const send = async () => ({ state: 'RUNNING' })
  await expect(registry.execute(ticket, send)).rejects.toMatchObject({ reason: 'invalid' })
  expect((await registry.execute(ticket, send, { success: () => 'pending' })).kind).toBe('pending')
  await expect(registry.execute(ticket, send, { success: () => 'pending' })).rejects.toMatchObject({ reason: 'retry' })
})
it('fails before sending when durable metadata cannot be written', async () => {
  const registry = create(() => ({ length: 0, key: () => null, setItem: () => { throw new Error('Quota') } }) as unknown as Storage)
  const send = vi.fn(), ticket = registry.reserve(local)
  await expect(registry.execute(ticket, send)).rejects.toMatchObject({ reason: 'storage' })
  expect(send).not.toHaveBeenCalled(); expect(() => registry.reserve({ ...local, resource: 'other' })).toThrow(OperationBlocked)
})
it('does not silently discard corrupt metadata or evict unresolved operations at capacity', async () => {
  localStorage.setItem('sma.operation.v1.corrupt', '{broken')
  expect(() => create().reserve(local)).toThrow(OperationBlocked)
  localStorage.clear()
  const registry = create(() => localStorage, 1), ticket = registry.reserve(local)
  await registry.execute(ticket, async () => { throw new ApiError(0, null, 'timeout') })
  expect(() => registry.reserve({ ...local, resource: 'other' })).toThrow(OperationBlocked)
  expect(registry.list()[0]?.key).toBe(ticket.key)
})
it('invalidates lookup ownership on session changes and never accepts a different known receipt ID', async () => {
  const registry = create(), ticket = registry.reserve(local)
  await registry.execute(ticket, async options => { options.onResponse({ status: 202, operationId: 'known-id' }); throw new ApiError(0, null, 'lost') })
  await expect(registry.lookupLocal(ticket.key, async () => receipt())).rejects.toThrow(OperationBlocked)
  let complete!: (value: unknown) => void
  const result = registry.lookupLocal(ticket.key, () => new Promise(resolve => { complete = resolve }))
  owner = { ...owner!, session: 'another-session' }; complete({ ...receipt(), operationId: 'known-id' })
  await expect(result).rejects.toMatchObject({ reason: 'identity' })
  expect(registry.list()).toHaveLength(1)
})
it('requires a validated matching tenant entry, ignores presentation refreshes, and invalidates permissions or lock changes', () => {
  const actor = { id: '20', portal: 'admin', permissions: ['b', 'a'] }, entry = { tenantId: '10', portal: 'admin' }
  const first = operationOwner(actor, entry, 'token', 'https://site/api')
  expect(operationOwner({ ...actor, permissions: ['a', 'b'] }, entry, 'token', 'https://site/api')).toEqual(first)
  expect(operationOwner({ ...actor, permissions: ['a'] }, entry, 'token', 'https://site/api')).not.toEqual(first)
  expect(operationOwner(actor, null, 'token', 'https://site/api')).toBeNull()
  expect(operationOwner(actor, { ...entry, portal: 'other' }, 'token', 'https://site/api')).toBeNull()
  expect(operationOwner({ ...actor, locked: true }, entry, 'token', 'https://site/api')).toBeNull()
})
it('retains the recovery record if storage fails after the server already completed', async () => {
  const storage: Storage = { get length() { return localStorage.length }, key: index => localStorage.key(index),
    getItem: key => localStorage.getItem(key), setItem: (key, value) => localStorage.setItem(key, value),
    removeItem: () => { throw new Error('Storage unavailable') }, clear: () => localStorage.clear() }
  const registry = create(() => storage), ticket = registry.reserve(local)
  await expect(registry.execute(ticket, async () => 'committed')).rejects.toMatchObject({ reason: 'storage' })
  expect(() => registry.reserve(local)).toThrow(OperationBlocked)
  expect(create().list()[0]).toMatchObject({ key: ticket.key, state: 'UNKNOWN' })
})
it('invalidates late work even when the same actor and token return after a lock transition', async () => {
  const registry = create(), ticket = registry.reserve(local)
  let complete!: (value: number) => void
  const pending = registry.execute(ticket, () => new Promise<number>(resolve => { complete = resolve }))
  registry.invalidate(); complete(1)
  expect(await pending).toEqual({ kind: 'stale' })
  expect(registry.list()[0]?.state).toBe('UNKNOWN')
  expect(() => registry.reserve(local)).toThrow(OperationBlocked)
})
it('uses native upload evidence and never treats ACCEPTED or RUNNING as completion', async () => {
  const registry = create(), ticket = registry.reserve({ ...local, protocol: 'UPLOAD' })
  await registry.execute(ticket, async () => { throw new ApiError(0, null, 'Lost') }, { success: () => 'completed' })
  const row = { uploadId: '11111111-1111-4111-8111-111111111111', state: 'ACCEPTED', resumable: true, fileId: null as string | null }
  expect((await registry.lookupNative(ticket.key, async () => row, uploadEvidence)).state).toBe('PENDING')
  expect(registry.list()).toHaveLength(1)
  await expect(registry.lookupLocal(ticket.key, async () => receipt())).rejects.toThrow(OperationBlocked)
  row.state = 'SUCCEEDED'; row.fileId = '9007199254740993'; row.resumable = false
  expect((await registry.lookupNative(ticket.key, async () => row, uploadEvidence)).state).toBe('SUCCEEDED')
  expect(registry.list()).toEqual([])
})
it('reserves a fixed batch atomically against single rows and overlapping batches while leaving disjoint rows independent', async () => {
  const registry = create(), resources = ['tasks.2', 'tasks.3']
  const ticket = registry.reserve({ ...local, resource: 'tasks.1', resources })
  resources[0] = 'tasks.99'
  expect(() => registry.reserve({ ...local, resource: 'tasks.2' })).toThrow(OperationBlocked)
  expect(() => registry.reserve({ ...local, resource: 'tasks.4', resources: ['tasks.3'] })).toThrow(OperationBlocked)
  const other = registry.reserve({ ...local, resource: 'tasks.4' }); registry.cancel(other)
  await registry.execute(ticket, async () => { throw new ApiError(0, null, 'Lost') })
  const restored = create(), record = restored.list()[0]!
  ;(record.resources as string[])[0] = 'tasks.99'
  expect(() => restored.reserve({ ...local, resource: 'tasks.2' })).toThrow(OperationBlocked)
  await restored.lookupLocal(ticket.key, async () => receipt())
  expect(() => restored.reserve({ ...local, resource: 'tasks.2' })).not.toThrow()
})
it('collection actions conflict with their rows in both directions without blocking similarly named namespaces', () => {
  const registry = create(), all = registry.reserve({ ...local, resource: 'tasks.*' })
  expect(() => registry.reserve({ ...local, resource: 'tasks.1' })).toThrow(OperationBlocked)
  const unrelated = registry.reserve({ ...local, resource: 'tasks2.1' }); registry.cancel(unrelated); registry.cancel(all)
  const row = registry.reserve({ ...local, resource: 'tasks.1' })
  expect(() => registry.reserve({ ...local, resource: 'tasks.*' })).toThrow(OperationBlocked)
  registry.cancel(row)
})

it('does not confuse a nested LOCAL admission receipt with the native task identity', async () => {
  const registry = create(), ticket = registry.reserve({ operation: 'cache.submit', resource: 'cache.task1', protocol: 'NATIVE' })
  await registry.execute(ticket, async options => {
    options.onResponse({ status: 0, operationId: 'task1' })
    throw new ApiError(409, 'common.request.pending', 'Pending admission', receipt('RUNNING'), { status: 409, operationId: 'receipt-1' })
  }, { success: () => 'pending' })
  expect(registry.list()[0]).toMatchObject({ operationId: 'task1', state: 'UNKNOWN' })
  expect((await registry.lookupNative(ticket.key, async () => ({ id: 'task1', state: 'RUNNING' }), value => ({ id: value.id, state: 'PENDING' }))).state).toBe('PENDING')
  expect(registry.list()).toHaveLength(1)
})

it('persists an existing native command key before dispatch so a reload never needs its OTP or body', async () => {
  const registry = create(), nativeId = 'cmd-11111111-1111-4111-8111-111111111111'
  const ticket = registry.reserve({ operation: 'extension.command.UPLOAD', resource: 'extension.console.production.echo', protocol: 'NATIVE', nativeId })
  await registry.execute(ticket, async () => {
    const stored = JSON.parse(localStorage.getItem('sma.operation.v1.' + ticket.key)!)
    expect(stored.operationId).toBe(nativeId); expect(stored.nativeId).toBeUndefined()
    throw new ApiError(0, null, 'Response lost')
  }, { success: () => 'completed' })
  const restored = create(); expect(restored.list()[0]!.operationId).toBe(nativeId)
  await restored.lookupNative(ticket.key, async record => { expect(record.operationId).toBe(nativeId); return { key: nativeId } }, value => ({ id: value.key, state: 'SUCCEEDED' }))
  expect(restored.list()).toEqual([])
})
