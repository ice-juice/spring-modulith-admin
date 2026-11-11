import { effectScope } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import { OperationRegistry } from '@/lib/operation-registry'
import { useSubmissionPool } from '../useSubmissionPool'
const owner = { scope: ['https://site/api', '1', 'admin', '1'] as const, session: 'session' }
const spec = (resource: string) => ({ operation: 'object.delete', resource, protocol: 'LOCAL' as const })
beforeEach(() => localStorage.clear())
it('runs different objects independently while sharing the same-object lock', async () => {
  const registry = new OperationRegistry(() => owner, () => localStorage), scope = effectScope(), pool = scope.run(() => useSubmissionPool(registry))!
  let release!: () => void; const gate = new Promise<void>(resolve => { release = resolve })
  const send = vi.fn(async () => { await gate; return true })
  const first = pool.get('object.1').run(spec('object.1'), () => send)
  const second = pool.get('object.2').run(spec('object.2'), () => send)
  expect(pool.busy('object.1')).toBe(true); expect(pool.busy('object.2')).toBe(true)
  const duplicate = vi.fn(() => send)
  expect(await pool.get('object.1').run(spec('object.1'), duplicate)).toEqual({ kind: 'blocked' })
  expect(duplicate).not.toHaveBeenCalled(); await Promise.resolve(); expect(send).toHaveBeenCalledTimes(2)
  release(); expect((await first).kind).toBe('success'); expect((await second).kind).toBe('success')
  expect(pool.busy('object.1')).toBe(false); scope.stop()
})
it('disposes row listeners and cancels an unsent reservation without dispatching delayed confirmation', async () => {
  const registry = new OperationRegistry(() => owner, () => localStorage), scope = effectScope(), pool = scope.run(() => useSubmissionPool(registry))!
  let confirm!: () => void; const confirmation = new Promise<void>(resolve => { confirm = resolve }), send = vi.fn(async () => true)
  const result = pool.get('object.1').run(spec('object.1'), async () => { await confirmation; return send })
  scope.stop(); expect(registry.list()).toEqual([]); confirm()
  expect(await result).toEqual({ kind: 'cancelled' }); expect(send).not.toHaveBeenCalled()
})
