import { effectScope, ref } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import { OperationRegistry, type OperationOwner } from '@/lib/operation-registry'
import { useFileUpload } from '../useFileUpload'
import { upload, lookupUpload, uploadedAsset, type Asset, type UploadReceipt } from '@/features/file/files'

vi.mock('@/features/file/files', async original => ({ ...await original<object>(), upload: vi.fn(), lookupUpload: vi.fn(), uploadedAsset: vi.fn() }))
const taskId = '11111111-1111-4111-8111-111111111111'
const asset = { id: '42', name: 'original.png' } as Asset
const receipt = (state: UploadReceipt['state'] = 'ACCEPTED'): UploadReceipt => ({ uploadId: taskId, state, resumable: state === 'ACCEPTED', fileId: state === 'SUCCEEDED' ? asset.id : null })
let owner: OperationOwner
beforeEach(() => {
  localStorage.clear(); vi.resetAllMocks()
  owner = { scope: ['https://site/api', '1', 'admin', '1'], session: 'session' }
  vi.mocked(upload).mockRejectedValue(new Error('lost response'))
  vi.mocked(lookupUpload).mockResolvedValue(receipt())
  vi.mocked(uploadedAsset).mockResolvedValue(asset)
})
function setup(context = ref('file.avatar.self'), registry = new OperationRegistry(() => owner, () => localStorage)) {
  const scope = effectScope(), task = scope.run(() => useFileUpload(() => context.value, () => true, registry))!
  return { task, scope, context, registry }
}
it('resumes only after querying ACCEPTED, with the exact in-memory File and original key, once per click burst', async () => {
  const { task, scope } = setup(), file = new File(['original'], 'original.png', { type: 'image/png' })
  await task.send(file); const key = task.action.record.value!.key
  expect(task.canResume.value).toBe(false)
  await task.check(); expect(task.canResume.value).toBe(true)
  vi.mocked(upload).mockResolvedValue(asset)
  const results = await Promise.all(Array.from({ length: 20 }, () => task.resume()))
  expect(results.filter(Boolean)).toEqual([asset]); expect(upload).toHaveBeenCalledTimes(2)
  expect(lookupUpload).toHaveBeenCalledTimes(2)
  for (const call of vi.mocked(upload).mock.calls) { expect(call[0]).toBe(file); expect(call[2]?.idempotencyKey).toBe(key) }
  expect(localStorage.length).toBe(0); scope.stop()
})
it.each(['RUNNING', 'UNKNOWN'] as const)('rechecks authoritative %s state before continuing and never resends bytes', async state => {
  const { task, scope } = setup()
  await task.send(new File(['original'], 'original.png')); await task.check()
  vi.mocked(lookupUpload).mockResolvedValue(receipt(state))
  await task.resume()
  expect(upload).toHaveBeenCalledOnce(); expect(task.action.unresolved.value).toBe(true); expect(task.canResume.value).toBe(false)
  scope.stop()
})
it('reads an already completed file through its current ACL without another multipart request', async () => {
  const { task, scope } = setup()
  await task.send(new File(['original'], 'original.png')); await task.check()
  vi.mocked(lookupUpload).mockResolvedValue(receipt('SUCCEEDED'))
  expect(await task.resume()).toEqual(asset)
  expect(upload).toHaveBeenCalledOnce(); expect(uploadedAsset).toHaveBeenCalledWith('42'); scope.stop()
})
it('isolates form and brand slot contexts and discards late results after switching them', async () => {
  const { task, scope, context, registry } = setup(ref('file.brand.LOGO.1'))
  let finish!: (value: Asset) => void
  vi.mocked(upload).mockImplementation(() => new Promise(resolve => { finish = resolve }))
  const pending = task.send(new File(['original'], 'original.png')); await Promise.resolve()
  context.value = 'file.brand.BACKGROUND.1'; finish(asset)
  expect(await pending).toBeUndefined(); expect(task.action.unresolved.value).toBe(false)
  expect(registry.list()).toEqual([]); scope.stop()
})
it('reloads only metadata, cannot reconstruct an original File, and never binds another context', async () => {
  const first = setup(); await first.task.send(new File(['private-file-bytes'], 'private-name.png')); first.scope.stop()
  expect(JSON.stringify({ ...localStorage })).not.toMatch(/private-file-bytes|private-name.png|session/)
  const restored = setup(); await restored.task.check()
  expect(restored.task.action.unresolved.value).toBe(true); expect(restored.task.canResume.value).toBe(false)
  await restored.task.resume(); expect(upload).toHaveBeenCalledOnce()
  const other = setup(ref('file.library.upload')); expect(other.task.action.unresolved.value).toBe(false)
  restored.scope.stop(); other.scope.stop()
})
it('locking clears the retained file and makes a pending result ineligible for attachment', async () => {
  const { task, scope, registry } = setup(); await task.send(new File(['original'], 'original.png')); await task.check()
  window.dispatchEvent(new Event('sma:locked')); owner = { ...owner, session: 'new-session' }; registry.invalidate()
  expect(task.canResume.value).toBe(false); await task.resume(); expect(upload).toHaveBeenCalledOnce(); scope.stop()
})
