import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { OperationRegistry } from '@/lib/operation-registry'
import { reactive } from 'vue'
import { useShelfNotes, type PersonalNote, type NoteInput } from '../notes'
import { ApiError } from '@/api/http'
const mocks = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('@/api/http', async original => ({ ...await original<typeof import('@/api/http')>(), get: mocks.get, post: mocks.post, put: mocks.put }))
let registry: OperationRegistry
vi.mock('@/lib/operations', () => ({ useOperations: () => ({ registry }) }))
const auth = reactive<{ user: { id: string; portal: string; locked?: boolean } | null }>({ user: null })
vi.mock('@/stores/auth', () => ({ useAuth: () => auth }))
function remote(key: string, input: NoteInput, version = '1'): PersonalNote { return { ...input, id: '9007199254740993333', requestKey: key, version, status: 'active', trashedAt: null, updatedAt: '2026-11-11T00:00:00Z', relatedTarget: null } }
beforeEach(() => { localStorage.clear(); registry = new OperationRegistry(() => auth.user && !auth.user.locked ? { scope: ['https://test/api', '1', auth.user.portal, auth.user.id], session: sessionStorage.getItem('sma.access-token')! } : null, () => localStorage); vi.useFakeTimers(); vi.resetAllMocks(); setActivePinia(createPinia()); auth.user = { id: '1', portal: 'admin' }; sessionStorage.setItem('sma.access-token', 'one') })
afterEach(() => { useShelfNotes().reset(); vi.useRealTimers() })
it('preserves input typed while a save is pending and saves the later revision', async () => {
  const notes = useShelfNotes(), key = notes.create('text')
  let finish!: (value: PersonalNote) => void
  mocks.post.mockImplementation(() => new Promise(resolve => { finish = resolve }))
  const initial = { ...notes.entries[key]!.input }, writing = notes.save(key)
  notes.edit(key, { ...initial, content: 'typed during save' })
  await Promise.resolve(); await Promise.resolve(); finish(remote(key, initial)); await writing
  expect(notes.entries[key]!.input.content).toBe('typed during save')
  expect(notes.dirty).toBe(true)
  mocks.put.mockImplementation((_url, body) => Promise.resolve(remote(key, body.note, '2')))
  await vi.advanceTimersByTimeAsync(1000)
  expect(notes.dirty).toBe(false); expect(mocks.put).toHaveBeenCalledTimes(1)
})
it('reconciles a lost create response by request key without a duplicate write', async () => {
  const notes = useShelfNotes(), key = notes.create('text'), input = notes.entries[key]!.input
  mocks.post.mockRejectedValue(new ApiError(0, null, 'network'))
  mocks.get.mockResolvedValue([remote(key, input)])
  expect(await notes.save(key)).toBe(false)
  expect(notes.entries[key]!.uncertain).toBe(true)
  const intent = registry.list()[0]!
  mocks.post.mockImplementation((url, body) => { expect(url).toBe('/common/requests/lookup'); expect(body).toEqual({ key: intent.key }); return Promise.resolve({ operationId: 'saved-note', operation: 'shelf.note-create', state: 'SUCCEEDED', createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:01Z' }) })
  expect(await notes.save(key, true)).toBe(true)
  expect(notes.dirty).toBe(false); expect(mocks.post.mock.calls.filter(call => call[0] === '/workspace/shelf/notes')).toHaveLength(1)
})
it('failed autosave preserves text, stops retries and blocks closing until resolved', async () => {
  const notes = useShelfNotes(), key = notes.create('text')
  notes.edit(key, { ...notes.entries[key]!.input, content: 'keep this draft' })
  mocks.post.mockRejectedValue(new ApiError(0, null, 'offline')); mocks.get.mockRejectedValue(new Error('offline'))
  await vi.advanceTimersByTimeAsync(1000)
  await vi.advanceTimersByTimeAsync(10000)
  expect(mocks.post).toHaveBeenCalledTimes(1); expect(notes.entries[key]!.input.content).toBe('keep this draft')
  expect(await notes.flushAll()).toBe(false); expect(mocks.post.mock.calls.filter(call => call[0] === '/workspace/shelf/notes')).toHaveLength(1)
})
it('conflicts preserve local edits and allow a separate copy without overwriting remote', async () => {
  const notes = useShelfNotes(), key = notes.create('text'), original = { ...notes.entries[key]!.input }
  mocks.post.mockResolvedValue(remote(key, original)); await notes.save(key)
  notes.edit(key, { ...original, content: 'local' })
  mocks.put.mockRejectedValue(new ApiError(409, null, 'conflict'))
  mocks.get.mockResolvedValue(remote(key, { ...original, content: 'remote' }, '2'))
  expect(await notes.save(key)).toBe(false); expect(notes.entries[key]!.input.content).toBe('local')
  const copy = notes.copyDraft(key)!
  expect(notes.entries[copy]!.input.content).toBe('local'); expect(notes.entries[key]!.input.content).toBe('remote')
  expect(notes.entries[copy]!.server).toBeNull()
})
it('account changes discard drafts and late responses, while lock hides and freezes saves', async () => {
  const notes = useShelfNotes(), key = notes.create('text'), input = notes.entries[key]!.input
  let finish!: (value: PersonalNote) => void
  mocks.post.mockImplementation(() => new Promise(resolve => { finish = resolve }))
  const operation = notes.save(key)
  await Promise.resolve(); await Promise.resolve()
  auth.user = { id: '2', portal: 'admin' }
  finish(remote(key, input)); await operation
  expect(Object.keys(notes.entries)).toEqual([])
  const second = notes.create('text'); auth.user.locked = true
  await vi.advanceTimersByTimeAsync(2000)
  expect(await notes.save(second)).toBe(false); expect(mocks.post).toHaveBeenCalledTimes(1)
})
it('resumes autosave when a temporarily empty checklist item is corrected', async () => {
  const notes = useShelfNotes(), key = notes.create('checklist')
  const input = { ...notes.entries[key]!.input, items: [{ id: 'item', text: '', done: false }] }
  notes.edit(key, input)
  await vi.advanceTimersByTimeAsync(1000)
  expect(notes.entries[key]!.validationError).toBe(true)
  expect(mocks.post).not.toHaveBeenCalled()
  mocks.post.mockImplementation((_url, body) => Promise.resolve(remote(key, body.note)))
  notes.edit(key, { ...input, items: [{ id: 'item', text: 'Completed input', done: false }] })
  await vi.advanceTimersByTimeAsync(1000)
  expect(notes.entries[key]!.error).toBe('')
  expect(notes.dirty).toBe(false)
  expect(mocks.post).toHaveBeenCalledTimes(1)
})

it('does not recreate an unknown note when neither lookup nor content search finds its result', async () => {
  const notes = useShelfNotes(), key = notes.create('text')
  notes.edit(key, { ...notes.entries[key]!.input, content: 'private uncertain text' })
  mocks.post.mockRejectedValue(new ApiError(0, null, 'disconnected')); mocks.get.mockResolvedValue([])
  expect(await notes.save(key)).toBe(false)
  const original = registry.list()[0]!
  expect(JSON.stringify({ ...localStorage })).not.toContain('private uncertain text')
  mocks.post.mockImplementation((url, body) => {
    expect(url).toBe('/common/requests/lookup'); expect(body).toEqual({ key: original.key })
    return Promise.reject(new ApiError(404, null, 'not found'))
  })
  expect(await notes.save(key, true)).toBe(false)
  await vi.advanceTimersByTimeAsync(10000)
  expect(mocks.post.mock.calls.filter(call => call[0] === '/workspace/shelf/notes')).toHaveLength(1)
  expect(notes.entries[key]!.uncertain).toBe(true); expect(registry.list()[0]!.key).toBe(original.key)
})

it('serializes concurrent archive and trash requests before awaiting a dirty save', async () => {
  const notes = useShelfNotes(), key = notes.create('text'), input = notes.entries[key]!.input
  let finish!: (value: PersonalNote) => void
  mocks.post.mockImplementation(() => new Promise(resolve => { finish = resolve }))
  mocks.put.mockImplementation((_url, body) => Promise.resolve({ ...remote(key, input, '2'), status: body.status }))
  const archive = notes.transition(key, 'archived')
  expect(await notes.transition(key, 'trash')).toBe(false)
  await Promise.resolve(); await Promise.resolve(); finish(remote(key, input))
  expect(await archive).toBe(true)
  expect(mocks.put).toHaveBeenCalledTimes(1)
  expect(mocks.put).toHaveBeenCalledWith('/workspace/shelf/notes/9007199254740993333/status', { version: '1', status: 'archived' }, expect.objectContaining({ idempotencyKey: expect.any(String) }))
})
