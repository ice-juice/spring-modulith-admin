import { effectScope } from 'vue'
import { describe, expect, it } from 'vitest'
import { useLatestRequest } from '../useLatestRequest'
describe('view request ownership', () => {
  it('rejects obsolete results and ignores completion after disposal', async () => {
    const scope = effectScope(), request = scope.run(useLatestRequest)!
    const accepted: string[] = []
    let resolveFirst!: (value: string) => void
    const first = request.run(() => new Promise<string>(resolve => { resolveFirst = resolve }), value => accepted.push(value))
    await request.run(() => Promise.resolve('latest'), value => accepted.push(value))
    resolveFirst('obsolete'); await first
    expect(accepted).toEqual(['latest']); expect(request.loading.value).toBe(false)
    let resolveDisposed!: (value: string) => void
    const disposed = request.run(() => new Promise<string>(resolve => { resolveDisposed = resolve }), value => accepted.push(value))
    scope.stop(); resolveDisposed('disposed'); await disposed
    expect(accepted).toEqual(['latest'])
  })
  it('retains only the current error', async () => {
    const scope = effectScope(), request = scope.run(useLatestRequest)!
    await request.run(() => Promise.reject(new Error('Current failure')), () => {})
    expect(request.error.value).toBe('Current failure')
    await request.run(() => Promise.resolve('ok'), () => {})
    expect(request.error.value).toBe(''); scope.stop()
  })
})
