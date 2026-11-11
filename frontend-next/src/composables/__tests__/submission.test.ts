import { effectScope } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import { OperationRegistry } from '@/lib/operation-registry'
import { useSubmission } from '../useSubmission'
import { ApiError } from '@/api/http'
const spec = { operation: 'users.save', resource: 'iam.users.1', protocol: 'LOCAL' as const }
const owner = { scope: ['https://site/api', '1', 'admin', '1'] as const, session: 'session' }
beforeEach(() => localStorage.clear())
it('reserves before asynchronous validation and a second submit cannot start another preparation', async () => {
  const registry = new OperationRegistry(() => owner, () => localStorage), scope = effectScope(), submit = scope.run(() => useSubmission(registry))!
  const send = vi.fn(async () => 'saved'); let validated!: () => void
  const preparation = new Promise<void>(resolve => { validated = resolve })
  const first = submit.run(spec, async () => { await preparation; return send })
  expect(submit.busy.value).toBe(true)
  const duplicate = vi.fn(() => send)
  expect(await submit.run(spec, duplicate)).toEqual({ kind: 'blocked' }); expect(duplicate).not.toHaveBeenCalled()
  validated(); expect(await first).toEqual({ kind: 'success', value: 'saved' }); expect(send).toHaveBeenCalledOnce(); scope.stop()
})
it('cancels unsubmitted work after declined confirmation or disposal during validation', async () => {
  const registry = new OperationRegistry(() => owner, () => localStorage), scope = effectScope(), submit = scope.run(() => useSubmission(registry))!
  expect(await submit.run(spec, async () => undefined)).toEqual({ kind: 'cancelled' }); expect(registry.list()).toEqual([])
  let ready!: () => void; const validation = new Promise<void>(resolve => { ready = resolve }), send = vi.fn(async () => 1)
  const pending = submit.run(spec, async () => { await validation; return send }); scope.stop(); ready()
  expect(await pending).toEqual({ kind: 'cancelled' }); expect(send).not.toHaveBeenCalled(); expect(registry.list()).toEqual([])
})
it('preserves an unknown intent when a new handler tries to submit that resource', async () => {
  const registry = new OperationRegistry(() => owner, () => localStorage), scope = effectScope(), submit = scope.run(() => useSubmission(registry))!
  await submit.run(spec, () => async () => { throw new ApiError(0, null, 'Disconnected') })
  expect(submit.unresolved.value).toBe(true)
  const next = scope.run(() => useSubmission(registry))!, send = vi.fn(async () => 1)
  expect(await next.run(spec, () => send)).toEqual({ kind: 'blocked' }); expect(send).not.toHaveBeenCalled()
  expect(next.unresolved.value).toBe(true); expect(next.key.value).toBe(submit.key.value); scope.stop()
})
it('binds a reopened form to unresolved target metadata before accepting any new input', async () => {
  const registry = new OperationRegistry(() => owner, () => localStorage), scope = effectScope(), original = scope.run(() => useSubmission(registry))!
  await original.run(spec, () => async () => { throw new ApiError(0, null, 'Disconnected') })
  const reopened = scope.run(() => useSubmission(registry))!
  reopened.bind(spec.resource)
  expect(reopened.unresolved.value).toBe(true); expect(reopened.record.value?.key).toBe(original.key.value)
  reopened.resetView(); reopened.bind('iam.users.2'); expect(reopened.unresolved.value).toBe(false)
  scope.stop()
})
