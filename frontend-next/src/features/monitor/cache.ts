import { get, http, post, put, type ApiResponse, type RequestOptions } from '@/api/http'

export type CacheAction = 'CLEAR' | 'PAUSE' | 'RESUME' | 'RESET_STATS' | 'PREWARM'
export type CacheOperation = CacheAction | 'EDIT_POLICY'
export type CacheDuration = string | number
export interface CachePolicy {
  ttl: CacheDuration
  negativeTtl: CacheDuration
  maxEntries: string
  maxBytes: string
  maxEntryBytes: number
}
export interface CacheControl {
  version: string
  dataRevision: string
  statsEpoch: string
  paused: boolean | null
  requestedPolicy: CachePolicy | null
  confirmed: boolean
}
export interface WarmupSpec {
  region: string
  version: number
  readPermission: string
  limits: { pageSize: number; maxItems: number; itemsPerSecond: number; maxDuration: CacheDuration }
}
export interface CacheObservation {
  provider: string
  processId: string
  sampledAt: string
  entryScope: string
  counterScope: string
  byteMeasurement: string
  providerByteLimit: string
  providerByteLimitScope: string
  policy: { declared: CachePolicy; effective: CachePolicy | null; sources: Record<string, string> }
  snapshot: { region: string; provider: string; size: string; bytes: string; hits: string; misses: string; bypasses: string; rejectedWrites: string }
  control: CacheControl | null
}
export interface CacheRegion {
  id: string
  name: string
  owner: string
  group: string
  scenario: string
  kind: string
  scope: string
  schemaVersion: number
  managePermission: string
  declaredOperations: string[]
  allowedOperations: string[]
  invalidatedBy: string[]
  state: string
  observation: CacheObservation | null
  prewarming: { available: boolean; reason: string; specification: WarmupSpec | null } | null
  resource?: { region: string; provider: string; processId: string; sampledAt: string; state: string; entries: string; entryLimit: string; bytes: string; protectedEntries: string; lifecycleKey: string } | null
  lifecycleDescription?: string | null
}
export interface CacheTarget {
  region: string
  nameKey: string
  scope: string
  managePermission: string
  controlVersion: string
  registrationHash: string
  warmup: WarmupSpec | null
  resource?: { processId: string; nodeId: string; adapterVersion: number } | null
}
export interface CacheItem {
  region: string
  state: string
  dataRevision: string | null
  controlVersion: string | null
  failureCode: string
  warmup: { checkpointedReads: string; processId: string; startedAt: string | null } | null
  resource?: { processId: string; startedAt: string | null; completedAt: string | null; affectedEntries: string | null; remainingEntries: string | null; routeDeadline: string | null } | null
}
export interface CacheTask {
  id: string
  action: CacheAction
  state: string
  reason: string
  createdAt: string
  expiresAt: string
  cancelRequested: boolean
  targets: CacheTarget[]
  skipped: { region: string; reason: string }[]
  items: CacheItem[]
}
export interface CacheNode {
  nodeId: string
  processId: string | null
  epoch: string | null
  leaseUntil: string | null
  stateAtCapture: string
}
export interface CacheNodes {
  scope: { manifestHash: string; manifestVersion: string; capturedAt: string; members: CacheNode[] }
  localProcessId: string
  observerState: string
  lastHeartbeatAt: string | null
}
export interface CacheConfirmation {
  taskId: string
  logicalState: string
  coverage: string
  checkedAt: string
  allTargetsConfirmed: boolean
  allExpectedProcessesOnline: boolean
  reclamation: string
  nodes: {
    region: string; nodeId: string; expectedProcessId: string | null; expectedEpoch: string | null
    availability: string; confirmation: string; requiredRevision: string | null; requiredControlVersion: string | null
    observedRevision: string | null; observedControlVersion: string | null
    requestedAt: string | null; confirmedAt: string | null; lastError: string; lastAttemptAt: string | null
  }[]
}
export interface CachePolicyState {
  region: string; version: string; dataRevision: string; statsEpoch: string; paused: boolean
  requestedPolicy: CachePolicy | null; deploymentPolicy: CachePolicy; effectivePolicy: CachePolicy
}
export interface CacheHistory {
  id: string; occurredAt: string; actorId: string; portal: string; action: string; reason: string
  before: CachePolicyState; after: CachePolicyState
}
export interface CachePolicyInput {
  expectedVersion: string; reason: string; ttlMillis: number; negativeTtlMillis: number
  maxEntries: string; maxBytes: string; maxEntryBytes: number
}
const base = '/monitor/caches'
export const cacheApi = {
  directory: () => get<CacheRegion[]>(base + '/directory'),
  nodes: () => get<CacheNodes>(base + '/nodes'),
  tasks: () => get<CacheTask[]>(base + '/tasks', { limit: 100 }),
  task: (id: string) => get<CacheTask>(base + '/tasks/' + encodeURIComponent(id)),
  confirmation: (id: string) => get<CacheConfirmation>(base + '/tasks/' + encodeURIComponent(id) + '/confirmation'),
  preview: (action: CacheAction, regions: string[], reason: string) => post<CacheTask>(base + '/tasks/preview', { action, regions, reason }),
  submit: async (id: string, key: string) => (await http.post<ApiResponse<CacheTask>>(base + '/tasks/' + encodeURIComponent(id) + '/submit', undefined, { headers: { 'Idempotency-Key': key } })).data.data,
  cancel: (id: string, options?: RequestOptions) => post<CacheTask>(base + '/tasks/' + encodeURIComponent(id) + '/cancel', undefined, options),
  policy: (id: string) => get<CachePolicyState>(base + '/' + encodeURIComponent(id) + '/policy'),
  history: (id: string) => get<CacheHistory[]>(base + '/' + encodeURIComponent(id) + '/history', { limit: 50 }),
  savePolicy: (id: string, input: CachePolicyInput, options?: RequestOptions) => put<CachePolicyState>(base + '/' + encodeURIComponent(id) + '/policy', input, options),
  resetPolicy: (id: string, expectedVersion: string, reason: string, options?: RequestOptions) => post<CachePolicyState>(base + '/' + encodeURIComponent(id) + '/policy/reset', { expectedVersion, reason }, options),
}

