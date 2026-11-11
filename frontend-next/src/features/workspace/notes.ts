import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { get, post, put } from '@/api/http'
import { useSubmissionPool } from '@/composables/useSubmissionPool'
import { useAuth } from '@/stores/auth'
import { getToken } from '@/lib/token'
import { i18n } from '@/locales'
import { useShelf, type ShelfTarget } from './shelf'
export interface NoteItem { id: string; text: string; done: boolean }
export interface NoteInput { title: string; kind: 'text' | 'checklist'; content: string; items: NoteItem[]; color: 'neutral' | 'purple' | 'green' | 'amber'; pinned: boolean; relatedMenuId: string | null }
export interface PersonalNote extends NoteInput { id: string; requestKey: string; version: string; status: 'active' | 'archived' | 'trash'; trashedAt: string | null; updatedAt: string; relatedTarget: ShelfTarget | null }
export interface NoteDraft { input: NoteInput; server: PersonalNote | null; baseline: string; saving: boolean; transitioning?: boolean; validationError?: boolean; error: string; conflict: PersonalNote | null; uncertain: boolean }
export function noteInput(n: NoteInput): NoteInput { return { title: n.title, kind: n.kind, content: n.content, items: n.items.map(i => ({ ...i })), color: n.color, pinned: n.pinned, relatedMenuId: n.relatedMenuId } }
export function noteValid(n: NoteInput) { return n.title.trim().length > 0 && n.title.length <= 50 && n.content.length <= 10000 && n.items.length <= 100 && n.items.every(i => !!i.text.trim() && i.text.length <= 500) }
const stringify = (n: NoteInput) => JSON.stringify(noteInput(n))
const endpoint = '/workspace/shelf/notes'
export const useShelfNotes = defineStore('personal-shelf-notes', () => {
  const auth = useAuth(), shelf = useShelf(), actions = useSubmissionPool()
  const entries = ref<Record<string, NoteDraft>>({}), loading = ref(false), loaded = ref(false), error = ref(''), selected = ref('')
  function operation(key: string) { return actions.get('workspace.shelf.note.' + key) }
  const pending = new Map<string, Promise<boolean>>(), timers = new Map<string, ReturnType<typeof setTimeout>>()
  let generation = 0, reads = 0
  const dirty = computed(() => Object.values(entries.value).some(d => d.saving || d.uncertain || stringify(d.input) !== d.baseline))
  const list = computed(() => Object.entries(entries.value).sort(([, a], [, b]) => Number(b.input.pinned) - Number(a.input.pinned) || (b.server?.updatedAt || '').localeCompare(a.server?.updatedAt || '')))
  function alive(g: number, token: string | null) { return g === generation && getToken() === token && !!auth.user && !auth.user.locked }
  function clearTimer(key: string) { const timer = timers.get(key); if (timer) clearTimeout(timer); timers.delete(key) }
  function reset() { generation++; reads++; timers.forEach(clearTimeout); timers.clear(); pending.clear(); entries.value = {}; selected.value = ''; loaded.value = false; loading.value = false; error.value = '' }
  watch(() => shelf.identity, reset, { flush: 'sync' })
  watch(() => auth.user?.locked, locked => {
    if (locked) { generation++; reads++; timers.forEach(clearTimeout); timers.clear(); pending.clear(); loading.value = false; Object.values(entries.value).forEach(d => { if (d.saving) { d.uncertain = true; d.error = i18n.global.t('common.shelf.checkSave') }; d.saving = false; d.transitioning = false }) }
  }, { flush: 'sync' })
  function accept(n: PersonalNote) {
    const old = entries.value[n.requestKey]
    if (old?.server && BigInt(n.version) < BigInt(old.server.version)) return
    if (old && (old.saving || old.uncertain || operation(n.requestKey).unresolved.value || stringify(old.input) !== old.baseline)) {
      if (old.server && old.server.version !== n.version) old.conflict = n
      return
    }
    entries.value[n.requestKey] = { input: noteInput(n), server: n, baseline: stringify(n), saving: false, error: '', conflict: null, uncertain: operation(n.requestKey).unresolved.value }
  }
  async function load() {
    if (!auth.user || auth.user.locked) return false
    const g = generation, token = getToken(), request = ++reads
    loading.value = true; error.value = ''
    try {
      const rows = await get<PersonalNote[]>(endpoint)
      if (!alive(g, token) || request !== reads) return false
      rows.forEach(accept)
      const present = new Set(rows.map(n => n.requestKey))
      for (const [key, draft] of Object.entries(entries.value)) if (draft.server && !present.has(key) && !draft.saving && !draft.uncertain && !operation(key).unresolved.value && stringify(draft.input) === draft.baseline) delete entries.value[key]
      loaded.value = true; return true
    } catch (cause) { if (alive(g, token)) error.value = (cause as Error).message; return false }
    finally { if (alive(g, token) && request === reads) loading.value = false }
  }
  function edit(key: string, input: NoteInput) {
    const d = entries.value[key]; if (!d || d.transitioning || auth.user?.locked || d.server && d.server.status !== 'active') return
    d.input = noteInput(input); clearTimer(key)
    if (d.validationError && noteValid(d.input)) { d.validationError = false; d.error = '' }
    if (!d.error && !d.conflict && !d.uncertain) timers.set(key, setTimeout(() => { void save(key) }, 1000))
  }
  function create(kind: NoteInput['kind'], relatedMenuId: string | null = null) {
    const key = crypto.randomUUID()
    const input: NoteInput = { title: i18n.global.t('common.shelf.untitled'), kind, content: '', items: [], color: 'neutral', pinned: false, relatedMenuId }
    entries.value[key] = { input, server: null, baseline: '', saving: false, error: '', conflict: null, uncertain: false }
    selected.value = key; shelf.section = 'notes'; edit(key, input); return key
  }
  async function fetchCurrent(key: string, draft: NoteDraft) {
    return draft.server ? get<PersonalNote>(endpoint + '/' + draft.server.id) : (await get<PersonalNote[]>(endpoint)).find(n => n.requestKey === key)
  }
  async function reconcile(key: string) {
    const d = entries.value[key]; if (!d || !auth.user || auth.user.locked) return false
    const action = operation(key), g = generation, token = getToken()
    d.saving = true
    try {
      if (action.unresolved.value) {
        const state = await action.check()
        if (!alive(g, token)) return false
        if (state !== 'SUCCEEDED' && state !== 'FAILED_FINAL') { d.error = action.error.value; d.uncertain = true; return false }
        if (state === 'FAILED_FINAL') { d.uncertain = false; d.error = action.error.value; return false }
      }
      const remote = await fetchCurrent(key, d)
      if (!alive(g, token)) return false
      // A missing object never proves that an older write was not accepted.
      if (!remote) { d.uncertain = true; d.error = i18n.global.t('common.operation.notFound'); return false }
      if (stringify(remote) === stringify(d.input)) {
        d.server = remote; d.baseline = stringify(remote); d.uncertain = false; d.error = ''; return true
      }
      d.conflict = remote; d.uncertain = false; d.error = ''; return false
    } catch (cause) { if (alive(g, token)) { d.error = (cause as Error).message; d.uncertain = true }; return false }
    finally { if (alive(g, token)) d.saving = false }
  }
  async function performSave(key: string, manual: boolean) {
    const d = entries.value[key]
    if (!d || !auth.user || auth.user.locked) return false
    const action = operation(key)
    if (action.unresolved.value || d.uncertain) return manual ? reconcile(key) : false
    if (d.conflict || (!manual && d.error)) return false
    if (!noteValid(d.input)) { d.validationError = true; d.error = i18n.global.t('common.shelf.invalidNote'); return false }
    d.validationError = false
    if (stringify(d.input) === d.baseline) return true
    const g = generation, token = getToken(), snapshot = noteInput(d.input), sent = stringify(snapshot)
    const id = d.server?.id, version = d.server?.version
    d.saving = true; d.error = ''
    try {
      const result = await action.run({ operation: id ? 'shelf.note-update' : 'shelf.note-create', resource: 'workspace.shelf.note.' + key, protocol: 'LOCAL' },
        () => options => id ? put<PersonalNote>(endpoint + '/' + id, { version, note: snapshot }, options) : post<PersonalNote>(endpoint, { requestKey: key, note: snapshot }, options))
      if (!alive(g, token)) return false
      if (result.kind === 'success' || result.kind === 'completed') {
        const remote = result.kind === 'success' ? result.value : await fetchCurrent(key, d)
        if (!alive(g, token)) return false
        if (!remote) { d.uncertain = true; d.error = i18n.global.t('common.operation.notFound'); return false }
        d.server = remote; d.baseline = stringify(remote); d.uncertain = false
        if (stringify(d.input) === sent) d.input = noteInput(remote)
        return true
      }
      d.error = action.error.value; d.uncertain = action.unresolved.value
      if (d.uncertain) return false
      if (result.kind === 'rejected' || result.kind === 'failed') {
        try {
          const remote = await fetchCurrent(key, d)
          if (alive(g, token) && remote && (!d.server || remote.version !== d.server.version)) d.conflict = remote
        } catch { /* Preserve the draft and the original failure. */ }
      }
      return false
    } catch (cause) { if (alive(g, token)) { d.error = (cause as Error).message; d.uncertain = true }; return false }
    finally {
      if (alive(g, token)) { d.saving = false; if (!d.error && !d.conflict && !d.uncertain && stringify(d.input) !== d.baseline) timers.set(key, setTimeout(() => { void save(key) }, 1000)) }
    }
  }
  async function save(key: string, manual = false): Promise<boolean> {
    clearTimer(key)
    const ongoing = pending.get(key)
    if (ongoing) { const ok = await ongoing; return ok && entries.value[key] ? save(key, manual) : false }
    const operation = performSave(key, manual); pending.set(key, operation)
    try { return await operation } finally { if (pending.get(key) === operation) pending.delete(key) }
  }
  async function flushAll() {
    for (const key of Object.keys(entries.value)) if (!await save(key, true) || entries.value[key] && stringify(entries.value[key].input) !== entries.value[key].baseline) { selected.value = key; shelf.section = 'notes'; shelf.open = true; return false }
    return true
  }
  function useRemote(key: string) {
    const d = entries.value[key], remote = d?.conflict
    if (!remote) return
    clearTimer(key); entries.value[key] = { input: noteInput(remote), server: remote, baseline: stringify(remote), saving: false, error: '', conflict: null, uncertain: false }
  }
  function copyDraft(key: string) {
    const d = entries.value[key]; if (!d) return
    const input = noteInput(d.input), newKey = create(input.kind, input.relatedMenuId)
    edit(newKey, input); if (d.conflict) useRemote(key)
    return newKey
  }
  async function transition(key: string, status: PersonalNote['status']) {
    const d = entries.value[key]; if (!d || d.saving || d.transitioning || !auth.user || auth.user.locked) return false
    if (d.uncertain || operation(key).unresolved.value) { await save(key, true); return false }
    // Own the transition before awaiting the preceding autosave.
    const g = generation, token = getToken(); d.transitioning = true
    try {
      if (!await save(key, true) || !alive(g, token) || !d.server || stringify(d.input) !== d.baseline) return false
      const id = d.server.id, version = d.server.version, action = operation(key)
      d.saving = true; d.error = ''
      const result = await action.run({ operation: 'shelf.note-status', resource: 'workspace.shelf.note.' + key, protocol: 'LOCAL' },
        () => options => put<PersonalNote>(endpoint + '/' + id + '/status', { version, status }, options))
      if (!alive(g, token)) return false
      if (result.kind === 'success' || result.kind === 'completed') {
        const remote = result.kind === 'success' ? result.value : await get<PersonalNote>(endpoint + '/' + id)
        if (!alive(g, token)) return false
        d.server = remote; d.baseline = stringify(remote); d.input = noteInput(remote); d.uncertain = false; return true
      }
      d.error = action.error.value; d.uncertain = action.unresolved.value
      if (!d.uncertain && (result.kind === 'rejected' || result.kind === 'failed')) {
        try { const remote = await get<PersonalNote>(endpoint + '/' + id); if (alive(g, token) && remote.version !== version) d.conflict = remote } catch { /* Preserve local input. */ }
      }
      return false
    } catch (cause) { if (alive(g, token)) { d.error = (cause as Error).message; d.uncertain = true }; return false }
    finally { if (alive(g, token)) { d.saving = false; d.transitioning = false } }
  }
  return { entries, loading, loaded, error, selected, dirty, list, load, reset, create, edit, save, flushAll, useRemote, copyDraft, transition, operation, reconcile }
})
