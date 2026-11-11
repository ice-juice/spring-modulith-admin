import type { Page, Route } from '@playwright/test'
import type { CacheTask, CacheRegion, CachePolicyState } from '../src/features/monitor/cache'
const stamp = '2026-11-11T08:00:00Z'
export const cachePolicy = { ttl: 'PT5M', negativeTtl: 'PT10S', maxEntries: '1000', maxBytes: '1048576', maxEntryBytes: 32768 }
export function cacheRegion(id = 'config.dictionary-options', overrides: Partial<CacheRegion> = {}): CacheRegion {
  return { id, name: '字典选项', owner: 'config', group: 'basic-config', scenario: 'dictionary-options', kind: 'DATA', scope: 'TENANT', schemaVersion: 1, managePermission: 'monitor:cache:view', declaredOperations: ['CLEAR', 'PREWARM', 'PAUSE', 'RESET_STATS', 'EDIT_POLICY'], allowedOperations: ['CLEAR', 'PREWARM', 'PAUSE', 'RESET_STATS', 'EDIT_POLICY'], invalidatedBy: ['item-write'], state: 'ACTIVE', prewarming: { available: true, reason: 'READY', specification: { region: id, version: 1, readPermission: 'config:dictionary:view', limits: { pageSize: 16, maxItems: 1000, itemsPerSecond: 20, maxDuration: 'PT2M' } } }, observation: { provider: 'redis', processId: 'process-a', sampledAt: stamp, entryScope: 'SHARED_TENANT_REGION', counterScope: 'PROCESS_OBSERVATION_LIFETIME', byteMeasurement: 'ENCODED_FIELDS_ESTIMATE', providerByteLimit: '1048576', providerByteLimitScope: 'SHARED_TENANT_REGION', policy: { declared: cachePolicy, effective: cachePolicy, sources: { ttl: 'DEPLOYMENT_DEFAULT', negativeTtl: 'DECLARED', maxEntries: 'DECLARED', maxBytes: 'DEPLOYMENT_CAP', maxEntryBytes: 'DECLARED' } }, snapshot: { region: id, provider: 'redis', size: '20', bytes: '2048', hits: '9007199254740993123', misses: '2', bypasses: '0', rejectedWrites: '0' }, control: { version: '9007199254740993123', dataRevision: '5', statsEpoch: '1', paused: false, requestedPolicy: null, confirmed: true } }, ...overrides }
}
export function createCacheFixture() {
  const regions = [cacheRegion(), cacheRegion('config.preferences', { name: '个人偏好', group: 'personal', scenario: 'preferences', scope: 'TENANT_USER', allowedOperations: ['CLEAR', 'PAUSE', 'EDIT_POLICY'], declaredOperations: ['CLEAR', 'PAUSE', 'EDIT_POLICY'], prewarming: null }), cacheRegion('auth.sessions', { name: '登录会话', owner: 'auth', group: 'security', scenario: 'login', kind: 'PROTECTED', allowedOperations: [], declaredOperations: [], observation: null, prewarming: null, state: 'PROTECTED' })]
  const tasks: CacheTask[] = []
  const writes: { path: string; method: string; body: Record<string, unknown>; key?: string }[] = []
  const policy: CachePolicyState = { region: regions[0]!.id, version: '9007199254740993123', dataRevision: '5', statsEpoch: '1', paused: false, requestedPolicy: null, deploymentPolicy: { ...cachePolicy }, effectivePolicy: { ...cachePolicy } }
  let lostSubmit = false, conflictPolicy = false
  async function handle(route: Route, path: string, method: string, body: Record<string, unknown>) {
    const ok = (data: unknown) => route.fulfill({ json: { code: 0, data, message: 'ok', error: null } })
    if (method !== 'GET') writes.push({ path, method, body, key: route.request().headers()['idempotency-key'] })
    if (path === '/monitor/caches/directory') return ok(regions)
    if (path === '/monitor/caches/nodes') return ok({ localProcessId: 'process-a', observerState: 'OWNED', lastHeartbeatAt: stamp, scope: { manifestHash: 'frozen-manifest', manifestVersion: '1', capturedAt: stamp, members: [{ nodeId: 'node-a', processId: 'process-a', epoch: '1', leaseUntil: stamp, stateAtCapture: 'ONLINE' }, { nodeId: 'node-b', processId: null, epoch: null, leaseUntil: null, stateAtCapture: 'OFFLINE' }] } })
    if (path === '/monitor/caches/tasks/preview') {
      const selected = regions.filter(region => (body.regions as string[]).includes(region.id)), action = body.action as CacheTask['action']
      const capable = selected.filter(region => region.allowedOperations.includes(action === 'RESUME' ? 'PAUSE' : action))
      const task: CacheTask = { id: 'preview-' + (tasks.length + 1), action, state: 'PREVIEW', reason: String(body.reason), createdAt: stamp, expiresAt: '2099-01-01T00:00:00Z', cancelRequested: false, targets: capable.map(row => ({ region: row.id, nameKey: 'config.cache.dictionaries', scope: row.scope, managePermission: row.managePermission, controlVersion: row.kind === 'RUNTIME' ? '0' : policy.version, registrationHash: 'frozen', warmup: action === 'PREWARM' ? row.prewarming?.specification || null : null, resource: row.kind === 'RUNTIME' ? { processId: row.resource?.processId || 'process-a', nodeId: 'node-a', adapterVersion: 1 } : null })), skipped: selected.filter(row => !capable.includes(row)).map(row => ({ region: row.id, reason: row.kind === 'PROTECTED' ? 'PROTECTED' : 'UNSUPPORTED' })), items: [] }
      tasks.unshift(task); return ok(task)
    }
    if (path === '/monitor/caches/tasks') return ok(tasks)
    const taskMatch = /^\/monitor\/caches\/tasks\/([^/]+)(?:\/(submit|cancel|confirmation))?$/.exec(path)
    if (taskMatch) {
      const task = tasks.find(value => value.id === taskMatch[1]); if (!task) return route.fulfill({ status: 404, json: { message: 'Task missing' } })
      if (taskMatch[2] === 'submit') { task.state = 'QUEUED'; task.items = task.targets.map(target => ({ region: target.region, state: 'PENDING', dataRevision: null, controlVersion: null, failureCode: '', warmup: null, resource: target.resource ? { processId: target.resource.processId, startedAt: null, completedAt: null, affectedEntries: null, remainingEntries: null, routeDeadline: '2099-01-01T00:00:00Z' } : null })); if (lostSubmit) { lostSubmit = false; return route.abort('failed') } }
      if (taskMatch[2] === 'cancel') { task.cancelRequested = true; task.state = 'CANCELLED' }
      if (taskMatch[2] === 'confirmation') return ok({ taskId: task.id, logicalState: task.state, coverage: task.targets.every(target => target.resource) ? 'NOT_APPLICABLE_RESOURCE' : 'DECLARED', checkedAt: stamp, allTargetsConfirmed: task.state === 'SUCCEEDED' && !task.targets.some(target => target.resource), allExpectedProcessesOnline: false, reclamation: 'NOT_MEASURED', nodes: task.targets.filter(target => !target.resource).map(target => ({ region: target.region, nodeId: 'node-b', expectedProcessId: 'old-process', expectedEpoch: '1', availability: 'OFFLINE', confirmation: task.state === 'SUCCEEDED' ? 'CONFIRMED' : 'WAITING', requiredRevision: '5', requiredControlVersion: '2', observedRevision: task.state === 'SUCCEEDED' ? '5' : null, observedControlVersion: task.state === 'SUCCEEDED' ? '2' : null, requestedAt: stamp, confirmedAt: task.state === 'SUCCEEDED' ? stamp : null, lastError: '', lastAttemptAt: null })) })
      return ok(task)
    }
    if (path.endsWith('/history')) return ok([])
    if (path.endsWith('/policy') || path.endsWith('/policy/reset')) {
      if (method !== 'GET') {
        if (conflictPolicy) { policy.version = '9007199254740993124'; conflictPolicy = false; return route.fulfill({ status: 409, json: { message: '策略已变化，请重新核对版本' } }) }
        policy.version = (BigInt(policy.version) + 1n).toString()
      }
      return ok(policy)
    }
    return ok(null)
  }
  return { regions, tasks, writes, policy, handle, loseSubmit: () => { lostSubmit = true }, conflictPolicy: () => { conflictPolicy = true } }
}
export async function installCacheFixture(page: Page) {
  const fixture = createCacheFixture()
  await page.route('**/api/monitor/caches/**', route => {
    const request = route.request(); let body: Record<string, unknown> = {}
    try { body = request.postDataJSON() || {} } catch { /* no request body */ }
    return fixture.handle(route, new URL(request.url()).pathname.replace(/^\/api/, ''), request.method(), body)
  })
  return fixture
}
