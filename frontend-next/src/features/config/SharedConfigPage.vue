<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuth } from '@/stores/auth'
import { confirmAction, notify } from '@/lib/feedback'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import PageHeader from '@/components/business/PageHeader.vue'
import TableWorkspace from '@/components/business/TableWorkspace.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import EmptyState from '@/components/business/EmptyState.vue'
import StatusBadge from '@/components/business/StatusBadge.vue'
import OperationStatus from '@/components/business/OperationStatus.vue'
import { useSubmission } from '@/composables/useSubmission'
import { useDirectorySession } from '@/features/tenant/useDirectorySession'
import { parameterProblem, shared, type Label, type SharedDetail, type SharedItem, type SharedRevision, type SharedSummary } from './layers'

interface ItemDraft { code: string; parentCode: string; text: string; zh: string; en: string; messageKey: string | null; enabled: boolean; sort: string; published: boolean }
const { t, locale } = useI18n(), auth = useAuth(), submission = useSubmission()
const kind = ref<'' | 'PARAMETER' | 'DICTIONARY'>(''), keyword = ref(''), rows = ref<SharedSummary[]>([]), selectedId = ref(''), detail = ref<SharedDetail>()
const history = ref<SharedRevision[]>([]), historyPage = ref(1), historyTotal = ref(0), listPage = ref(1), listTotal = ref(0), snapshot = ref(''), loading = ref(false), error = ref('')
const text = ref(''), zh = ref(''), en = ref(''), value = ref(''), explicitNull = ref(false), items = ref<ItemDraft[]>([])
let attempt = 0, snapshotAttempt = 0
function clear() { attempt++; snapshotAttempt++; rows.value = []; selectedId.value = ''; detail.value = undefined; history.value = []; historyPage.value = 1; historyTotal.value = 0; listPage.value = 1; listTotal.value = 0; snapshot.value = ''; text.value = ''; zh.value = ''; en.value = ''; value.value = ''; explicitNull.value = false; items.value = []; loading.value = false; error.value = '' }
const session = useDirectorySession('config:shared:view', clear, () => { attempt++; loading.value = false })
const canEdit = computed(() => auth.hasPermission('config:shared:edit')), canPublish = computed(() => auth.hasPermission('config:shared:publish'))
const disabled = computed(() => loading.value || submission.busy.value || submission.querying.value || !session.accessible.value)
const resource = computed(() => 'config.shared.' + selectedId.value)
const parameter = computed(() => detail.value?.parameterDefinition)
const problem = computed(() => parameter.value ? parameterProblem({ valueType: parameter.value.valueType, constraints: parameter.value.constraints }, explicitNull.value ? null : value.value) : null)
const itemProblem = computed(() => {
  if (detail.value?.kind !== 'DICTIONARY') return null
  const codes = new Set<string>()
  for (const item of items.value) {
    if (!item.code || item.code.length > 128 || /[\u0000-\u001f\u007f]/.test(item.code) || codes.has(item.code)) return 'code'
    if (!item.text.trim() || item.text.length > 128 || !/^\d+$/.test(item.sort) || Number(item.sort) > 2147483647) return 'item'
    codes.add(item.code)
  }
  for (const item of items.value) if (item.parentCode && (!codes.has(item.parentCode) || item.parentCode === item.code || !detail.value.dictionaryDefinition?.tree)) return 'parent'
  return items.value.length > 512 ? 'capacity' : null
})
const dirty = computed(() => !!detail.value && JSON.stringify(currentContent()) !== JSON.stringify(baseline(detail.value)))
function label(): Label { const translations: Label['translations'] = {}; if (zh.value.trim()) translations['zh-CN'] = zh.value.trim(); if (en.value.trim()) translations['en-US'] = en.value.trim(); return { text: text.value.trim(), messageKey: currentLabel()?.messageKey ?? null, translations } }
function currentLabel() { return detail.value?.parameterDraft?.label || detail.value?.dictionaryDraft?.label }
function toItems(): SharedItem[] {
  return items.value.map(item => { const translations: Label['translations'] = {}; if (item.zh.trim()) translations['zh-CN'] = item.zh.trim(); if (item.en.trim()) translations['en-US'] = item.en.trim()
    return { code: item.code, parentCode: item.parentCode || null, label: { text: item.text.trim(), messageKey: item.messageKey, translations }, enabled: item.enabled, sort: Number(item.sort) } })
}
function currentContent() { return { label: label(), value: detail.value?.kind === 'DICTIONARY' || explicitNull.value ? null : value.value, items: detail.value?.kind === 'DICTIONARY' ? toItems() : [] } }
function baseline(row: SharedDetail) { return { label: { text: currentLabel()?.text || '', messageKey: currentLabel()?.messageKey ?? null, translations: currentLabel()?.translations || {} }, value: row.parameterDraft?.value ?? null, items: row.dictionaryDraft?.items.map(item => ({ code: item.code, parentCode: item.parentCode, label: { text: item.label.text, messageKey: item.label.messageKey ?? null, translations: item.label.translations }, enabled: item.enabled, sort: item.sort })) || [] } }
function fill(row: SharedDetail, publishedCodes: string[]) {
  detail.value = row; const current = row.parameterDraft?.label || row.dictionaryDraft?.label
  text.value = current?.text || ''; zh.value = current?.translations['zh-CN'] || ''; en.value = current?.translations['en-US'] || ''
  value.value = row.parameterDraft?.value ?? ''; explicitNull.value = row.parameterDraft !== null && row.parameterDraft?.value === null
  // Codes in the current publication are stable: they may be disabled or relabelled, never removed or reparented.
  const published = new Set(publishedCodes)
  items.value = (row.dictionaryDraft?.items || []).map(item => ({ code: item.code, parentCode: item.parentCode || '', text: item.label.text, zh: item.label.translations['zh-CN'] || '', en: item.label.translations['en-US'] || '', messageKey: item.label.messageKey ?? null, enabled: item.enabled, sort: String(item.sort), published: published.has(item.code) }))
}
async function load(next = 1) {
  if (!session.accessible.value || submission.unresolved.value) return
  if (dirty.value && !await confirmAction(t('common.unsavedConfirm'), t('common.confirm'))) return
  const ticket = session.ticket(), current = ++attempt; loading.value = true; error.value = ''
  try {
    const result = await shared.page(next, kind.value, keyword.value.trim())
    if (!session.valid(ticket) || current !== attempt) return
    rows.value = result.items; listPage.value = next; listTotal.value = Number(result.total)
    if (!result.items.some(item => item.id === selectedId.value)) selectedId.value = result.items[0]?.id || ''
  } catch (cause) { if (session.valid(ticket) && current === attempt) { rows.value = []; error.value = (cause as Error).message } }
  finally { if (session.valid(ticket) && current === attempt) loading.value = false }
  if (session.valid(ticket) && current === attempt) await open(selectedId.value)
}
async function open(id: string, page = 1) {
  if (!id) { detail.value = undefined; return }
  const ticket = session.ticket(), current = ++attempt; snapshotAttempt++; loading.value = true; error.value = ''; snapshot.value = ''
  try {
    const [row, revisions] = await Promise.all([shared.detail(id), shared.history(id, page)])
    const publication = row.kind === 'DICTIONARY' && row.publishedRevisionId ? await shared.revision(id, row.publishedRevisionId) : undefined
    if (!session.valid(ticket) || current !== attempt) return
    selectedId.value = id; fill(row, publication?.dictionary?.items.map(item => item.code) || []); history.value = revisions.items; historyPage.value = page; historyTotal.value = Number(revisions.total)
  } catch (cause) { if (session.valid(ticket) && current === attempt) { detail.value = undefined; error.value = (cause as Error).message } }
  finally { if (session.valid(ticket) && current === attempt) loading.value = false }
}
async function choose(id: string) { if (disabled.value || id === selectedId.value) return; if (dirty.value && !await confirmAction(t('common.unsavedConfirm'), t('common.confirm'))) return; submission.resetView(); await open(id) }
function addItem() { items.value.push({ code: '', parentCode: '', text: '', zh: '', en: '', messageKey: null, enabled: true, sort: String(items.value.length), published: false }) }
function removeItem(index: number) { if (!items.value[index]?.published) items.value.splice(index, 1) }
async function save() {
  const row = detail.value
  if (!row || disabled.value || !canEdit.value || submission.unresolved.value || (row.kind === 'PARAMETER' ? problem.value : itemProblem.value) || !text.value.trim()) return
  submission.bind(resource.value)
  const content = currentContent(), ticket = session.ticket()
  const result = await submission.run({ operation: 'shared.draft', resource: resource.value, protocol: 'LOCAL' }, () => options => row.kind === 'PARAMETER'
    ? shared.saveParameter(row, content.label, content.value, options) : shared.saveDictionary(row, content.label, content.items, options))
  if (session.valid(ticket) && (result.kind === 'success' || result.kind === 'completed')) { notify(t('config.shared.draftSaved')); await open(row.id) }
}
async function publish() {
  const row = detail.value
  if (!row || disabled.value || !canPublish.value || dirty.value || submission.unresolved.value) return
  submission.bind(resource.value)
  const ticket = session.ticket()
  const result = await submission.run({ operation: 'shared.publish', resource: resource.value, protocol: 'LOCAL' }, async () => {
    if (!await confirmAction(t('config.shared.publishConfirm', { key: row.key, version: row.draftVersion }), t('common.confirm'), true)) return
    return options => shared.publish(row, options)
  })
  if (session.valid(ticket) && (result.kind === 'success' || result.kind === 'completed')) { notify(t('config.shared.published')); await load() }
}
async function showRevision(revision: SharedRevision) {
  if (!detail.value || disabled.value) return
  const ticket = session.ticket(), id = detail.value.id, current = ++snapshotAttempt
  try { const result = await shared.revision(id, revision.id); if (session.valid(ticket) && current === snapshotAttempt && detail.value?.id === id) snapshot.value = JSON.stringify(result.parameter || result.dictionary, null, 2) }
  catch (cause) { if (session.valid(ticket) && current === snapshotAttempt) error.value = (cause as Error).message }
}
async function check() { const ticket = session.ticket(); if (await submission.check() === 'SUCCEEDED' && session.valid(ticket)) await open(selectedId.value) }
async function historyTurn(next: number) { if (!detail.value || submission.unresolved.value || next < 1 || next > Math.ceil(historyTotal.value / 20)) return; if (dirty.value && !await confirmAction(t('common.unsavedConfirm'), t('common.confirm'))) return; await open(detail.value.id, next) }
watch(locale, () => { if (!dirty.value) void load() })
watch(() => session.accessible.value, accessible => { if (accessible && !rows.value.length) void load() }, { immediate: true })
</script>
<template>
  <TableWorkspace v-if="session.accessible.value">
    <template #tools>
      <PageHeader :title="t('config.shared.title')" :description="t('config.shared.description')" />
      <div class="mb-3 flex flex-wrap items-end gap-2">
        <select v-model="kind" class="h-9 rounded-md border bg-background px-2" :aria-label="t('config.shared.kind')" :disabled="disabled || submission.unresolved.value"><option value="">{{ t('config.shared.allKinds') }}</option><option value="PARAMETER">{{ t('config.shared.kinds.PARAMETER') }}</option><option value="DICTIONARY">{{ t('config.shared.kinds.DICTIONARY') }}</option></select>
        <Input v-model="keyword" class="h-9 max-w-xs" :aria-label="t('config.layers.keyword')" :placeholder="t('config.layers.keyword')" :disabled="disabled || submission.unresolved.value" @keydown.enter="load(1)" />
        <Button variant="outline" :disabled="disabled || submission.unresolved.value" @click="load(1)">{{ t('common.search') }}</Button>
      </div>
      <ErrorNotice :message="error || submission.error.value" />
      <OperationStatus :slow="submission.slow.value" :unresolved="submission.unresolved.value" :querying="submission.querying.value" queryable @check="check" />
    </template>
    <div class="grid gap-4 lg:grid-cols-[18rem_minmax(0,1fr)]">
      <nav class="flex max-h-[36rem] flex-col gap-1 overflow-y-auto rounded-lg border bg-card p-2" :aria-label="t('config.shared.title')">
        <button v-for="row in rows" :key="row.id" type="button" class="rounded-md px-3 py-2 text-left text-sm hover:bg-accent/40 disabled:opacity-60" :class="{ 'bg-accent/60 font-medium': row.id === selectedId }" :aria-current="row.id === selectedId ? 'true' : undefined" :disabled="disabled" @click="choose(row.id)">
          <span class="block truncate">{{ row.label.translations[locale as 'zh-CN' | 'en-US'] || row.label.text }}</span>
          <span class="block truncate text-xs text-muted-foreground">{{ t('config.layers.joined', { left: t('config.layers.joined', { left: t('config.shared.kinds.' + row.kind), right: row.key }), right: t('config.shared.revisionShort', { revision: row.publishedRevision }) }) }}</span>
        </button>
        <EmptyState v-if="!rows.length && !loading" />
        <div v-if="listTotal > 50" class="mt-auto flex items-center justify-between gap-1 border-t pt-2 text-xs"><Button size="sm" variant="ghost" :disabled="disabled || submission.unresolved.value || listPage <= 1" @click="load(listPage - 1)">{{ t('config.layers.previous') }}</Button><span>{{ t('common.pageOf', { page: listPage, pages: Math.ceil(listTotal / 50) }) }}</span><Button size="sm" variant="ghost" :disabled="disabled || submission.unresolved.value || listPage * 50 >= listTotal" @click="load(listPage + 1)">{{ t('config.layers.next') }}</Button></div>
      </nav>
      <section v-if="detail" class="flex min-w-0 flex-col gap-4 rounded-lg border bg-card p-4">
        <div class="flex flex-wrap items-center gap-2">
          <code class="text-sm">{{ detail.key }}</code><StatusBadge :label="t('config.shared.kinds.' + detail.kind)" />
          <span class="text-xs tabular-nums text-muted-foreground">{{ t('config.shared.versions', { draft: detail.draftVersion, revision: detail.publishedRevision }) }}</span>
          <StatusBadge v-if="detail.codeDefaultsChanged" :label="t('config.shared.codeDefaultsChanged')" tone="warning" />
          <span class="ml-auto flex gap-2">
            <Button v-if="canEdit" size="sm" :disabled="disabled || submission.unresolved.value || !dirty || !!(detail.kind === 'PARAMETER' ? problem : itemProblem) || !text.trim()" @click="save">{{ t('config.shared.saveDraft') }}</Button>
            <Button v-if="canPublish" size="sm" variant="outline" :disabled="disabled || submission.unresolved.value || dirty" :title="dirty ? t('config.shared.saveFirst') : ''" @click="publish">{{ t('config.shared.publish') }}</Button>
          </span>
        </div>
        <p class="text-xs text-muted-foreground">{{ t('config.shared.draftHint') }}</p>
        <div class="grid gap-3 text-sm sm:grid-cols-3">
          <label class="flex flex-col gap-1">{{ t('config.layers.labelText') }}<Input v-model="text" maxlength="128" :disabled="!canEdit || disabled || submission.unresolved.value" /></label>
          <label class="flex flex-col gap-1">{{ t('config.layers.labelZh') }}<Input v-model="zh" maxlength="128" :disabled="!canEdit || disabled || submission.unresolved.value" /></label>
          <label class="flex flex-col gap-1">{{ t('config.layers.labelEn') }}<Input v-model="en" maxlength="128" :disabled="!canEdit || disabled || submission.unresolved.value" /></label>
        </div>
        <div v-if="detail.kind === 'PARAMETER' && parameter" class="flex flex-col gap-2 text-sm">
          <p class="text-xs text-muted-foreground">{{ t('config.layers.joined', { left: parameter.valueType ? t('config.choices.' + parameter.valueType) : '—', right: parameter.overridePolicy ? t('config.layers.policies.' + parameter.overridePolicy) : '—' }) }}<template v-if="parameter.securitySensitive">{{ ' ' }}{{ t('config.layers.security') }}</template></p>
          <label v-if="parameter.constraints.nullable" class="flex items-center gap-2"><input v-model="explicitNull" type="checkbox" :disabled="!canEdit || disabled || submission.unresolved.value" /> {{ t('config.layers.explicitNull') }}</label>
          <Textarea v-if="!explicitNull" v-model="value" rows="4" class="font-mono" :aria-label="t('config.layers.value')" :disabled="!canEdit || disabled || submission.unresolved.value" />
          <p v-if="problem" class="text-destructive" role="alert">{{ t('config.layers.problems.' + problem) }}</p>
        </div>
        <div v-else-if="detail.kind === 'DICTIONARY'" class="flex flex-col gap-2 text-sm">
          <p class="text-xs text-muted-foreground">{{ t('config.layers.joined', { left: t('config.layers.modes.' + detail.dictionaryDefinition?.mode), right: t('config.shared.stableCodes') }) }}</p>
          <div class="overflow-x-auto"><table class="w-full text-left text-xs"><thead class="bg-muted text-muted-foreground"><tr><th class="p-2">{{ t('config.layers.columns.code') }}</th><th class="p-2">{{ t('config.layers.parent') }}</th><th class="p-2">{{ t('config.layers.labelText') }}</th><th class="p-2">{{ t('config.layers.labelZh') }}</th><th class="p-2">{{ t('config.layers.labelEn') }}</th><th class="p-2">{{ t('config.layers.columns.sort') }}</th><th class="p-2">{{ t('config.layers.enabled') }}</th><th class="p-2" /></tr></thead>
            <tbody><tr v-for="(item, index) in items" :key="index" class="border-t">
              <td class="p-1"><Input v-model="item.code" class="h-8 font-mono" maxlength="128" :disabled="item.published || !canEdit || disabled || submission.unresolved.value" :aria-label="t('config.layers.columns.code')" /></td>
              <td class="p-1"><Input v-model="item.parentCode" class="h-8 font-mono" maxlength="128" :disabled="item.published || !canEdit || disabled || submission.unresolved.value || !detail.dictionaryDefinition?.tree" :aria-label="t('config.layers.parent')" /></td>
              <td class="p-1"><Input v-model="item.text" class="h-8" maxlength="128" :disabled="!canEdit || disabled || submission.unresolved.value" :aria-label="t('config.layers.labelText')" /></td>
              <td class="p-1"><Input v-model="item.zh" class="h-8" maxlength="128" :disabled="!canEdit || disabled || submission.unresolved.value" :aria-label="t('config.layers.labelZh')" /></td>
              <td class="p-1"><Input v-model="item.en" class="h-8" maxlength="128" :disabled="!canEdit || disabled || submission.unresolved.value" :aria-label="t('config.layers.labelEn')" /></td>
              <td class="p-1"><Input v-model="item.sort" class="h-8 w-20" inputmode="numeric" :disabled="!canEdit || disabled || submission.unresolved.value" :aria-label="t('config.layers.columns.sort')" /></td>
              <td class="p-1 text-center"><input v-model="item.enabled" type="checkbox" :disabled="!canEdit || disabled || submission.unresolved.value" :aria-label="t('config.layers.enabled')" /></td>
              <td class="p-1"><Button v-if="!item.published && canEdit" size="sm" variant="ghost" :disabled="disabled || submission.unresolved.value" @click="removeItem(index)">{{ t('config.shared.removeItem') }}</Button></td>
            </tr></tbody></table></div>
          <Button v-if="canEdit" size="sm" variant="outline" class="self-start" :disabled="disabled || submission.unresolved.value || items.length >= 512" @click="addItem">{{ t('config.shared.addItem') }}</Button>
          <p v-if="itemProblem" class="text-destructive" role="alert">{{ t('config.shared.problems.' + itemProblem) }}</p>
        </div>
        <div>
          <h3 class="mb-2 text-sm font-semibold">{{ t('config.shared.history') }}</h3>
          <ul class="flex flex-col gap-1 text-xs">
            <li v-for="revision in history" :key="revision.id" class="flex flex-wrap items-center gap-2"><span class="tabular-nums">{{ t('config.shared.revisionShort', { revision: revision.revision }) }}</span><span class="text-muted-foreground">{{ t('config.shared.revisionMeta', { time: revision.createdAt, bytes: revision.contentBytes }) }}</span><StatusBadge v-if="revision.id === detail.publishedRevisionId" :label="t('config.shared.current')" tone="success" /><Button size="sm" variant="ghost" :disabled="disabled" @click="showRevision(revision)">{{ t('config.shared.view') }}</Button></li>
          </ul>
          <div v-if="historyTotal > 20" class="mt-2 flex items-center gap-2 text-xs"><Button size="sm" variant="ghost" :disabled="disabled || historyPage <= 1" @click="historyTurn(historyPage - 1)">{{ t('config.layers.previous') }}</Button><span>{{ t('common.pageOf', { page: historyPage, pages: Math.ceil(historyTotal / 20) }) }}</span><Button size="sm" variant="ghost" :disabled="disabled || historyPage * 20 >= historyTotal" @click="historyTurn(historyPage + 1)">{{ t('config.layers.next') }}</Button></div>
          <pre v-if="snapshot" class="mt-2 max-h-64 overflow-auto rounded-md bg-muted p-2 text-xs">{{ snapshot }}</pre>
        </div>
      </section>
      <EmptyState v-else-if="!loading" :title="t('config.shared.select')" />
    </div>
  </TableWorkspace>
</template>

