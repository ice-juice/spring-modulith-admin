import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { get, post, put, remove, type RequestOptions } from '@/api/http'
import { useSubmission } from '@/composables/useSubmission'
import { useAuth } from '@/stores/auth'
import { getToken } from '@/lib/token'
import { i18n } from '@/locales'
import { safeLocalPath } from '@/lib/web-addresses'

export interface ShelfTarget { id: string; title: string; path: string; icon: string | null }
export interface Bookmark { id: string; menuId: string; alias: string; position: number; target: ShelfTarget | null }
export interface ShelfBoard { scope: string; mode: 'drawer' | 'floating' | 'docked'; version: string; bookmarks: Bookmark[]; targets: ShelfTarget[] }
const emptyBoard = (): ShelfBoard => ({ scope: '', mode: 'drawer', version: '0', bookmarks: [], targets: [] })
export const useShelf = defineStore('personal-shelf', () => {
  const auth = useAuth(), submission = useSubmission()
  submission.bind('workspace.shelf.board')
  const board = ref<ShelfBoard>(emptyBoard()), ready = ref(false), loading = ref(false), busy = submission.busy
  const open = ref(false), error = ref(''), section = ref<'bookmarks' | 'notes'>('bookmarks')
  let generation = 0, readSequence = 0
  const identity = computed(() => auth.user ? auth.user.portal + '.' + auth.user.id + '.' + getToken() : '')
  function current(g: number, token: string | null) { return g === generation && token === getToken() && !!auth.user && !auth.user.locked }
  function reset() { generation++; readSequence++; board.value = emptyBoard(); ready.value = false; error.value = ''; loading.value = false; open.value = false; section.value = 'bookmarks' }
  watch(identity, reset, { flush: 'sync' })
  watch(() => auth.user?.locked, locked => { if (locked) { generation++; readSequence++; open.value = false; loading.value = false; ready.value = false; board.value = emptyBoard() } }, { flush: 'sync' })
  async function load() {
    if (!auth.user || auth.user.locked) return false
    const g = generation, token = getToken(), sequence = ++readSequence
    loading.value = true; error.value = ''
    try {
      const result = await get<ShelfBoard>('/workspace/shelf')
      if (!current(g, token) || sequence !== readSequence) return false
      board.value = result; ready.value = true
      return true
    } catch (cause) { if (current(g, token) && sequence === readSequence) error.value = (cause as Error).message; return false }
    finally { if (current(g, token) && sequence === readSequence) loading.value = false }
  }
  async function change(operation: string, prepare: () => (options: RequestOptions) => Promise<unknown>) {
    if (!auth.user || auth.user.locked) return false
    const g = generation, token = getToken()
    error.value = ''; readSequence++
    const result = await submission.run({ operation: 'shelf.' + operation, resource: 'workspace.shelf.board', protocol: 'LOCAL' }, async () => {
      if (!ready.value && !await load()) return
      return prepare()
    })
    if (!current(g, token)) return false
    // Even a normal response is reread: the common completion receipt is not a Board.
    if (result.kind === 'success' || result.kind === 'completed') { readSequence++; return load() }
    const message = submission.error.value
    if (result.kind !== 'cancelled' && result.kind !== 'stale') await load()
    if (current(g, token)) error.value = message || error.value
    return false
  }
  async function check() {
    const g = generation, token = getToken(), state = await submission.check()
    if (!current(g, token)) return false
    if (state === 'SUCCEEDED') return load()
    error.value = submission.error.value; return false
  }
  async function show() { open.value = true; await load() }
  function add(menuId: string, alias = '') { return change('bookmark-add', () => options => post('/workspace/shelf/bookmarks', { menuId, alias }, options)) }
  function rename(id: string, alias: string) { return change('bookmark-edit', () => { const version = board.value.version; return options => put('/workspace/shelf/bookmarks/' + id, { version, alias }, options) }) }
  function removeBookmark(id: string) { return change('bookmark-remove', () => { const version = board.value.version; return options => remove('/workspace/shelf/bookmarks/' + id, { ...options, params: { version } }) }) }
  function reorder(ids: string[]) { const snapshot = [...ids]; return change('bookmark-order', () => { const version = board.value.version; return options => put('/workspace/shelf/bookmarks/order', { version, ids: snapshot }, options) }) }
  function mode(value: ShelfBoard['mode']) { return change('mode', () => { const version = board.value.version; return options => put('/workspace/shelf/mode', { version, mode: value }, options) }) }
  function target(path: string) { return board.value.targets.find(t => t.path === path.split(/[?#]/)[0] && safeLocalPath(t.path)) }
  function label(item: Bookmark) { return item.alias || targetLabel(item.target) }
  function targetLabel(item: ShelfTarget | null) { return !item ? i18n.global.t('common.shelf.unavailable') : item.id === '0' ? i18n.global.t('common.home') : item.title }
  return { board, ready, loading, busy, open, error, section, identity, reset, load, show, add, rename, remove: removeBookmark, reorder, mode, check, slow: submission.slow, unresolved: submission.unresolved, querying: submission.querying, target, label, targetLabel }
})