/** Keep all counters exact; -1/invalid remain unknown instead of silently becoming zero. */
export function cacheCount(value: unknown, locale: string, unknown: string): string {
  if (typeof value !== 'string' && !(typeof value === 'number' && Number.isSafeInteger(value))) return unknown
  const raw = String(value)
  if (!/^\d+$/.test(raw)) return unknown
  return BigInt(raw).toLocaleString(locale)
}
export function durationMillis(value: CacheDuration): number | null {
  if (typeof value === 'number') {
    const millis = Math.round(value * 1000)
    return Number.isFinite(value) && value >= 0 && Number.isSafeInteger(millis) && millis <= 86400000 ? millis : null
  }
  const match = /^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d{1,3})?)S)?$/.exec(value)
  if (!match || !match.slice(1).some(Boolean)) return null
  const result = Math.round((Number(match[1] || 0) * 3600 + Number(match[2] || 0) * 60 + Number(match[3] || 0)) * 1000)
  return Number.isSafeInteger(result) && result <= 86400000 ? result : null
}
export function permitsCacheAction(row: CacheRegion, action: CacheAction): boolean {
  const capability = action === 'RESUME' ? 'PAUSE' : action
  return row.allowedOperations.includes(capability) && (action !== 'PREWARM' || row.prewarming?.available === true)
}
export interface CacheFilter { owner: string; group: string; scenario: string; kind: string; provider: string; status: string; keyword: string }
export function cacheProvider(row: CacheRegion): string { return row.observation?.provider || row.resource?.provider || 'adapter' }
export function filterCaches(rows: CacheRegion[], filter: CacheFilter): CacheRegion[] {
  return rows.filter(row => (!filter.owner || row.owner === filter.owner) && (!filter.group || row.group === filter.group)
    && (!filter.scenario || row.scenario === filter.scenario) && (!filter.kind || row.kind === filter.kind)
    && (!filter.provider || cacheProvider(row) === filter.provider) && (!filter.status || row.state === filter.status)
    && (!filter.keyword.trim() || (row.id + ' ' + row.name).toLocaleLowerCase().includes(filter.keyword.trim().toLocaleLowerCase())))
}

export class CacheRequestSuperseded extends Error {}
type SubmissionTransport = Pick<typeof cacheApi, 'task' | 'submit'>
/** One preview and one key for the whole user intent. No storage and no automatic second write. */
export class CacheSubmission {
  readonly key: string
  readonly id: string
  view: CacheTask
  uncertain = false
  private attempted = false
  private active = true
  private flight: Promise<CacheTask> | undefined
  constructor(preview: CacheTask, private transport: SubmissionTransport = cacheApi, private owns: () => boolean = () => true, key: string = crypto.randomUUID()) {
    this.view = preview; this.id = preview.id; this.key = key
  }
  invalidate() { this.active = false }
  private check() { if (!this.active || !this.owns()) throw new CacheRequestSuperseded() }
  private accept(view: CacheTask) {
    this.check()
    if (view.id !== this.id) throw new Error('Cache task identity mismatch')
    this.view = view; this.uncertain = false
    return view
  }
  async refresh(): Promise<CacheTask> { this.check(); return this.accept(await this.transport.task(this.id)) }
  submit(): Promise<CacheTask> {
    if (this.flight) return this.flight
    this.flight = this.send().finally(() => { this.flight = undefined })
    return this.flight
  }
  private async send(): Promise<CacheTask> {
    this.check()
    if (this.attempted) {
      const known = await this.refresh() // Explicit retry always inspects first.
      if (known.state !== 'PREVIEW') return known
    }
    this.check(); this.attempted = true
    try { return this.accept(await this.transport.submit(this.id, this.key)) }
    catch (failure) {
      this.check(); this.uncertain = true
      try {
        const known = await this.refresh()
        if (known.state !== 'PREVIEW') return known
        this.uncertain = true // An in-flight first commit can still be unknown.
      } catch { this.check(); this.uncertain = true }
      throw failure
    }
  }
}

/** Business task evidence, not the nested LOCAL enqueue receipt or node membership report. */
export function cacheTaskEvidence(task: CacheTask, id: string) {
  if (!task || task.id !== id) throw new Error('Cache task identity mismatch')
  const state = task.items.some(item => item.state === 'UNKNOWN') ? 'UNKNOWN' as const : task.state === 'SUCCEEDED' ? 'SUCCEEDED' as const
    : ['FAILED', 'PARTIAL', 'CANCELLED'].includes(task.state) ? 'FAILED_FINAL' as const
      : ['QUEUED', 'RUNNING'].includes(task.state) ? 'PENDING' as const : 'UNKNOWN' as const
  return { id, state }
}
