import { expect, it } from 'vitest'
import { BrowserOperationCoordinator, type BrowserLocks } from '../browser-operation-coordinator'

/** Implements shared/exclusive grant semantics, including release when a callback rejects. */
function manager() {
  const held = new Map<string, LockMode[]>()
  const locks: BrowserLocks = { async request(name, options, callback) {
    const modes = held.get(name) || []
    if (modes.some(mode => mode === 'exclusive' || options.mode === 'exclusive')) return callback(null)
    modes.push(options.mode); held.set(name, modes)
    try { return await callback({ name }) }
    finally { modes.splice(modes.indexOf(options.mode), 1); if (!modes.length) held.delete(name) }
  } }
  return { locks, held }
}
const owner = '["https://site/api","10","admin","20"]'
it('excludes the same row across clients while allowing disjoint rows and isolated owners', async () => {
  const { locks, held } = manager(), a = new BrowserOperationCoordinator(() => locks), b = new BrowserOperationCoordinator(() => locks)
  const first = await a.acquire({ owner, resource: 'tasks.1' })
  await expect(b.acquire({ owner, resource: 'tasks.1' })).rejects.toMatchObject({ reason: 'busy' })
  const other = await b.acquire({ owner, resource: 'tasks.2' }), tenant = await b.acquire({ owner: 'different-tenant', resource: 'tasks.1' })
  await Promise.all([first.release(), other.release(), tenant.release()]); expect(held.size).toBe(0)
  const again = await b.acquire({ owner, resource: 'tasks.1' }); await again.release()
})
it('makes overlapping batches and wildcard collections conflict in both directions', async () => {
  const { locks, held } = manager(), client = new BrowserOperationCoordinator(() => locks)
  const batch = await client.acquire({ owner, resource: 'tasks.1', resources: ['tasks.2', 'tasks.3'] })
  await expect(client.acquire({ owner, resource: 'tasks.4', resources: ['tasks.2'] })).rejects.toMatchObject({ reason: 'busy' })
  await expect(client.acquire({ owner, resource: 'tasks.*' })).rejects.toMatchObject({ reason: 'busy' })
  // Partially acquired locks from a rejected batch must not leak.
  const fourth = await client.acquire({ owner, resource: 'tasks.4' }); await fourth.release(); await batch.release()
  const all = await client.acquire({ owner, resource: 'tasks.*' })
  await expect(client.acquire({ owner, resource: 'tasks.deep.row' })).rejects.toMatchObject({ reason: 'busy' })
  const similar = await client.acquire({ owner, resource: 'tasks2.1' }), parentExact = await client.acquire({ owner, resource: 'tasks' })
  await Promise.all([all.release(), similar.release(), parentExact.release()]); expect(held.size).toBe(0)
})
it('normalizes a mixed collection and row batch without conflicting with its own shared ancestors', async () => {
  const { locks, held } = manager(), client = new BrowserOperationCoordinator(() => locks)
  const lease = await client.acquire({ owner, resource: 'tasks.*', resources: ['tasks.2', 'tasks.deep.*', 'tasks.deep.3', 'tasks.2'] })
  await expect(client.acquire({ owner, resource: 'tasks.deep.4' })).rejects.toMatchObject({ reason: 'busy' })
  await lease.release(); await lease.release(); expect(held.size).toBe(0)
})
it('does not queue a rejected intent for automatic execution after the current holder leaves', async () => {
  const { locks, held } = manager(), client = new BrowserOperationCoordinator(() => locks)
  const first = await client.acquire({ owner, resource: 'objects.1' })
  await expect(client.acquire({ owner, resource: 'objects.1' })).rejects.toMatchObject({ reason: 'busy' })
  await first.release(); await Promise.resolve(); expect(held.size).toBe(0)
})
it('fails closed for missing or rejected browser capability and bounds hostile resource sets', async () => {
  const target = { owner, resource: 'objects.1' }
  await expect(new BrowserOperationCoordinator(() => undefined).acquire(target)).rejects.toMatchObject({ reason: 'coordination' })
  await expect(new BrowserOperationCoordinator(() => { throw new Error('Denied') }).acquire(target)).rejects.toMatchObject({ reason: 'coordination' })
  const rejecting: BrowserLocks = { request: async () => { throw new Error('SecurityError') } }
  await expect(new BrowserOperationCoordinator(() => rejecting).acquire(target)).rejects.toMatchObject({ reason: 'coordination' })
  const { locks, held } = manager(), client = new BrowserOperationCoordinator(() => locks)
  await expect(client.acquire({ owner, resource: 'root.*', resources: Array.from({ length: 100 }, (_, i) => 'objects.' + i) })).rejects.toMatchObject({ reason: 'capacity' })
  await expect(client.acquire({ owner, resource: 'invalid/*' })).rejects.toMatchObject({ reason: 'capacity' })
  expect(held.size).toBe(0)
})
