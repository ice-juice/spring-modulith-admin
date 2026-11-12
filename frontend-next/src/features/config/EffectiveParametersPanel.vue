<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuth } from '@/stores/auth'
import { confirmAction, notify } from '@/lib/feedback'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import DataTable from '@/components/business/DataTable.vue'
import EditorDialog from '@/components/business/EditorDialog.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import StatusBadge from '@/components/business/StatusBadge.vue'
import OperationStatus from '@/components/business/OperationStatus.vue'
import { useSubmission } from '@/composables/useSubmission'
import { useDirectorySession } from '@/features/tenant/useDirectorySession'
import { editable, layers, parameterProblem, type EffectiveParameter } from './layers'

const { t, locale } = useI18n(), auth = useAuth(), submission = useSubmission()
const rows = ref<(EffectiveParameter & { id: string })[]>([]), total = ref(0), page = ref(1), keyword = ref(''), applied = ref('')
const loading = ref(false), error = ref(''), open = ref(false), target = ref<EffectiveParameter>(), draft = ref(''), explicitNull = ref(false)
let attempt = 0
function clear() { attempt++; rows.value = []; total.value = 0; target.value = undefined; open.value = false; draft.value = ''; explicitNull.value = false; loading.value = false; error.value = '' }
const session = useDirectorySession('config:parameter:view', clear, () => { attempt++; loading.value = false; open.value = false })
const canEdit = computed(() => auth.hasPermission('config:parameter:update'))
const disabled = computed(() => loading.value || submission.busy.value || submission.querying.value || !session.accessible.value)
const columns = computed(() => ['name', 'key', 'value', 'origin', 'revision', 'actions'].map(key => ({ key, title: t('config.layers.columns.' + key), width: key === 'value' ? 220 : key === 'actions' ? 170 : 150 })))
const problem = computed(() => target.value ? parameterProblem(target.value, explicitNull.value ? null : draft.value) : null)
const dirty = computed(() => !!target.value && (explicitNull.value ? target.value.value !== null : draft.value !== (target.value.value ?? '')))
async function load(next = page.value) {
  if (!session.accessible.value) return
  const ticket = session.ticket(), current = ++attempt; loading.value = true; error.value = ''
  try {
    const result = await layers.parameters(next, applied.value)
    if (!session.valid(ticket) || current !== attempt) return
    rows.value = result.items.map(row => ({ ...row, id: row.key })); total.value = Number(result.total); page.value = next
  } catch (cause) { if (session.valid(ticket) && current === attempt) { rows.value = []; error.value = (cause as Error).message } }
  finally { if (session.valid(ticket) && current === attempt) loading.value = false }
}
function search() { if (!disabled.value) { applied.value = keyword.value.trim(); void load(1) } }
function display(row: EffectiveParameter) { return row.value === null ? t('config.layers.nullValue') : row.value === '' ? t('config.layers.emptyValue') : row.value }
function tone(row: EffectiveParameter) { return row.origin === 'TENANT_OVERRIDE' || row.origin === 'TENANT_ONLY' ? 'warning' : row.origin === 'PLATFORM' ? 'success' : 'neutral' }
function edit(row: EffectiveParameter) {
  if (disabled.value || !canEdit.value || !editable(row)) return
  submission.resetView(); submission.bind('config.parameters.effective.' + row.key)
  target.value = row; draft.value = row.value ?? ''; explicitNull.value = row.value === null && row.constraints.nullable; open.value = true
}
async function save() {
  const row = target.value
  if (!row || disabled.value || submission.unresolved.value || problem.value) return
  // The revision and value are fixed before confirmation; a retry never rereads newer versions under the same key.
  const value = explicitNull.value ? null : draft.value, ticket = session.ticket()
  const result = await submission.run({ operation: 'parameters.override', resource: 'config.parameters.effective.' + row.key, protocol: 'LOCAL' }, () => options => layers.override(row, value, options))
  if (!session.valid(ticket)) return
  if (result.kind === 'success' || result.kind === 'completed') { open.value = false; target.value = undefined; notify(t('common.saved')); await load() }
}
async function inherit(row: EffectiveParameter) {
  if (disabled.value || !canEdit.value || !row.overridden) return
  submission.resetView(); submission.bind('config.parameters.effective.' + row.key)
  const ticket = session.ticket()
  const result = await submission.run({ operation: 'parameters.inherit', resource: 'config.parameters.effective.' + row.key, protocol: 'LOCAL' }, async () => {
    if (!await confirmAction(t('config.layers.inheritConfirm', { key: row.key }), t('common.confirm'))) return
    return options => layers.inherit(row, options)
  })
  if (session.valid(ticket) && (result.kind === 'success' || result.kind === 'completed')) { notify(t('config.layers.inherited')); await load() }
}
async function check() { const ticket = session.ticket(); if (await submission.check() === 'SUCCEEDED' && session.valid(ticket)) { open.value = false; target.value = undefined; await load() } }
watch(locale, () => { if (!open.value) void load() })
watch(() => session.accessible.value, accessible => { if (accessible && !rows.value.length) void load(1) }, { immediate: true })
</script>
<template>
  <div v-if="session.accessible.value" class="flex flex-col gap-3">
    <p class="text-sm text-muted-foreground">{{ t('config.layers.parameterHint') }}</p>
    <div class="flex flex-wrap items-end gap-2">
      <Input v-model="keyword" class="h-9 max-w-xs" :aria-label="t('config.layers.keyword')" :placeholder="t('config.layers.keyword')" :disabled="disabled" @keydown.enter="search" />
      <Button variant="outline" :disabled="disabled" @click="search">{{ t('common.search') }}</Button>
      <Button variant="ghost" :disabled="disabled" @click="load()">{{ t('common.refresh') }}</Button>
    </div>
    <ErrorNotice :message="error || submission.error.value" />
    <OperationStatus :slow="submission.slow.value" :unresolved="submission.unresolved.value" :querying="submission.querying.value" queryable @check="check" />
    <DataTable :rows="rows" :columns="columns" :loading="loading" :page="page" :size="50" :page-sizes="[50]" :total="total" storage-key="config.effective-parameters" @update:page="load">
      <template #name="{ row }"><span class="font-medium">{{ row.name }}</span><span v-if="row.securitySensitive" class="ml-2 text-xs text-muted-foreground">{{ t('config.layers.security') }}</span></template>
      <template #key="{ row }"><code class="text-xs">{{ row.key }}</code></template>
      <template #value="{ row }"><span class="break-all font-mono text-xs" :class="{ 'italic text-muted-foreground': row.value === null || row.value === '' }">{{ display(row) }}</span></template>
      <template #origin="{ row }"><StatusBadge :label="t('config.layers.origins.' + row.origin)" :tone="tone(row)" /><span v-if="row.overridePolicy === 'LOCKED'" class="ml-2 text-xs text-muted-foreground">{{ t('config.layers.locked') }}</span></template>
      <template #revision="{ row }"><span class="tabular-nums text-xs text-muted-foreground">{{ t('config.layers.revision', { platform: row.revision.platform, tenant: row.revision.tenant }) }}</span></template>
      <template #actions="{ row }">
        <div v-if="canEdit && editable(row)" class="flex gap-1">
          <Button size="sm" variant="outline" :disabled="disabled || submission.unresolved.value" @click="edit(row)">{{ t('config.layers.override') }}</Button>
          <Button v-if="row.overridden" size="sm" variant="ghost" :disabled="disabled || submission.unresolved.value" @click="inherit(row)">{{ t('config.layers.inherit') }}</Button>
        </div>
      </template>
    </DataTable>
    <EditorDialog v-model="open" :title="t('config.layers.overrideTitle', { name: target?.name || '' })" :description="t('config.layers.overrideHint')" :busy="submission.busy.value" :dirty="dirty" :submit-disabled="!!problem || submission.unresolved.value" :save-label="t('config.layers.saveOverride')" @save="save">
      <div v-if="target" class="flex flex-col gap-3 text-sm">
        <ErrorNotice :message="submission.error.value" />
        <OperationStatus :slow="submission.slow.value" :unresolved="submission.unresolved.value" :querying="submission.querying.value" queryable @check="check" />
        <p>{{ t('config.layers.typeAndKey', { key: target.key, type: target.valueType ? t('config.choices.' + target.valueType) : '—' }) }}</p>
        <label v-if="target.constraints.nullable" class="flex items-center gap-2"><input v-model="explicitNull" type="checkbox" :disabled="submission.busy.value || submission.unresolved.value" /> {{ t('config.layers.explicitNull') }}</label>
        <select v-if="!explicitNull && (target.valueType === 'BOOLEAN' || target.constraints.choices.length)" v-model="draft" class="h-9 rounded-md border bg-background px-2" :aria-label="t('config.layers.value')" :disabled="submission.busy.value || submission.unresolved.value">
          <option v-for="choice in target.valueType === 'BOOLEAN' ? ['true', 'false'] : target.constraints.choices" :key="choice" :value="choice">{{ choice }}</option>
        </select>
        <Textarea v-else-if="!explicitNull" v-model="draft" rows="4" class="font-mono" :aria-label="t('config.layers.value')" :disabled="submission.busy.value || submission.unresolved.value" />
        <p v-if="problem" class="text-destructive" role="alert">{{ t('config.layers.problems.' + problem) }}</p>
        <p class="text-xs text-muted-foreground">{{ t('config.layers.versionHint', { platform: target.revision.platform, tenant: target.revision.tenant }) }}</p>
      </div>
    </EditorDialog>
  </div>
</template>

