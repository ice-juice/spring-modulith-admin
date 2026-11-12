<script setup lang="ts">
import { computed, onScopeDispose, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import TableWorkspace from '@/components/business/TableWorkspace.vue'
import DataTable from '@/components/business/DataTable.vue'
import { useSubmission } from '@/composables/useSubmission'
import OperationStatus from '@/components/business/OperationStatus.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import EditorDialog from '@/components/business/EditorDialog.vue'
import PermissionButton from '@/components/business/PermissionButton.vue'
import { useRequestGate } from '@/composables/useRequestGate'
import { useAuth } from '@/stores/auth'
import { usePreferences } from '@/stores/preferences'
import { getToken } from '@/lib/token'
import { formatInstant } from '@/lib/time'
import { confirmAction } from '@/lib/feedback'
import { cacheApi, cacheTaskEvidence, cacheCount, durationMillis, type CacheRegion, type CacheHistory, type CachePolicyState, type CachePolicyInput, type CacheNodeSelection } from './cache'
const props = defineProps<{ regions: CacheRegion[]; selectedId: string; node?: CacheNodeSelection | null }>(), emit = defineEmits<{ changed: []; editing: [value: boolean] }>()
const { t, te, locale } = useI18n(), auth = useAuth(), preferences = usePreferences(), gate = useRequestGate(), writeGate = useRequestGate()
const submission = useSubmission()
const regionId = ref(''), state = ref<CachePolicyState>(), history = ref<CacheHistory[]>([]), busy = ref(false), error = ref(''), editor = ref(false), saving = submission.busy, editError = ref(''), needsReview = ref(false)
const input = reactive<CachePolicyInput>({ expectedVersion: '', reason: '', ttlMillis: 0, negativeTtlMillis: 0, maxEntries: '', maxBytes: '', maxEntryBytes: 0 })
const editableRegions = computed(() => props.regions.filter(row => row.kind === 'DATA' && auth.hasPermission(row.managePermission)))
const region = computed(() => props.regions.find(row => row.id === regionId.value))
const canEdit = computed(() => !!state.value && region.value?.allowedOperations.includes('EDIT_POLICY'))
const fields = ['ttlMillis', 'negativeTtlMillis', 'maxEntries', 'maxBytes', 'maxEntryBytes'] as const
const columns = computed(() => [{ key: 'occurredAt', title: t('monitor.cacheGovernance.createdAt'), width: 190 }, { key: 'action', title: t('common.actions') }, { key: 'actorId', title: t('monitor.cacheGovernance.actor') }, { key: 'reason', title: t('monitor.cacheGovernance.reason'), width: 220 }, { key: 'versions', title: t('monitor.cacheGovernance.controlVersion') }])
const policyRows = computed(() => {
  if (!state.value) return []
  const mapping = { ttlMillis: 'ttl', negativeTtlMillis: 'negativeTtl', maxEntries: 'maxEntries', maxBytes: 'maxBytes', maxEntryBytes: 'maxEntryBytes' } as const
  return fields.map(field => {
    const key = mapping[field], duration = field === 'ttlMillis' || field === 'negativeTtlMillis'
    const display = (value: string | number | undefined) => value === undefined ? t('monitor.cacheGovernance.unknown') : cacheCount(duration ? durationMillis(value) : value, locale.value, t('monitor.cacheGovernance.unknown'))
    return { id: field, name: t('monitor.cacheGovernance.policyFields.' + field), declared: display(region.value?.observation?.policy.declared[key]), deployment: display(state.value!.deploymentPolicy[key]), effective: display(state.value!.effectivePolicy[key]), source: region.value?.observation?.policy.sources[key] || 'UNAVAILABLE' }
  })
})
const label = (value: string) => te('monitor.cacheGovernance.states.' + value) ? t('monitor.cacheGovernance.states.' + value) : t('monitor.cacheGovernance.unknown')
async function load() {
  const id = regionId.value, current = gate(), token = getToken(); state.value = undefined; history.value = []; error.value = ''
  if (!id) return
  busy.value = true
  const results = await Promise.allSettled([cacheApi.policy(id, props.node), cacheApi.history(id)])
  if (!current() || token !== getToken()) return
  const [policy, audit] = results
  if (policy.status === 'fulfilled') state.value = policy.value; else error.value = (policy.reason as Error).message
  if (audit.status === 'fulfilled') history.value = audit.value; else error.value = [error.value, (audit.reason as Error).message].filter(Boolean).join(' · ')
  busy.value = false
}
function edit() {
  if (!state.value || saving.value || submission.querying.value) return
  submission.resetView(); submission.bind('monitor.caches.' + regionId.value)
  const policy = state.value.effectivePolicy
  const ttl = durationMillis(policy.ttl), negative = durationMillis(policy.negativeTtl)
  if (ttl === null || negative === null) { error.value = t('monitor.cacheGovernance.policyUnavailable'); return }
  Object.assign(input, { expectedVersion: state.value.version, reason: '', ttlMillis: ttl, negativeTtlMillis: negative, maxEntries: policy.maxEntries, maxBytes: policy.maxBytes, maxEntryBytes: policy.maxEntryBytes })
  needsReview.value = false; editError.value = ''; editor.value = true
}
async function save(reset = false) {
  if (!state.value || saving.value || submission.unresolved.value || needsReview.value) return
  const current = writeGate(), token = getToken(), id = regionId.value, snapshot = { ...input, reason: input.reason.trim() }, selectedNode = props.node ? { ...props.node } : null
  editError.value = ''
  const result = await submission.run({ operation: reset ? 'cache.policy-reset' : 'cache.policy-save', resource: 'monitor.caches.' + id, protocol: 'LOCAL' }, async () => {
    if (!snapshot.reason) { editError.value = t('monitor.cacheGovernance.reasonRequired'); return }
    if (reset && !await confirmAction(t('monitor.cacheGovernance.resetPolicyHint'), t('common.confirm'))) return
    return options => reset ? cacheApi.resetPolicy(id, snapshot.expectedVersion, snapshot.reason, options, selectedNode) : cacheApi.savePolicy(id, snapshot, options, selectedNode)
  })
  if (!current() || token !== getToken()) return
  if (result.kind === 'success' || result.kind === 'completed') { editor.value = false; emit('changed'); await load() }
  else if (result.kind !== 'cancelled') {
    editError.value = submission.error.value; needsReview.value = !submission.unresolved.value
    try { const latest = await cacheApi.policy(id, selectedNode); if (current() && token === getToken()) state.value = latest } catch { /* Keep original input and expectedVersion. */ }
  }
}
async function check() {
  const current = writeGate(), token = getToken()
  if (submission.record.value?.protocol === 'NATIVE') {
    const id = submission.record.value.resource.slice('monitor.cache-submit.'.length)
    const result = await submission.checkNative(() => cacheApi.task(id), value => cacheTaskEvidence(value, id))
    if (!current() || token !== getToken()) return
    if (result?.state === 'SUCCEEDED' || result?.state === 'FAILED_FINAL') { needsReview.value = true; editError.value = t('common.operation.previousFinished'); await load() }
    else editError.value = submission.error.value
    return
  }
  const state = await submission.check()
  if (!current() || token !== getToken()) return
  if (state === 'SUCCEEDED') { editor.value = false; emit('changed'); await load() }
  else { editError.value = submission.error.value; if (state === 'FAILED_FINAL') needsReview.value = true }
}
watch(() => props.selectedId, value => { if (value) regionId.value = value }, { immediate: true })
watch(editableRegions, value => { if (!value.some(row => row.id === regionId.value)) regionId.value = value[0]?.id || '' }, { immediate: true })
watch(regionId, () => { writeGate(); editor.value = false; void load() }, { immediate: true })
watch(() => props.node, () => { writeGate(); void load() })
watch(editor, value => emit('editing', value))
onScopeDispose(() => emit('editing', false))
</script>
<template><TableWorkspace><template #tools><div class="flex flex-wrap items-end gap-3"><label class="flex min-w-48 flex-col gap-1 text-sm">{{ t('monitor.cacheGovernance.region') }}<select v-model="regionId" :aria-label="t('monitor.cacheGovernance.region')" :disabled="saving || submission.unresolved.value" class="h-9 max-w-full rounded-md border bg-background px-2"><option v-for="row in editableRegions" :key="row.id" :value="row.id">{{ row.name }} {{ t('monitor.cacheGovernance.separator') }} {{ row.id }}</option></select></label><Button variant="outline" :disabled="busy || !regionId" @click="load">{{ t('common.refresh') }}</Button><PermissionButton v-if="canEdit" permission="monitor:cache:policy" :disabled="busy" @click="edit">{{ t('monitor.cacheGovernance.editPolicy') }}</PermissionButton></div><ErrorNotice :message="error" /><p class="my-3 text-sm text-muted-foreground">{{ t('monitor.cacheGovernance.policyHint') }}</p><div v-if="state" class="overflow-x-auto rounded-lg border bg-card"><table class="w-full text-left text-xs"><thead class="bg-muted"><tr><th class="p-3">{{ t('monitor.cacheGovernance.policyField') }}</th><th class="p-3">{{ t('monitor.cacheGovernance.declared') }}</th><th class="p-3">{{ t('monitor.cacheGovernance.deployment') }}</th><th class="p-3">{{ t('monitor.cacheGovernance.effective') }}</th><th class="p-3">{{ t('monitor.cacheGovernance.source') }}</th></tr></thead><tbody><tr v-for="row in policyRows" :key="row.id" class="border-t"><td class="whitespace-nowrap p-3">{{ row.name }}</td><td class="p-3 tabular-nums">{{ row.declared }}</td><td class="p-3 tabular-nums">{{ row.deployment }}</td><td class="p-3 tabular-nums">{{ row.effective }}</td><td class="p-3">{{ row.source === 'DECLARED' ? t('monitor.cacheGovernance.declared') : label(row.source) }}</td></tr></tbody></table></div><p v-if="state" class="my-2 text-xs text-muted-foreground">{{ t('monitor.cacheGovernance.controlVersion') }}{{ t('monitor.cacheGovernance.colon') }} {{ state.version }} {{ t('monitor.cacheGovernance.separator') }} {{ label(state.paused ? 'PAUSED' : 'ACTIVE') }} {{ t('monitor.cacheGovernance.separator') }} {{ t('monitor.cacheGovernance.auditHint') }}</p></template><DataTable :rows="history" :columns="columns" :loading="busy" storage-key="monitor.cache-policy-history"><template #occurredAt="{ row }">{{ formatInstant(row.occurredAt, preferences.timeZone, locale) }}</template><template #action="{ row }">{{ label(row.action) }}</template><template #versions="{ row }">{{ row.before.version }} {{ t('monitor.cacheGovernance.arrow') }} {{ row.after.version }}</template></DataTable></TableWorkspace>
  <EditorDialog v-model="editor" :title="t('monitor.cacheGovernance.editPolicy')" :description="t('monitor.cacheGovernance.policyHint')" :dirty="!!input.reason" :busy="saving" @save="save()"><div class="space-y-4"><ErrorNotice :message="editError" /><OperationStatus :slow="submission.slow.value" :unresolved="submission.unresolved.value" :querying="submission.querying.value" queryable @check="check" /><p v-if="needsReview" class="rounded-md border bg-accent p-3 text-sm">{{ t('monitor.cacheGovernance.policyReview') }}</p><p class="text-xs text-muted-foreground">{{ t('monitor.cacheGovernance.controlVersion') }}{{ t('monitor.cacheGovernance.colon') }} {{ input.expectedVersion }}</p><div class="grid gap-3 sm:grid-cols-2"><label v-for="field in fields" :key="field" class="space-y-1 text-sm"><span>{{ t('monitor.cacheGovernance.policyFields.' + field) }}</span><Input v-model="input[field]" inputmode="numeric" :disabled="saving || submission.unresolved.value || needsReview" /></label></div><label class="block space-y-1 text-sm"><span>{{ t('monitor.cacheGovernance.reason') }}</span><textarea v-model="input.reason" maxlength="256" rows="3" :disabled="saving || submission.unresolved.value" class="w-full rounded-md border bg-background p-3" /></label></div><template #footer><Button variant="outline" :disabled="saving" @click="editor = false">{{ t('common.cancel') }}</Button><Button v-if="!needsReview" variant="outline" :disabled="saving || submission.unresolved.value" @click="save(true)">{{ t('monitor.cacheGovernance.resetPolicy') }}</Button><Button v-if="needsReview" variant="outline" :disabled="saving || submission.unresolved.value" @click="editor = false; load()">{{ t('monitor.cacheGovernance.reviewLatest') }}</Button><Button :disabled="saving || submission.unresolved.value || needsReview" @click="save()">{{ t('common.save') }}</Button></template></EditorDialog>
</template>
