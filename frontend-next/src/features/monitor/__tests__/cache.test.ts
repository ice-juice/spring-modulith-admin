import { describe, expect, it, vi } from 'vitest'
import { cacheTaskEvidence, cacheCount, durationMillis, filterCaches, permitsCacheAction, CacheSubmission, CacheRequestSuperseded, type CacheTask, type CacheRegion } from '../cache'

function task(state = 'PREVIEW'): CacheTask { return { id: 'same-task', action: 'CLEAR', state, reason: 'test', createdAt: '', expiresAt: '', cancelRequested: false, targets: [], skipped: [], items: [] } }
function region(id: string, overrides: Partial<CacheRegion> = {}): CacheRegion {
  return { id, name: id, owner: 'config', group: 'basic', scenario: 'options', kind: 'DATA', scope: 'TENANT', schemaVersion: 1, managePermission: 'monitor:cache:view', declaredOperations: ['CLEAR'], allowedOperations: ['CLEAR'], invalidatedBy: [], state: 'ACTIVE', observation: null, prewarming: null, ...overrides }
}
describe('cache presentation boundaries', () => {
  it('keeps large counters exact and unknown distinct from zero', () => {
    expect(cacheCount('9007199254740993123', 'en-US', '?')).toBe('9,007,199,254,740,993,123')
    for (const value of [-1, '-1', undefined, 'NaN', Number.MAX_SAFE_INTEGER + 1]) expect(cacheCount(value, 'en-US', '?')).toBe('?')
    expect(cacheCount('0', 'en-US', '?')).toBe('0')
    expect(durationMillis('PT5M')).toBe(300000)
    expect(durationMillis('PT0.123S')).toBe(123)
    expect(durationMillis(0.123)).toBe(123)
    expect(durationMillis(60)).toBe(60000)
    expect(durationMillis(-1)).toBeNull()
    expect(durationMillis('PT24H')).toBe(86400000)
    expect(durationMillis('PT25H')).toBeNull()
    expect(durationMillis('arbitrary')).toBeNull()
  })
  it('intersects every filter and never derives permissions from group membership', () => {
    const a = region('config.a'), b = region('config.b', { scenario: 'private', allowedOperations: [] })
    expect(filterCaches([a, b], { owner: 'config', group: 'basic', scenario: 'options', kind: 'DATA', provider: 'adapter', status: 'ACTIVE', keyword: 'a' })).toEqual([a])
    expect(permitsCacheAction(b, 'CLEAR')).toBe(false)
    expect(permitsCacheAction(region('config.c', { allowedOperations: ['PREWARM'] }), 'PREWARM')).toBe(false)
    expect(permitsCacheAction(region('config.d', { allowedOperations: ['PAUSE'] }), 'RESUME')).toBe(true)
  })
})
describe('one cache task intent', () => {
  it('recovers a lost response by reading without sending another write', async () => {
    const api = { submit: vi.fn().mockRejectedValue(new Error('lost response')), task: vi.fn().mockResolvedValue(task('QUEUED')) }
    const submission = new CacheSubmission(task(), api, undefined, 'original-key')
    expect((await submission.submit()).state).toBe('QUEUED')
    expect((await submission.submit()).state).toBe('QUEUED')
    expect(api.submit).toHaveBeenCalledExactlyOnceWith('same-task', 'original-key')
    expect(submission.uncertain).toBe(false)
  })
  it('keeps the original key after uncertainty and inspects before an explicit retry', async () => {
    const order: string[] = []
    const api = { submit: vi.fn(async () => { order.push('write'); if (order.length === 1) throw new Error('timeout'); return task('QUEUED') }), task: vi.fn(async () => { order.push('read'); return task() }) }
    const submission = new CacheSubmission(task(), api, undefined, 'same-key')
    await expect(submission.submit()).rejects.toThrow('timeout')
    expect(submission.uncertain).toBe(true)
    await submission.submit()
    expect(order).toEqual(['write', 'read', 'read', 'write'])
    expect(api.submit.mock.calls).toEqual([['same-task', 'same-key'], ['same-task', 'same-key']])
  })
  it('shares concurrent clicks and discards a response after identity changes', async () => {
    let complete!: (value: CacheTask) => void
    let owns = true
    const api = { submit: vi.fn(() => new Promise<CacheTask>(resolve => { complete = resolve })), task: vi.fn() }
    const submission = new CacheSubmission(task(), api, () => owns, 'original')
    const first = submission.submit(), second = submission.submit()
    expect(first).toBe(second); owns = false; complete(task('QUEUED'))
    await expect(first).rejects.toBeInstanceOf(CacheRequestSuperseded)
    expect(submission.view.state).toBe('PREVIEW'); expect(api.task).not.toHaveBeenCalled()
  })
  it('keeps unknown state when inspection fails and never manufactures a successful result', async () => {
    const api = { submit: vi.fn().mockRejectedValue(new Error('timeout')), task: vi.fn().mockRejectedValue(new Error('offline')) }
    const submission = new CacheSubmission(task(), api, undefined, 'original')
    await expect(submission.submit()).rejects.toThrow('timeout')
    expect(submission.uncertain).toBe(true)
    await expect(submission.submit()).rejects.toThrow('offline')
    expect(api.submit).toHaveBeenCalledTimes(1)
    submission.invalidate(); await expect(submission.refresh()).rejects.toBeInstanceOf(CacheRequestSuperseded)
  })
})


it('distinguishes queued work, terminal failure and partial native uncertainty', () => {
  expect(cacheTaskEvidence(task('QUEUED'), 'same-task').state).toBe('PENDING')
  expect(cacheTaskEvidence(task('SUCCEEDED'), 'same-task').state).toBe('SUCCEEDED')
  expect(cacheTaskEvidence(task('PARTIAL'), 'same-task').state).toBe('FAILED_FINAL')
  const partial = task('PARTIAL')
  partial.items = [{ region: 'native', state: 'UNKNOWN', dataRevision: null, controlVersion: null, failureCode: 'NATIVE_RESULT_UNKNOWN', warmup: null }]
  expect(cacheTaskEvidence(partial, 'same-task').state).toBe('UNKNOWN')
  expect(() => cacheTaskEvidence(task(), 'another-task')).toThrow('identity mismatch')
})
