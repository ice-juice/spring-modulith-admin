import { ApiError, type ResponseMeta } from '@/api/http'
import { localFailure, operationReceipt, type LocalOutcome, type OperationReceipt } from './operation-result'

export interface OperationOwner {
  /** Validated API/site, tenant, portal, user. The session discriminator is memory-only. */
  scope: readonly [string, string, string, string]
  session: string
}
export interface OperationSpec { operation: string; resource: string; resources?: readonly string[]; protocol: 'LOCAL' | 'UPLOAD' | 'NATIVE'; nativeId?: string }
export type OperationState = 'PREPARING' | 'SENDING' | 'SLOW' | 'PENDING' | 'UNKNOWN'
export interface OperationRecord extends OperationSpec {
  key: string; owner: string; state: OperationState; createdAt: number; updatedAt: number
  operationId?: string; uploadId?: string; retryAt?: number
}
export class OperationBlocked extends Error {
  constructor(public reason: 'busy' | 'identity' | 'storage' | 'capacity' | 'invalid' | 'retry') { super('operation.' + reason) }
}
export interface OperationTicket { readonly key: string; current: () => boolean }
export type OperationResult<T> =
  | { kind: 'success'; value: T }
  | { kind: 'completed' }
  | { kind: 'pending'; value?: T; error?: unknown }
  | { kind: 'unknown' | 'rejected' | 'failed'; error?: unknown }
  | { kind: 'stale' }
export interface OperationSend {
  idempotencyKey: string
  onResponse: (meta: ResponseMeta) => void
}
interface Entry {
  record: OperationRecord
  owner: OperationOwner
  ticket: OperationTicket
  active: boolean
  uncertain: boolean
  send?: (options: OperationSend) => Promise<unknown>
}
interface NativeResults<T> { success: (value: T) => LocalOutcome; failure?: (cause: unknown) => LocalOutcome }
const prefix = 'sma.operation.v1.'
const identifier = /^[A-Za-z0-9_.:-]{1,160}$/
const resourceKey = (value: unknown): value is string => typeof value === 'string' && value.length <= 160
  && (identifier.test(value) || value.endsWith('.*') && identifier.test(value.slice(0, -2)))
/** A collection marker is explicit and bounded to its namespace, never a user-input path glob. */
export function resourcesConflict(left: string, right: string) {
  return left === right || left.endsWith('.*') && right.startsWith(left.slice(0, -1))
    || right.endsWith('.*') && left.startsWith(right.slice(0, -1))
}
const targets = (row: OperationSpec) => [row.resource, ...(row.resources || [])]
const copyRecord = (row: OperationRecord): OperationRecord => ({ ...row, resources: row.resources ? [...row.resources] : undefined })
const uuid = /^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/
const scopeKey = (owner: OperationOwner) => JSON.stringify(owner.scope)
const sameOwner = (a: OperationOwner, b: OperationOwner | null) => !!b && scopeKey(a) === scopeKey(b) && a.session === b.session

/** In-tab synchronous ownership. Persistence is recovery metadata, not an atomic cross-tab lock.
 * No body, password, file, token or executable request is persisted. Backend governance remains authoritative. */
export class OperationRegistry {
  private entries = new Map<string, Entry>()
  private listeners = new Set<() => void>()
  private loaded = false
  private storageFailed = false
  private generation = 0
  constructor(private owner: () => OperationOwner | null, private storage: () => Storage,
    private now: () => number = Date.now, private newKey: () => string = () => crypto.randomUUID(), private capacity = 100) {}

