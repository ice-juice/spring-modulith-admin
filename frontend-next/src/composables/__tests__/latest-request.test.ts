import { effectScope } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { saveToken, clearToken } from '@/lib/token'
import { createPinia, setActivePinia } from 'pinia'
import { useAuth } from '@/stores/auth'
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
  it('merges identical in-flight reads, cancels obsolete reads and never shares across sessions', async () => {
    clearToken()
    const scope = effectScope(), request = scope.run(useLatestRequest)!, accepted = vi.fn()
    let firstSignal!: AbortSignal, complete!: (value: string) => void
    const read = vi.fn((signal: AbortSignal) => { firstSignal = signal; return new Promise<string>(resolve => { complete = resolve }) })
    const first = request.run(read, accepted, 'query-one'), duplicate = request.run(read, accepted, 'query-one')
    expect(first).toBe(duplicate); expect(read).toHaveBeenCalledOnce()
    await request.run(async () => 'new-result', accepted, 'query-two')
    expect(firstSignal.aborted).toBe(true); complete('old-result'); await first
    expect(accepted).toHaveBeenCalledExactlyOnceWith('new-result')
    saveToken('session-a', false)
    const delayed = request.run(() => new Promise<string>(resolve => { complete = resolve }), accepted, 'query-two')
    saveToken('session-b', false); complete('old-session'); await delayed
    expect(accepted).toHaveBeenCalledTimes(1); scope.stop(); clearToken()
  })
  it('invalidates pending reads on lock and does not dispatch after disposal', async () => {
    const scope = effectScope(), request = scope.run(useLatestRequest)!, accepted = vi.fn()
    let complete!: (value: number) => void, signal!: AbortSignal
    const pending = request.run(input => { signal = input; return new Promise<number>(resolve => { complete = resolve }) }, accepted)
    window.dispatchEvent(new Event('sma:locked')); complete(1); await pending
    expect(signal.aborted).toBe(true); expect(accepted).not.toHaveBeenCalled(); scope.stop()
    const read = vi.fn(async () => 2); await request.run(read, accepted); expect(read).not.toHaveBeenCalled()
  })
  it('does not leave a permanently merged request after a synchronous loader failure', async () => {
    const scope = effectScope(), request = scope.run(useLatestRequest)!, accepted = vi.fn()
    await request.run(() => { throw new Error('Local preparation failed') }, accepted, 'same')
    await request.run(async () => 'fixed', accepted, 'same')
    expect(accepted).toHaveBeenCalledWith('fixed'); scope.stop()
  })
  it('does not accept or merge a read after permissions change within the same session', async () => {
    setActivePinia(createPinia()); saveToken('same-session', false)
    const auth = useAuth()
    auth.user = { id: '1', username: 'test', name: 'Test', portal: 'admin', superAdmin: false, permissions: ['read'], menus: [], navigation: [] }
    const scope = effectScope(), request = scope.run(useLatestRequest)!, accepted = vi.fn()
    let complete!: (value: number) => void
    const pending = request.run(() => new Promise<number>(resolve => { complete = resolve }), accepted, 'same-query')
    auth.user.permissions = []; complete(1); await pending
    expect(accepted).not.toHaveBeenCalled()
    scope.stop(); clearToken(); setActivePinia(undefined)
  })
})
