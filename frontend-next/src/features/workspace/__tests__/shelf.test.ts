import { beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { OperationRegistry } from '@/lib/operation-registry'
import { reactive, nextTick } from 'vue'
import { useShelf, type ShelfBoard } from '../shelf'
const mocks = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), del: vi.fn() }))
vi.mock('@/api/http', async original => ({ ...await original<typeof import('@/api/http')>(), get: mocks.get, post: mocks.post, put: mocks.put, remove: mocks.del }))
let registry: OperationRegistry
vi.mock('@/lib/operations', () => ({ useOperations: () => ({ registry }) }))
const auth = reactive<{ user: { id: string; portal: string; locked?: boolean } | null }>({ user: null })
vi.mock('@/stores/auth', () => ({ useAuth: () => auth }))
const board = (version = '0'): ShelfBoard => ({ scope: '1.admin.1', version, mode: 'drawer', bookmarks: [], targets: [{ id: '0', title: '', path: '/', icon: null }] })
beforeEach(() => { localStorage.clear(); registry = new OperationRegistry(() => auth.user && !auth.user.locked ? { scope: ['https://test/api', '1', auth.user.portal, auth.user.id], session: sessionStorage.getItem('sma.access-token')! } : null, () => localStorage); setActivePinia(createPinia()); vi.resetAllMocks(); auth.user = { id: '1', portal: 'admin' }; sessionStorage.setItem('sma.access-token', 'owner-one') })
it('discards a late response and private state after owner changes', async () => {
  let finish!: (value: ShelfBoard) => void
  mocks.get.mockImplementation(() => new Promise(resolve => { finish = resolve }))
  const shelf = useShelf(), pending = shelf.load()
  auth.user = { id: '2', portal: 'admin' }; await nextTick()
  finish(board('9')); await pending
  expect(shelf.ready).toBe(false); expect(shelf.board.scope).toBe('')
})
it('locks clear cached targets and prevent requests', async () => {
  mocks.get.mockResolvedValue(board())
  const shelf = useShelf(); await shelf.load(); shelf.open = true
  auth.user!.locked = true
  expect(shelf.open).toBe(false); expect(shelf.board.targets).toEqual([])
  expect(await shelf.add('0')).toBe(false); expect(mocks.post).not.toHaveBeenCalled()
})
it('failed writes reconcile by reading without retrying or claiming success', async () => {
  mocks.get.mockResolvedValue(board('2')); mocks.put.mockRejectedValue(new Error('conflict'))
  const shelf = useShelf(); await shelf.load()
  expect(await shelf.rename('44', 'My label')).toBe(false)
  expect(mocks.put).toHaveBeenCalledTimes(1); expect(shelf.unresolved).toBe(true); expect(shelf.error).toBeTruthy(); expect(shelf.board.version).toBe('2')
})
it('preserves long versions as strings and ignores delayed reads after a write', async () => {
  const shelf = useShelf(); mocks.get.mockResolvedValue(board('9007199254740993999')); await shelf.load()
  let finish!: (value: ShelfBoard) => void
  mocks.get.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
  const oldRead = shelf.load()
  mocks.put.mockResolvedValue(board('9007199254740994000')); mocks.get.mockResolvedValue(board('9007199254740994000'))
  await shelf.mode('floating')
  finish(board('9007199254740993999')); await oldRead
  expect(shelf.board.version).toBe('9007199254740994000')
  expect(mocks.put).toHaveBeenCalledWith('/workspace/shelf/mode', { version: '9007199254740993999', mode: 'floating' }, expect.objectContaining({ idempotencyKey: expect.any(String) }))
})
