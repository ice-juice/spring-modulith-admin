import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import { post } from '@/api/http'
import { OperationRegistry, type OperationOwner } from '@/lib/operation-registry'
import { useOperationPanel } from '../useOperationPanel'
vi.mock('@/api/http', async original => ({ ...await original<typeof import('@/api/http')>(), post: vi.fn() }))
let owner: OperationOwner | null
const scopes: ReturnType<typeof effectScope>[] = []
const receipt = (state = 'SUCCEEDED') => ({ operationId: 'receipt-1', operation: 'parameters.refresh', state, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' })
function harness(storage: () => Storage = () => localStorage) {
  const registry = new OperationRegistry(() => owner, storage)
  const scope = effectScope(); scopes.push(scope)
  const panel = scope.run(() => useOperationPanel(registry))!
  return { registry, panel, scope }
}
async function unknown(registry: OperationRegistry, protocol: 'LOCAL' | 'NATIVE' = 'LOCAL', resource = 'config.parameters.maintenance') {
  const ticket = registry.reserve({ operation: 'parameters.refresh', resource, protocol })
  await registry.execute(ticket, async () => { throw new Error('response lost') }, protocol === 'NATIVE' ? { success: () => 'completed' } : undefined)
  return ticket.key
}
beforeEach(() => {
  localStorage.clear(); vi.clearAllMocks()
  owner = { scope: ['https://site/api', 'tenant-1', 'admin', 'user-1'], session: 'private-token' }
  vi.mocked(post).mockResolvedValue(receipt())
})
afterEach(() => { scopes.splice(0).forEach(scope => scope.stop()) })

it('projects current scope only, including restored records, and drops old messages on identity change', async () => {
  const { registry, panel } = harness(), original = owner!
  await unknown(registry)
  expect(panel.count.value).toBe(1)
  owner = { ...original, scope: ['https://site/api', 'tenant-2', 'admin', 'user-1'] }; registry.invalidate()
  expect(panel.rows.value).toEqual([])
  await unknown(registry); expect(panel.count.value).toBe(1)
  owner = original; registry.invalidate(); expect(panel.count.value).toBe(1)
  const restored = harness(); expect(restored.panel.rows.value[0]?.state).toBe('UNKNOWN')
  owner = null; registry.invalidate()
  expect(panel.rows.value).toEqual([]); expect(panel.unavailable.value).toBe(false)
})
it('makes one original-key lookup, resolves only a terminal receipt and retains native work', async () => {
  const { registry, panel } = harness()
  const key = await unknown(registry), native = await unknown(registry, 'NATIVE', 'auth.sessions.other')
  let resolve!: (value: unknown) => void
  vi.mocked(post).mockReturnValueOnce(new Promise(done => { resolve = done }))
  const pending = panel.check(key)
  for (let i = 0; i < 20; i++) await panel.check(key)
  expect(post).toHaveBeenCalledExactlyOnceWith('/common/requests/lookup', { key })
  resolve(receipt()); await pending
  expect(panel.result.value).toBe('common.operationPanel.results.SUCCEEDED')
  expect(panel.rows.value.map(row => row.key)).toEqual([native])
  await panel.check(native); expect(post).toHaveBeenCalledOnce()
})
it('retains NOT_FOUND, malformed, RUNNING and UNKNOWN receipts and hides error payloads', async () => {
  const { registry, panel } = harness(), key = await unknown(registry)
  for (const value of [null, {}, { state: 'NOT_FOUND' }, receipt('RUNNING'), receipt('UNKNOWN')]) {
    vi.mocked(post).mockResolvedValueOnce(value); await panel.check(key)
    expect(panel.rows.value[0]?.key).toBe(key)
  }
  vi.mocked(post).mockRejectedValueOnce(new Error('private response body')); await panel.check(key)
  expect(panel.error.value).toBe('common.operationPanel.lookupFailed')
  expect(panel.rows.value[0]?.state).toBe('UNKNOWN')
})
it.each(['identity', 'lock', 'dispose'])('isolates late receipt presentation after %s', async change => {
  const { registry, panel, scope } = harness(), key = await unknown(registry)
  let resolve!: (value: unknown) => void
  vi.mocked(post).mockReturnValueOnce(new Promise(done => { resolve = done }))
  const pending = panel.check(key)
  if (change === 'dispose') scope.stop()
  else { owner = change === 'lock' ? null : { ...owner!, session: 'new-token' }; registry.invalidate() }
  resolve(receipt()); await pending
  expect(panel.result.value).toBe('')
  if (change !== 'dispose') expect(localStorage.getItem('sma.operation.v1.' + key)).not.toBeNull()
})
it('reports corrupt storage and never queries preparation or future cooldown', async () => {
  const corrupt = harness(() => { throw new Error('storage denied') })
  expect(corrupt.panel.unavailable.value).toBe(true); expect(corrupt.panel.error.value).toBe('common.operation.storage')
  const { registry, panel } = harness()
  const ticket = registry.reserve({ operation: 'parameters.refresh', resource: 'config.parameters.maintenance', protocol: 'LOCAL' })
  await panel.check(ticket.key); expect(post).not.toHaveBeenCalled(); registry.cancel(ticket)
  const key = await unknown(registry)
  const saved = JSON.parse(localStorage.getItem('sma.operation.v1.' + key)!)
  saved.retryAt = Date.now() + 60000; localStorage.setItem('sma.operation.v1.' + key, JSON.stringify(saved))
  registry.synchronize(); await panel.check(key)
  expect(post).not.toHaveBeenCalled(); expect(panel.error.value).toBe('common.operation.retry')
})

it('queries an upload through its native adapter, retains ACCEPTED and never reconstructs the original file', async () => {
  const { registry, panel } = harness()
  const ticket = registry.reserve({ operation: 'file.upload', resource: 'file.notice.new.original-form', protocol: 'UPLOAD' })
  await registry.execute(ticket, async () => { throw new Error('lost') }, { success: () => 'completed' })
  expect(panel.attentionCount.value).toBe(1)
  const uploadId = '11111111-1111-4111-8111-111111111111'
  vi.mocked(post).mockResolvedValueOnce({ uploadId, state: 'ACCEPTED', fileId: null, resumable: true })
  await panel.check(ticket.key)
  expect(post).toHaveBeenLastCalledWith('/file/uploads/lookup', { key: ticket.key })
  expect(panel.rows.value[0]).toMatchObject({ key: ticket.key, state: 'PENDING', uploadId })
  vi.mocked(post).mockResolvedValueOnce({ uploadId: '22222222-2222-4222-8222-222222222222', state: 'SUCCEEDED', fileId: '123', resumable: false })
  await panel.check(ticket.key)
  expect(panel.attentionCount.value).toBe(1)
  vi.mocked(post).mockResolvedValueOnce({ uploadId, state: 'SUCCEEDED', fileId: '123', resumable: false })
  await panel.check(ticket.key)
  expect(panel.attentionCount.value).toBe(0)
  expect(post).toHaveBeenCalledTimes(3)
  expect(panel.result.value).toBe('common.operationPanel.results.SUCCEEDED')
})
