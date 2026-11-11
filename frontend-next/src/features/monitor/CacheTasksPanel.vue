<script setup lang="ts">
import { computed, onScopeDispose, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import TableWorkspace from '@/components/business/TableWorkspace.vue'
import DataTable from '@/components/business/DataTable.vue'
import { useOperations } from '@/lib/operations'
import { useSubmissionPool } from '@/composables/useSubmissionPool'
import OperationStatus from '@/components/business/OperationStatus.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import PermissionButton from '@/components/business/PermissionButton.vue'
import StatusBadge from '@/components/business/StatusBadge.vue'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog'
import { useRequestGate } from '@/composables/useRequestGate'
import { usePreferences } from '@/stores/preferences'
import { useAuth } from '@/stores/auth'
import { getToken } from '@/lib/token'
import { formatInstant } from '@/lib/time'
import { confirmAction } from '@/lib/feedback'
import { cacheApi, cacheTaskEvidence, cacheCount, type CacheTask, type CacheConfirmation } from './cache'
const props = defineProps<{ selectedId: string }>(), emit = defineEmits<{ changed: [] }>()
const { t, te, locale } = useI18n(), preferences = usePreferences(), auth = useAuth(), gate = useRequestGate(), detailGate = useRequestGate()
const actions = useSubmissionPool(), cancelAction = actions.last, operations = useOperations()
const rows = ref<CacheTask[]>([]), busy = ref(false), error = ref(''), detail = ref<CacheTask>(), report = ref<CacheConfirmation>(), open = ref(false), detailBusy = ref(false), detailError = ref(''), cancelBusy = computed(() => actions.busy('monitor.cache-cancel.*')), auto = ref(false)
let timer: ReturnType<typeof setInterval> | undefined
const label = (value: string) => te('monitor.cacheGovernance.states.' + value) ? t('monitor.cacheGovernance.states.' + value) : t('monitor.cacheGovernance.unknown')
const failureLabel = (value: string) => te('monitor.cacheGovernance.failures.' + value) ? t('monitor.cacheGovernance.failures.' + value) : value ? t('monitor.cacheGovernance.unknown') : '—'
const permission = (task: CacheTask) => 'monitor:cache:' + ({ CLEAR: 'clear', PAUSE: 'pause', RESUME: 'pause', RESET_STATS: 'reset-stats', PREWARM: 'prewarm' }[task.action])
const cancellable = (task: CacheTask) => ['PREVIEW', 'QUEUED', 'RUNNING'].includes(task.state) && !task.cancelRequested
const columns = computed(() => [{ key: 'action', title: t('common.actions') }, { key: 'state', title: t('common.status') }, { key: 'reason', title: t('monitor.cacheGovernance.reason'), width: 230 }, { key: 'createdAt', title: t('monitor.cacheGovernance.createdAt'), width: 190 }, { key: 'id', title: t('monitor.cacheGovernance.taskId'), width: 300 }, { key: 'actions', title: t('common.actions') }])
async function load() {
  const current = gate(), token = getToken(); busy.value = true
  try { const data = await cacheApi.tasks(); if (current() && token === getToken()) { rows.value = data; error.value = '' } }
  catch (failure) { if (current() && token === getToken()) error.value = (failure as Error).message }
  finally { if (current()) busy.value = false }
}
async function show(id: string) {
  const current = detailGate(), token = getToken(); open.value = true; detailBusy.value = true; detailError.value = ''; report.value = undefined
  if (detail.value?.id !== id) detail.value = undefined
  const results = await Promise.allSettled([cacheApi.task(id), cacheApi.confirmation(id)])
  if (!current() || token !== getToken()) return
  const [taskResult, confirmationResult] = results
  if (taskResult.status === 'fulfilled') detail.value = taskResult.value
  else detailError.value = (taskResult.reason as Error).message
  if (confirmationResult.status === 'fulfilled') report.value = confirmationResult.value
  else detailError.value = [detailError.value, (confirmationResult.reason as Error).message].filter(Boolean).join(' · ')
  detailBusy.value = false
  try {
    const record = operations.registry.list().find(row => row.resource === 'monitor.cache-submit.' + id)
    if (record) await operations.registry.lookupNative(record.key, () => cacheApi.task(id), value => cacheTaskEvidence(value, id))
  } catch (cause) { if (current() && token === getToken()) detailError.value = (cause as Error).message }
}
function cancelEvidence(value: CacheTask, id: string) {
  if (!value || value.id !== id) throw new Error('Cache task identity mismatch')
  return { id, state: value.cancelRequested || value.state === 'CANCELLED' ? 'SUCCEEDED' as const
    : ['SUCCEEDED', 'FAILED', 'PARTIAL'].includes(value.state) ? 'FAILED_FINAL' as const : 'UNKNOWN' as const }
}
async function cancel(task: CacheTask) {
  const id = task.id, action = actions.get('monitor.cache-cancel.' + id)
  const result = await action.run({ operation: 'cache.cancel', resource: 'monitor.cache-cancel.' + id, protocol: 'NATIVE' }, async () => {
    if (!await confirmAction(t('monitor.cacheGovernance.cancelHint'), t('common.confirm'))) return
    return options => cacheApi.cancel(id, options)
  }, { success: value => cancelEvidence(value, id).state === 'SUCCEEDED' ? 'completed' : 'unknown' })
  if (result.kind === 'success') { await load(); if (open.value && detail.value?.id === id) await show(id); emit('changed') }
  else error.value = action.error.value
}
async function checkCancel() {
  const action = cancelAction.value, record = action?.record.value
  if (!action || !record) return
  const submit = record.operation === 'cache.submit', id = record.resource.slice((submit ? 'monitor.cache-submit.' : 'monitor.cache-cancel.').length)
  const result = await action.checkNative(() => cacheApi.task(id), value => submit ? cacheTaskEvidence(value, id) : cancelEvidence(value, id))
  if (result?.state === 'SUCCEEDED' || result?.state === 'FAILED_FINAL') { await load(); if (open.value && detail.value?.id === id) await show(id) }
  error.value = action.error.value
}
watch(() => props.selectedId, id => { void load(); if (id) void show(id) }, { immediate: true })
watch(auto, enabled => { if (timer) clearInterval(timer); timer = enabled ? setInterval(() => { if (document.hidden || auth.user?.locked || busy.value || detailBusy.value || cancelBusy.value) return; void load(); if (open.value && detail.value) void show(detail.value.id) }, 5000) : undefined })
watch(open, value => { if (!value) { detailGate(); detail.value = undefined; report.value = undefined; detailBusy.value = false } })
onScopeDispose(() => { if (timer) clearInterval(timer) })
</script>
<template>
  <TableWorkspace><template #tools><div class="flex flex-wrap items-center justify-between gap-3"><p class="text-sm text-muted-foreground">{{ t('monitor.cacheGovernance.taskListHint') }}</p><div class="flex items-center gap-3"><label class="flex items-center gap-2 text-sm"><input v-model="auto" type="checkbox">{{ t('monitor.cacheGovernance.autoRefresh') }}</label><Button variant="outline" :disabled="busy" @click="load">{{ t('common.refresh') }}</Button></div></div><ErrorNotice :message="error" /><OperationStatus v-if="cancelAction" :slow="cancelAction.slow.value" :unresolved="cancelAction.unresolved.value" :querying="cancelAction.querying.value" queryable @check="checkCancel" /></template><DataTable :rows="rows" :columns="columns" :loading="busy" storage-key="monitor.cache-tasks"><template #action="{ row }">{{ t('monitor.cacheGovernance.actions.' + row.action) }}</template><template #state="{ row }"><StatusBadge :label="label(row.state)" :tone="row.state === 'SUCCEEDED' ? 'success' : row.state === 'FAILED' ? 'danger' : row.state === 'UNKNOWN' ? 'warning' : 'neutral'" /><p v-if="row.cancelRequested" class="mt-1 text-xs text-muted-foreground">{{ t('monitor.cacheGovernance.cancelRequested') }}</p></template><template #createdAt="{ row }">{{ formatInstant(row.createdAt, preferences.timeZone, locale) }}</template><template #actions="{ row }"><Button variant="ghost" size="sm" @click="show(row.id)">{{ t('monitor.cacheGovernance.details') }}</Button><PermissionButton v-if="cancellable(row)" :permission="permission(row)" variant="ghost" size="sm" :disabled="actions.busy('monitor.cache-cancel.' + row.id)" @click="cancel(row)">{{ t('common.cancel') }}</PermissionButton></template></DataTable></TableWorkspace>
  <Dialog v-model:open="open"><DialogContent class="flex max-h-[90vh] flex-col overflow-hidden sm:max-w-4xl"><DialogHeader><DialogTitle>{{ t('monitor.cacheGovernance.taskDetails') }}</DialogTitle><DialogDescription>{{ t('monitor.cacheGovernance.logicalOnly') }}</DialogDescription></DialogHeader><div class="min-h-0 space-y-4 overflow-y-auto"><ErrorNotice :message="detailError" /><p v-if="detailBusy" role="status">{{ t('common.loading') }}</p><template v-if="detail"><div class="flex flex-wrap items-center justify-between gap-2"><p class="break-all text-xs text-muted-foreground">{{ detail.id }}</p><Button variant="outline" :disabled="detailBusy" @click="show(detail.id)">{{ t('common.refresh') }}</Button></div><div class="flex flex-wrap gap-3"><StatusBadge :label="label(detail.state)" /><span>{{ t('monitor.cacheGovernance.actions.' + detail.action) }}</span></div><p class="break-words text-sm">{{ detail.reason }}</p><div v-for="item in detail.items" :key="item.region" class="space-y-2 rounded-lg border p-3 text-sm"><div class="flex flex-wrap justify-between gap-2"><strong>{{ item.region }}</strong><StatusBadge :label="label(item.state)" /></div><p v-if="item.failureCode" class="text-destructive">{{ failureLabel(item.failureCode) }}</p><div v-if="item.resource" class="space-y-1 text-xs text-muted-foreground"><p class="break-all">{{ t('monitor.cacheGovernance.nativeProcess') }}{{ t('monitor.cacheGovernance.colon') }} {{ item.resource.processId }}</p><p>{{ t('monitor.cacheGovernance.nativeCounts', { affected: cacheCount(item.resource.affectedEntries, locale, t('monitor.cacheGovernance.unknown')), remaining: cacheCount(item.resource.remainingEntries, locale, t('monitor.cacheGovernance.unknown')) }) }}</p><p v-if="item.state === 'UNKNOWN'">{{ t('monitor.cacheGovernance.nativeUnknown') }}</p><p v-else-if="!item.resource.startedAt">{{ t('monitor.cacheGovernance.routeDeadline') }}{{ t('monitor.cacheGovernance.colon') }} {{ formatInstant(item.resource.routeDeadline, preferences.timeZone, locale) }}</p></div><p v-if="item.warmup" class="break-all text-xs text-muted-foreground">{{ t('monitor.cacheGovernance.warmReads') }}{{ t('monitor.cacheGovernance.colon') }} {{ cacheCount(item.warmup.checkpointedReads, locale, t('monitor.cacheGovernance.unknown')) }} {{ t('monitor.cacheGovernance.separator') }} {{ t('monitor.cacheGovernance.process') }}{{ t('monitor.cacheGovernance.colon') }} {{ item.warmup.processId || t('monitor.cacheGovernance.unknown') }}</p></div><p v-if="detail.action === 'PREWARM'" class="text-sm text-muted-foreground">{{ t('monitor.cacheGovernance.warmResultHint') }}</p></template>
    <template v-if="report"><p v-if="['NOT_APPLICABLE_RESOURCE', 'NOT_APPLICABLE_PREWARM'].includes(report.coverage)" class="text-sm text-muted-foreground">{{ label(report.coverage) }}</p><div v-else class="grid gap-3 sm:grid-cols-2"><div class="rounded-lg border p-3"><div class="text-sm text-muted-foreground">{{ t('monitor.cacheGovernance.historicalConfirmation') }}</div><div class="mt-1 font-medium">{{ report.allTargetsConfirmed ? t('monitor.cacheGovernance.allConfirmed') : label(report.coverage) }}</div></div><div class="rounded-lg border p-3"><div class="text-sm text-muted-foreground">{{ t('monitor.cacheGovernance.currentOnline') }}</div><div class="mt-1 font-medium">{{ report.allExpectedProcessesOnline ? t('monitor.cacheGovernance.allOnline') : t('monitor.cacheGovernance.notAllOnline') }}</div></div></div><p class="text-xs text-muted-foreground">{{ t('monitor.cacheGovernance.notMeasured') }}</p><div v-for="node in report.nodes" :key="node.region + '/' + node.nodeId" class="space-y-2 rounded-lg border p-3 text-sm"><div class="flex flex-wrap justify-between gap-2"><strong>{{ node.region }} {{ t('monitor.cacheGovernance.separator') }} {{ node.nodeId }}</strong><span>{{ label(node.confirmation) }} {{ t('monitor.cacheGovernance.separator') }} {{ label(node.availability) }}</span></div><p class="text-xs text-muted-foreground">{{ t('monitor.cacheGovernance.versions', { required: node.requiredRevision ?? '—', control: node.requiredControlVersion ?? '—', observed: node.observedRevision ?? '—', observedControl: node.observedControlVersion ?? '—' }) }}</p><p v-if="node.confirmedAt" class="text-xs text-muted-foreground">{{ t('monitor.cacheGovernance.confirmedAt') }}{{ t('monitor.cacheGovernance.colon') }} {{ formatInstant(node.confirmedAt, preferences.timeZone, locale) }}</p><p v-if="node.lastError" class="text-xs text-destructive">{{ failureLabel(node.lastError) }}</p></div></template></div></DialogContent></Dialog>
</template>
