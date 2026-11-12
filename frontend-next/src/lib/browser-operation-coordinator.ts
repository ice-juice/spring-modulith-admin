export interface CoordinationTarget {
  /** Canonical site/API, tenant, portal and user scope only. Never pass a session token. */
  owner: string
  resource: string
  resources?: readonly string[]
}
export interface OperationLease { release(): Promise<void> }
export interface OperationCoordinator {
  acquire(target: CoordinationTarget): Promise<OperationLease>
  /** Only a synchronous metadata critical section may queue. Never confirm or send inside it. */
  transaction(owner: string, work: () => void): Promise<void>
}
export class CoordinationError extends Error {
  constructor(public reason: 'busy' | 'coordination' | 'capacity') { super('operation.' + reason) }
}
export interface BrowserLocks {
  request(name: string, options: { mode: LockMode; ifAvailable?: true }, callback: (lock: unknown | null) => Promise<void>): Promise<unknown>
}
const identifier = /^[A-Za-z0-9_.:-]{1,160}$/

/** Exact rows take a leaf lock; collection wildcards take the namespace lock exclusively.
 * Ancestor namespaces are shared, so disjoint rows remain independent across tabs. */
function lockSet(target: CoordinationTarget) {
  const resources = [target.resource, ...(target.resources || [])]
  if (!target.owner || target.owner.length > 8192 || resources.length > 100) throw new CoordinationError('capacity')
  const entries = new Map<string, LockMode>()
  const add = (key: string, mode: LockMode) => { if (entries.get(key) !== 'exclusive') entries.set(key, mode) }
  for (const resource of resources) {
    const collection = resource.endsWith('.*'), base = collection ? resource.slice(0, -2) : resource
    if (resource.length > 160 || !identifier.test(base)) throw new CoordinationError('capacity')
    for (let index = base.indexOf('.'); index !== -1; index = base.indexOf('.', index + 1)) add('namespace:' + base.slice(0, index), 'shared')
    add((collection ? 'namespace:' : 'resource:') + base, 'exclusive')
  }
  if (entries.size > 256) throw new CoordinationError('capacity')
  const prefix = 'sma.operation-lock.v1:' + encodeURIComponent(target.owner) + ':'
  return [...entries].sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0).map(([name, mode]) => ({ name: prefix + name, mode }))
}

/** No timeout takeover and no queued execution. Closing a tab releases only its browser locks;
 * callers must retain durable UNKNOWN metadata until authoritative result reconciliation. */
export class BrowserOperationCoordinator implements OperationCoordinator {
  constructor(private provider: () => BrowserLocks | undefined = () => navigator.locks) {}
  async transaction(owner: string, work: () => void): Promise<void> {
    if (!owner || owner.length > 8192) throw new CoordinationError('capacity')
    let locks: BrowserLocks | undefined
    try { locks = this.provider() } catch { throw new CoordinationError('coordination') }
    if (!locks || typeof locks.request !== 'function') throw new CoordinationError('coordination')
    let entered = false
    try {
      await locks.request('sma.operation-ledger.v1:' + encodeURIComponent(owner), { mode: 'exclusive' }, async lock => {
        if (!lock) throw new CoordinationError('coordination')
        entered = true; work()
      })
    } catch (cause) {
      // Preserve a caller's storage/identity failure, but do not leak browser-specific capability errors.
      if (entered) throw cause
      throw cause instanceof CoordinationError ? cause : new CoordinationError('coordination')
    }
  }
  async acquire(target: CoordinationTarget): Promise<OperationLease> {
    let locks: BrowserLocks | undefined
    try { locks = this.provider() } catch { throw new CoordinationError('coordination') }
    if (!locks || typeof locks.request !== 'function') throw new CoordinationError('coordination')
    const names = lockSet(target), provider = locks
    let announce!: (lease: OperationLease) => void, reject!: (cause: unknown) => void, resume!: () => void
    const acquired = new Promise<OperationLease>((yes, no) => { announce = yes; reject = no })
    const held = new Promise<void>(yes => { resume = yes })
    async function next(index: number): Promise<void> {
      if (index === names.length) {
        announce({ release: () => { resume(); return finished } })
        await held
        return
      }
      const entry = names[index]!
      await provider.request(entry.name, { mode: entry.mode, ifAvailable: true }, async lock => {
        if (!lock) throw new CoordinationError('busy')
        await next(index + 1)
      })
    }
    const finished = next(0).catch(cause => { reject(cause instanceof CoordinationError ? cause : new CoordinationError('coordination')) })
    return acquired
  }
}
