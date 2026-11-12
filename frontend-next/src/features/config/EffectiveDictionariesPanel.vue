<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuth } from '@/stores/auth'
import { confirmAction, notify } from '@/lib/feedback'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import EditorDialog from '@/components/business/EditorDialog.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import EmptyState from '@/components/business/EmptyState.vue'
import StatusBadge from '@/components/business/StatusBadge.vue'
import OperationStatus from '@/components/business/OperationStatus.vue'
import { useSubmission } from '@/composables/useSubmission'
import { useDirectorySession } from '@/features/tenant/useDirectorySession'
import { layerEntry, layers, parentChoices, type DictionaryEntry, type DictionarySummary, type EffectiveDictionary, type Label } from './layers'

type Editor = { mode: 'override'; code: string; text: string; zh: string; en: string; sort: string; hidden: '' | 'true' | 'false' }
  | { mode: 'private'; existing: boolean; code: string; parentCode: string; text: string; zh: string; en: string; sort: string; enabled: boolean }
const { t, locale } = useI18n(), auth = useAuth(), submission = useSubmission()
const summaries = ref<DictionarySummary[]>([]), keyword = ref(''), selectedKey = ref(''), view = ref<EffectiveDictionary>(), page = ref(1), total = ref(0)
const loading = ref(false), error = ref(''), open = ref(false), editor = ref<Editor>()
let attempt = 0
function clear() { attempt++; summaries.value = []; selectedKey.value = ''; view.value = undefined; page.value = 1; total.value = 0; open.value = false; editor.value = undefined; loading.value = false; error.value = '' }
const session = useDirectorySession('config:dictionary:view', clear, () => { attempt++; loading.value = false; open.value = false })
const canEdit = computed(() => auth.hasPermission('config:dictionary:update'))
const disabled = computed(() => loading.value || submission.busy.value || submission.querying.value || !session.accessible.value)
const resource = computed(() => 'config.dictionaries.effective.' + selectedKey.value)
const ordered = computed(() => {
  const entries = view.value?.entries || [], byParent = new Map<string | null, DictionaryEntry[]>()
  for (const entry of entries) byParent.set(entry.parentCode, [...(byParent.get(entry.parentCode) || []), entry])
  const result: { entry: DictionaryEntry; depth: number }[] = [], seen = new Set<string>()
  const walk = (parent: string | null, depth: number) => { for (const entry of byParent.get(parent) || []) if (!seen.has(entry.code)) { seen.add(entry.code); result.push({ entry, depth }); walk(entry.code, depth + 1) } }
  walk(null, 0)
  for (const entry of entries) if (!seen.has(entry.code)) result.push({ entry, depth: 0 })
  return result
})
const parents = computed(() => view.value && editor.value?.mode === 'private' ? parentChoices(view.value, editor.value.existing ? editor.value.code : null) : [])
const invalid = computed(() => {
  const value = editor.value
  if (!value) return true
  const sortInvalid = value.sort !== '' && (!/^\d+$/.test(value.sort) || Number(value.sort) > 2147483647)
  if (value.mode === 'override') return sortInvalid || (!value.text.trim() && !value.zh && !value.en && value.sort === '' && value.hidden === '') || (!value.text.trim() && (!!value.zh || !!value.en))
  return sortInvalid || value.sort === '' || !value.code || value.code.length > 128 || /[\u0000-\u001f\u007f]/.test(value.code) || !value.text.trim() || value.text.length > 128
})
async function loadSummaries(next = 1) {
  if (!session.accessible.value) return
  const ticket = session.ticket(), current = ++attempt; loading.value = true; error.value = ''
  try {
    const result = await layers.dictionaries(next, keyword.value.trim())
    if (!session.valid(ticket) || current !== attempt) return
    summaries.value = result.items; page.value = next; total.value = Number(result.total)
    if (!result.items.some(item => item.key === selectedKey.value)) selectedKey.value = result.items[0]?.key || ''
    if (!selectedKey.value) view.value = undefined
  } catch (cause) { if (session.valid(ticket) && current === attempt) { summaries.value = []; view.value = undefined; error.value = (cause as Error).message } }
  finally { if (session.valid(ticket) && current === attempt) loading.value = false }
  if (session.valid(ticket) && current === attempt && selectedKey.value) await loadView()
}
async function loadView() {
  const key = selectedKey.value
  if (!key || !session.accessible.value) { view.value = undefined; return }
  const ticket = session.ticket(), current = ++attempt; loading.value = true; error.value = ''
  try { const result = await layers.dictionary(key); if (session.valid(ticket) && current === attempt && selectedKey.value === key) view.value = result }
  catch (cause) { if (session.valid(ticket) && current === attempt) { view.value = undefined; error.value = (cause as Error).message } }
  finally { if (session.valid(ticket) && current === attempt) loading.value = false }
}
function choose(key: string) { if (!disabled.value && !open.value) { selectedKey.value = key; submission.resetView(); void loadView() } }
function label(text: string, zh: string, en: string): Label { const translations: Label['translations'] = {}; if (zh.trim()) translations['zh-CN'] = zh.trim(); if (en.trim()) translations['en-US'] = en.trim(); return { text: text.trim(), translations } }
function tone(entry: DictionaryEntry) { return !entry.selectable ? 'danger' : entry.origin === 'PLATFORM' ? 'success' : entry.origin === 'DEFAULT' ? 'neutral' : 'warning' }
function overrideItem(entry: DictionaryEntry) {
  if (!view.value || !canEdit.value || view.value.mode !== 'EXTENDABLE' || entry.origin === 'TENANT_ONLY') return
  const raw = layerEntry(view.value, entry.code)
  submission.resetView(); submission.bind(resource.value)
  editor.value = { mode: 'override', code: entry.code, text: raw?.label?.text || '', zh: raw?.label?.translations['zh-CN'] || '', en: raw?.label?.translations['en-US'] || '', sort: raw?.sort === null || raw?.sort === undefined ? '' : String(raw.sort), hidden: raw?.hidden === null || raw?.hidden === undefined ? '' : raw.hidden ? 'true' : 'false' }
  open.value = true
}
function privateItem(entry?: DictionaryEntry) {
  if (!view.value || !canEdit.value || view.value.mode === 'PLATFORM_LOCKED') return
  const raw = entry ? layerEntry(view.value, entry.code) : undefined
  submission.resetView(); submission.bind(resource.value)
  editor.value = { mode: 'private', existing: !!entry, code: entry?.code || '', parentCode: entry?.parentCode || '', text: raw?.label?.text || '', zh: raw?.label?.translations['zh-CN'] || '', en: raw?.label?.translations['en-US'] || '', sort: String(raw?.sort ?? 0), enabled: raw?.enabled ?? true }
  open.value = true
}
async function after(result: { kind: string }, ticket: { epoch: number; actor: string }) {
  if (!session.valid(ticket)) return
  if (result.kind === 'success' || result.kind === 'completed') { open.value = false; editor.value = undefined; notify(t('common.saved')); await loadView() }
}
async function save() {
  const current = view.value, value = editor.value
  if (!current || !value || invalid.value || disabled.value || submission.unresolved.value) return
  const ticket = session.ticket()
  // Exact code, versions and labels are captured now; a retry of the same key never rereads newer state.
  if (value.mode === 'override') {
    const change = { label: value.text.trim() ? label(value.text, value.zh, value.en) : null, sort: value.sort === '' ? null : Number(value.sort), hidden: value.hidden === '' ? null : value.hidden === 'true' }
    await after(await submission.run({ operation: 'dictionaries.override', resource: resource.value, protocol: 'LOCAL' }, () => options => layers.overrideItem(current, value.code, change, options)), ticket)
  } else {
    const item = { code: value.code, parentCode: value.parentCode || null, label: label(value.text, value.zh, value.en), sort: Number(value.sort), enabled: value.enabled }
    await after(await submission.run({ operation: 'dictionaries.private', resource: resource.value, protocol: 'LOCAL' }, () => options => layers.savePrivate(current, item, options)), ticket)
  }
}
async function restore(entry: DictionaryEntry) {
  const current = view.value
  if (!current || disabled.value || !canEdit.value) return
  submission.resetView(); submission.bind(resource.value)
  const ticket = session.ticket()
  await after(await submission.run({ operation: 'dictionaries.restore', resource: resource.value, protocol: 'LOCAL' }, async () => {
    if (!await confirmAction(t('config.layers.restoreConfirm', { code: entry.code }), t('common.confirm'))) return
    return options => layers.restoreItem(current, entry.code, options)
  }), ticket)
}
async function toggle() {
  const current = view.value
  if (!current || disabled.value || !canEdit.value || current.mode === 'PLATFORM_LOCKED') return
  submission.resetView(); submission.bind(resource.value)
  const enabled = !current.enabled, ticket = session.ticket()
  await after(await submission.run({ operation: 'dictionaries.enabled', resource: resource.value, protocol: 'LOCAL' }, async () => {
    if (!enabled && !await confirmAction(t('config.layers.disableConfirm', { name: current.name }), t('common.confirm'), true)) return
    return options => layers.enabled(current, enabled, options)
  }), ticket)
}
async function check() { const ticket = session.ticket(); if (await submission.check() === 'SUCCEEDED' && session.valid(ticket)) { open.value = false; editor.value = undefined; await loadView() } }
watch(locale, () => { if (!open.value) void loadSummaries() })
watch(() => session.accessible.value, accessible => { if (accessible && !summaries.value.length) void loadSummaries() }, { immediate: true })
</script>
<template>
  <div v-if="session.accessible.value" class="flex flex-col gap-3">
    <p class="text-sm text-muted-foreground">{{ t('config.layers.dictionaryHint') }}</p>
    <div class="flex flex-wrap items-end gap-2">
      <Input v-model="keyword" class="h-9 max-w-xs" :aria-label="t('config.layers.keyword')" :placeholder="t('config.layers.keyword')" :disabled="disabled || submission.unresolved.value" @keydown.enter="loadSummaries(1)" />
      <Button variant="outline" :disabled="disabled || submission.unresolved.value" @click="loadSummaries(1)">{{ t('common.search') }}</Button>
    </div>
    <ErrorNotice :message="error || submission.error.value" />
    <OperationStatus :slow="submission.slow.value" :unresolved="submission.unresolved.value" :querying="submission.querying.value" queryable @check="check" />
    <div class="grid gap-4 lg:grid-cols-[18rem_minmax(0,1fr)]">
      <nav class="flex max-h-[32rem] flex-col gap-1 overflow-y-auto rounded-lg border bg-card p-2" :aria-label="t('config.layers.dictionaries')">
        <button v-for="item in summaries" :key="item.key" type="button" class="rounded-md px-3 py-2 text-left text-sm hover:bg-accent/40 disabled:opacity-60" :class="{ 'bg-accent/60 font-medium': item.key === selectedKey }" :aria-current="item.key === selectedKey ? 'true' : undefined" :disabled="disabled || open" @click="choose(item.key)">
          <span class="block truncate">{{ item.name }}</span>
          <span class="block truncate text-xs text-muted-foreground">{{ item.overridden || item.privateItems ? t('config.layers.joined', { left: t('config.layers.joined', { left: item.key, right: t('config.layers.modes.' + item.mode) }), right: t('config.layers.customized', { overridden: item.overridden, private: item.privateItems }) }) : t('config.layers.joined', { left: item.key, right: t('config.layers.modes.' + item.mode) }) }}</span>
        </button>
        <EmptyState v-if="!summaries.length && !loading" />
        <div v-if="total > 50" class="mt-auto flex items-center justify-between gap-1 border-t pt-2 text-xs"><Button size="sm" variant="ghost" :disabled="disabled || open || submission.unresolved.value || page <= 1" @click="loadSummaries(page - 1)">{{ t('config.layers.previous') }}</Button><span>{{ t('common.pageOf', { page, pages: Math.ceil(total / 50) }) }}</span><Button size="sm" variant="ghost" :disabled="disabled || open || submission.unresolved.value || page * 50 >= total" @click="loadSummaries(page + 1)">{{ t('config.layers.next') }}</Button></div>
      </nav>
      <section v-if="view" class="min-w-0 rounded-lg border bg-card p-4">
        <div class="mb-3 flex flex-wrap items-center gap-2">
          <h3 class="text-base font-semibold">{{ view.name }}</h3><code class="text-xs">{{ view.key }}</code>
          <StatusBadge :label="t('config.layers.modes.' + view.mode)" />
          <StatusBadge :label="view.enabled ? t('config.layers.enabled') : t('config.layers.disabled')" :tone="view.enabled ? 'success' : 'danger'" />
          <span class="text-xs tabular-nums text-muted-foreground">{{ t('config.layers.revision', { platform: view.revision.platform, tenant: view.revision.tenant }) }}</span>
          <div v-if="canEdit" class="ml-auto flex gap-2">
            <Button v-if="view.mode !== 'PLATFORM_LOCKED'" size="sm" variant="outline" :disabled="disabled || submission.unresolved.value" @click="privateItem()">{{ t('config.layers.addPrivate') }}</Button>
            <Button v-if="view.mode !== 'PLATFORM_LOCKED'" size="sm" variant="ghost" :disabled="disabled || submission.unresolved.value" @click="toggle">{{ view.enabled ? t('config.layers.disable') : t('config.layers.enable') }}</Button>
          </div>
        </div>
        <p v-if="view.mode === 'PLATFORM_LOCKED'" class="mb-2 text-xs text-muted-foreground">{{ t('config.layers.lockedHint') }}</p>
        <div class="overflow-x-auto"><table class="w-full text-left text-sm"><thead class="bg-muted text-xs text-muted-foreground"><tr><th class="p-2">{{ t('config.layers.columns.label') }}</th><th class="p-2">{{ t('config.layers.columns.code') }}</th><th class="p-2">{{ t('config.layers.columns.origin') }}</th><th class="p-2">{{ t('config.layers.columns.sort') }}</th><th class="p-2">{{ t('config.layers.columns.actions') }}</th></tr></thead>
          <tbody><tr v-for="{ entry, depth } in ordered" :key="entry.code" class="border-t">
            <td class="p-2" :style="{ paddingLeft: 8 + depth * 18 + 'px' }">{{ entry.label }}</td>
            <td class="p-2"><code class="break-all text-xs">{{ entry.code }}</code></td>
            <td class="p-2"><StatusBadge :label="t('config.layers.origins.' + entry.origin)" :tone="tone(entry)" /><span v-if="!entry.selectable" class="ml-2 text-xs text-muted-foreground">{{ entry.hidden ? t('config.layers.hidden') : t('config.layers.notSelectable') }}</span></td>
            <td class="p-2 tabular-nums">{{ entry.sort }}</td>
            <td class="p-2"><div v-if="canEdit" class="flex gap-1">
              <Button v-if="view.mode === 'EXTENDABLE' && entry.origin !== 'TENANT_ONLY'" size="sm" variant="outline" :disabled="disabled || submission.unresolved.value" @click="overrideItem(entry)">{{ t('config.layers.override') }}</Button>
              <Button v-if="entry.origin === 'TENANT_OVERRIDE'" size="sm" variant="ghost" :disabled="disabled || submission.unresolved.value" @click="restore(entry)">{{ t('config.layers.inherit') }}</Button>
              <Button v-if="entry.origin === 'TENANT_ONLY'" size="sm" variant="outline" :disabled="disabled || submission.unresolved.value" @click="privateItem(entry)">{{ t('common.edit') }}</Button>
            </div></td>
          </tr></tbody></table></div>
      </section>
      <EmptyState v-else-if="!loading" :title="t('config.layers.selectDictionary')" />
    </div>
    <EditorDialog v-model="open" :title="editor?.mode === 'override' ? t('config.layers.overrideItem', { code: editor.code }) : t('config.layers.privateItem')" :busy="submission.busy.value" :submit-disabled="invalid || submission.unresolved.value" :dirty="open" @save="save">
      <div v-if="editor" class="grid gap-3 text-sm sm:grid-cols-2">
        <div class="sm:col-span-2"><ErrorNotice :message="submission.error.value" /><OperationStatus :slow="submission.slow.value" :unresolved="submission.unresolved.value" :querying="submission.querying.value" queryable @check="check" /></div>
        <label v-if="editor.mode === 'private'" class="flex flex-col gap-1 sm:col-span-2">{{ t('config.layers.columns.code') }}<Input v-model="editor.code" :disabled="editor.existing || submission.busy.value || submission.unresolved.value" maxlength="128" /></label>
        <label v-if="editor.mode === 'private' && view?.tree" class="flex flex-col gap-1 sm:col-span-2">{{ t('config.layers.parent') }}<select v-model="editor.parentCode" class="h-9 rounded-md border bg-background px-2" :disabled="submission.busy.value || submission.unresolved.value"><option value="">{{ t('config.layers.root') }}</option><option v-for="parent in parents" :key="parent.code" :value="parent.code">{{ t('config.layers.joined', { left: parent.label, right: parent.code }) }}</option></select></label>
        <label class="flex flex-col gap-1 sm:col-span-2">{{ t('config.layers.labelText') }}<Input v-model="editor.text" maxlength="128" :disabled="submission.busy.value || submission.unresolved.value" /></label>
        <label class="flex flex-col gap-1">{{ t('config.layers.labelZh') }}<Input v-model="editor.zh" maxlength="128" :disabled="submission.busy.value || submission.unresolved.value" /></label>
        <label class="flex flex-col gap-1">{{ t('config.layers.labelEn') }}<Input v-model="editor.en" maxlength="128" :disabled="submission.busy.value || submission.unresolved.value" /></label>
        <label class="flex flex-col gap-1">{{ t('config.layers.columns.sort') }}<Input v-model="editor.sort" inputmode="numeric" :placeholder="editor.mode === 'override' ? t('config.layers.inheritPlaceholder') : ''" :disabled="submission.busy.value || submission.unresolved.value" /></label>
        <label v-if="editor.mode === 'override'" class="flex flex-col gap-1">{{ t('config.layers.visibility') }}<select v-model="editor.hidden" class="h-9 rounded-md border bg-background px-2" :disabled="submission.busy.value || submission.unresolved.value"><option value="">{{ t('config.layers.inheritPlaceholder') }}</option><option value="false">{{ t('config.layers.visible') }}</option><option value="true">{{ t('config.layers.hidden') }}</option></select></label>
        <label v-else class="flex items-center gap-2"><input v-model="editor.enabled" type="checkbox" :disabled="submission.busy.value || submission.unresolved.value" /> {{ t('config.layers.enabled') }}</label>
        <p class="text-xs text-muted-foreground sm:col-span-2">{{ editor.mode === 'override' ? t('config.layers.overrideItemHint') : t('config.layers.privateHint') }}</p>
      </div>
    </EditorDialog>
  </div>
</template>