  subscribe(listener: () => void) { this.listeners.add(listener); return () => { this.listeners.delete(listener) } }
  /** Called on logout, lock or identity transitions, even if the same actor later returns. */
  invalidate() {
    this.generation++
    for (const [key, entry] of this.entries) {
      if (entry.record.state === 'PREPARING') this.entries.delete(key)
      else { entry.uncertain = true; entry.record.state = 'UNKNOWN'; entry.send = undefined }
    }
    this.changed()
  }
  private changed() { for (const listener of this.listeners) listener() }
  private requireOwner() {
    const owner = this.owner()
    if (!owner?.session || owner.scope.length !== 4 || owner.scope.some(part => typeof part !== 'string' || !part || part.length > 1024)) throw new OperationBlocked('identity')
    return { scope: [...owner.scope] as [string, string, string, string], session: owner.session }
  }
  private load() {
    if (this.storageFailed) throw new OperationBlocked('storage')
    if (this.loaded) return
    try {
      const storage = this.storage()
      if (storage.length > 10000) throw new Error('Storage budget')
      for (let index = 0; index < storage.length; index++) {
        const key = storage.key(index)
        if (!key?.startsWith(prefix)) continue
        const raw = storage.getItem(key)
        if (!raw || raw.length > 8192 || this.entries.size >= 1000) throw new Error('Record budget')
        const row = JSON.parse(raw) as OperationRecord
        const owner = JSON.parse(row.owner) as string[]
        if (!Array.isArray(owner) || owner.length !== 4 || owner.some(part => typeof part !== 'string' || !part || part.length > 1024)
          || !uuid.test(row.key) || key !== prefix + row.key || !identifier.test(row.operation) || !resourceKey(row.resource)
          || row.resources !== undefined && (!Array.isArray(row.resources) || row.resources.length > 99 || row.resources.some(value => !resourceKey(value)))
          || !['LOCAL', 'UPLOAD', 'NATIVE'].includes(row.protocol)
          || !Number.isSafeInteger(row.createdAt) || !Number.isSafeInteger(row.updatedAt)
          || row.createdAt < 0 || row.updatedAt < row.createdAt
          || row.operationId !== undefined && !identifier.test(row.operationId)
          || row.uploadId !== undefined && !identifier.test(row.uploadId)
          || row.retryAt !== undefined && (!Number.isSafeInteger(row.retryAt) || row.retryAt < 0)) throw new Error('Invalid record')
        // Whitelist fields; no extra stored values are reflected into the UI or executable requests.
        const record: OperationRecord = { key: row.key, owner: row.owner, operation: row.operation, resource: row.resource,
          resources: row.resources ? [...row.resources] : undefined, protocol: row.protocol, state: 'UNKNOWN', createdAt: row.createdAt, updatedAt: row.updatedAt,
          operationId: row.operationId, uploadId: row.uploadId, retryAt: row.retryAt }
        const ticket: OperationTicket = { key: row.key, current: () => false }
        this.entries.set(row.key, { record, owner: { scope: owner as [string, string, string, string], session: '' }, ticket, active: false, uncertain: true })
      }
      this.loaded = true
    } catch { this.storageFailed = true; throw new OperationBlocked('storage') }
  }
  private persist(entry: Entry) {
    try {
      const row = entry.record
      this.storage().setItem(prefix + row.key, JSON.stringify({ ...row, state: 'UNKNOWN' }))
    } catch { this.storageFailed = true; throw new OperationBlocked('storage') }
  }
  private release(entry: Entry) {
    try { this.storage().removeItem(prefix + entry.record.key) }
    catch { this.storageFailed = true; entry.record.state = 'UNKNOWN'; entry.uncertain = true; this.changed(); throw new OperationBlocked('storage') }
    this.entries.delete(entry.record.key); this.changed()
  }
  list(): OperationRecord[] {
    this.load()
    const current = this.owner()
    if (!current) return []
    return [...this.entries.values()].filter(entry => entry.record.owner === scopeKey(current)).map(entry => copyRecord(entry.record))
  }
  reserve(spec: OperationSpec): OperationTicket {
    const owner = this.requireOwner(); this.load()
    if (!identifier.test(spec.operation) || !resourceKey(spec.resource) || !['LOCAL', 'UPLOAD', 'NATIVE'].includes(spec.protocol)
      || spec.nativeId !== undefined && (spec.protocol === 'LOCAL' || !identifier.test(spec.nativeId))
      || spec.resources !== undefined && (!Array.isArray(spec.resources) || spec.resources.length > 100 || spec.resources.some(value => !resourceKey(value)))) throw new OperationBlocked('invalid')
    const resources = spec.resources ? [...new Set(spec.resources)].filter(value => value !== spec.resource).sort() : undefined
    if (resources && resources.length > 99) throw new OperationBlocked('capacity')
    const owned = [...this.entries.values()].filter(entry => entry.record.owner === scopeKey(owner))
    // Actions on the same logical resource share the lock, even when operation names differ.
    if (owned.some(entry => targets(entry.record).some(existing => targets({ ...spec, resources }).some(wanted => resourcesConflict(existing, wanted))))) throw new OperationBlocked('busy')
    if (owned.length >= this.capacity) throw new OperationBlocked('capacity')
    const key = this.newKey()
    if (!uuid.test(key) || this.entries.has(key)) throw new OperationBlocked('invalid')
    const generation = this.generation
    const ticket: OperationTicket = { key, current: () => generation === this.generation && sameOwner(owner, this.owner()) }
    const record: OperationRecord = { operation: spec.operation, resource: spec.resource, resources: resources?.length ? resources : undefined, protocol: spec.protocol,
      key, owner: scopeKey(owner), state: 'PREPARING', createdAt: this.now(), updatedAt: this.now() }
    if (spec.nativeId) { if (spec.protocol === 'UPLOAD') record.uploadId = spec.nativeId; else record.operationId = spec.nativeId }
    if (JSON.stringify(record).length > 7168) throw new OperationBlocked('capacity')
    this.entries.set(key, { owner, ticket, active: false, uncertain: false, record })
    this.changed(); return ticket
  }
  private entry(ticket: OperationTicket) {
    const entry = this.entries.get(ticket.key)
    if (!entry || entry.ticket !== ticket || !ticket.current()) throw new OperationBlocked('identity')
    return entry
  }
  cancel(ticket: OperationTicket) {
    const entry = this.entries.get(ticket.key)
    if (entry?.ticket === ticket && entry.record.state === 'PREPARING') { this.entries.delete(ticket.key); this.changed() }
  }
  /** Call before any await used for confirmation/validation, then execute only the captured immutable input.
   * An explicit LOCAL retry may use this same ticket/key only after the previous call settled. */
  async execute<T>(ticket: OperationTicket, send: (options: OperationSend) => Promise<T>, native?: NativeResults<T>): Promise<OperationResult<T>> {
    const entry = this.entry(ticket)
    if (entry.active) throw new OperationBlocked('busy')
    if (entry.record.state !== 'PREPARING' && (entry.record.protocol !== 'LOCAL' || entry.record.state !== 'UNKNOWN')) throw new OperationBlocked('retry')
    if (entry.send && entry.send !== send) throw new OperationBlocked('retry')
    if (entry.record.retryAt && this.now() < entry.record.retryAt) throw new OperationBlocked('retry')
    if (entry.record.protocol !== 'LOCAL' && !native) throw new OperationBlocked('invalid')
    // Durable UNKNOWN precedes network dispatch. Reload can only query, never reconstruct a write.
    this.persist(entry)
    // Native commands can contain grants or OTPs. They are never retained for replay.
    entry.send = entry.record.protocol === 'LOCAL' ? send : undefined
    entry.active = true; entry.record.state = 'SENDING'; entry.record.updatedAt = this.now(); this.changed()
    const timer = setTimeout(() => {
      if (entry.active && entry.record.state === 'SENDING') { entry.record.state = 'SLOW'; this.changed() }
    }, 3000)
    const metadata = (meta: ResponseMeta) => {
      if (meta.operationId && identifier.test(meta.operationId)) entry.record.operationId = meta.operationId
      if (meta.uploadId && identifier.test(meta.uploadId)) entry.record.uploadId = meta.uploadId
      if (meta.retryAfterSeconds !== undefined && Number.isSafeInteger(meta.retryAfterSeconds) && meta.retryAfterSeconds >= 0
        && Number.isSafeInteger(this.now() + meta.retryAfterSeconds * 1000)) entry.record.retryAt = this.now() + meta.retryAfterSeconds * 1000
    }
    let result: OperationResult<T>
    try {
      const value = await send({ idempotencyKey: ticket.key, onResponse: metadata })
      if (!ticket.current()) result = { kind: 'stale' }
      else {
        const outcome = entry.record.protocol === 'LOCAL' ? 'completed' : native!.success(value)
        result = outcome === 'completed' ? { kind: 'success', value }
          : outcome === 'pending' ? { kind: 'pending', value }
            : { kind: outcome === 'failed' ? 'failed' : outcome === 'rejected' ? 'rejected' : 'unknown' }
      }
    } catch (error) {
      if (error instanceof ApiError) {
        // A nested LOCAL admission receipt is not a native task identity or completion result.
        metadata({ ...error.meta, operationId: entry.record.protocol === 'LOCAL' ? error.meta.operationId : undefined })
        const receipt = entry.record.protocol === 'LOCAL' ? operationReceipt(error.details) : undefined
        if (receipt) entry.record.operationId = receipt.operationId
      }
      if (!ticket.current()) result = { kind: 'stale' }
      else {
        const outcome = entry.record.protocol === 'LOCAL' ? localFailure(error, entry.uncertain) : native?.failure?.(error) || 'unknown'
        result = outcome === 'completed' ? { kind: 'completed' } : { kind: outcome, error }
      }
    } finally { clearTimeout(timer); entry.active = false }
    if (['success', 'completed', 'failed', 'rejected'].includes(result.kind)) this.release(entry)
    else {
      entry.uncertain = true; entry.record.state = result.kind === 'pending' ? 'PENDING' : 'UNKNOWN'; entry.record.updatedAt = this.now()
      this.persist(entry); this.changed()
    }
    return result
  }
  /** A scoped lookup may finish a restored record. NOT_FOUND never releases the original intent. */
  private reconcile(key: string, value: unknown) {
    const owner = this.requireOwner(); this.load()
    const entry = this.entries.get(key), receipt = operationReceipt(value)
    if (!entry || entry.record.owner !== scopeKey(owner) || entry.record.protocol !== 'LOCAL' || !receipt) throw new OperationBlocked('invalid')
    if (entry.active) throw new OperationBlocked('busy')
    if (entry.record.operationId && entry.record.operationId !== receipt.operationId) throw new OperationBlocked('invalid')
    entry.record.operationId = receipt.operationId
    if (receipt.state === 'SUCCEEDED' || receipt.state === 'FAILED_FINAL') this.release(entry)
    else { entry.uncertain = true; entry.record.state = receipt.state === 'RUNNING' ? 'PENDING' : 'UNKNOWN'; entry.record.updatedAt = this.now(); this.persist(entry); this.changed() }
    return receipt.state
  }
  /** Bind the entire lookup to the current session, including restored same-person records. */
  async lookupLocal(key: string, fetch: (record: Readonly<OperationRecord>) => Promise<unknown>) {
    const owner = this.requireOwner(); this.load()
    const generation = this.generation
    const entry = this.entries.get(key)
    if (!entry || entry.record.owner !== scopeKey(owner) || entry.record.protocol !== 'LOCAL') throw new OperationBlocked('invalid')
    if (entry.active) throw new OperationBlocked('busy')
    const receipt = await fetch(copyRecord(entry.record))
    if (generation !== this.generation || !sameOwner(owner, this.owner())) throw new OperationBlocked('identity')
    return this.reconcile(key, receipt)
  }
  guard() { const owner = this.requireOwner(), generation = this.generation; return () => generation === this.generation && sameOwner(owner, this.owner()) }
  /** Native adapters supply validated task evidence; a successful HTTP response alone is insufficient. */
  async lookupNative<T>(key: string, fetch: (record: Readonly<OperationRecord>) => Promise<T>,
    evidence: (value: T) => { id: string; state: 'SUCCEEDED' | 'FAILED_FINAL' | 'PENDING' | 'UNKNOWN' }) {
    const owner = this.requireOwner(), current = this.guard(); this.load()
    const entry = this.entries.get(key)
    if (!entry || entry.record.owner !== scopeKey(owner) || entry.record.protocol === 'LOCAL') throw new OperationBlocked('invalid')
    if (entry.active) throw new OperationBlocked('busy')
    const value = await fetch(copyRecord(entry.record))
    if (!current()) throw new OperationBlocked('identity')
    const result = evidence(value), previous = entry.record.protocol === 'UPLOAD' ? entry.record.uploadId : entry.record.operationId
    if (!identifier.test(result.id) || previous && previous !== result.id || !['SUCCEEDED', 'FAILED_FINAL', 'PENDING', 'UNKNOWN'].includes(result.state)) throw new OperationBlocked('invalid')
    if (entry.record.protocol === 'UPLOAD') entry.record.uploadId = result.id
    else entry.record.operationId = result.id
    if (result.state === 'SUCCEEDED' || result.state === 'FAILED_FINAL') this.release(entry)
    else { entry.uncertain = true; entry.record.state = result.state; entry.record.updatedAt = this.now(); this.persist(entry); this.changed() }
    return { value, state: result.state }
  }
}

export type { OperationReceipt }
