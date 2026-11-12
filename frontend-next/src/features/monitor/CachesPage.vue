<script setup lang="ts">
import { computed, onScopeDispose, reactive, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/business/PageHeader.vue'
import TableWorkspace from '@/components/business/TableWorkspace.vue'
import DataTable from '@/components/business/DataTable.vue'
import { useSubmission } from '@/composables/useSubmission'
import OperationStatus from '@/components/business/OperationStatus.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import EditorDialog from '@/components/business/EditorDialog.vue'
import PermissionButton from '@/components/business/PermissionButton.vue'
import StatusBadge from '@/components/business/StatusBadge.vue'
import FilterBar from '@/components/business/FilterBar.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useRequestGate } from '@/composables/useRequestGate'
import { useAuth } from '@/stores/auth'
import { getToken } from '@/lib/token'
import { cacheApi, cacheCount, cacheTaskEvidence, cacheProvider, filterCaches, permitsCacheAction, CacheSubmission, CacheRequestSuperseded, type CacheAction, type CacheFilter, type CacheRegion, type CacheTask, type CacheNodes, type CacheNodeSelection } from './cache'
import CacheOverviewPanel from './CacheOverviewPanel.vue'
import CacheTasksPanel from './CacheTasksPanel.vue'
import CachePoliciesPanel from './CachePoliciesPanel.vue'
import CacheRegionDetails from './CacheRegionDetails.vue'

const { t, te, locale } = useI18n(), auth = useAuth(), gate = useRequestGate(), actionGate = useRequestGate()
const submitAction = useSubmission()
submitAction.bind('monitor.cache-submit.*')
const tab = ref('directory'), rows = ref<CacheRegion[]>([]), selected = ref<CacheRegion[]>([]), busy = ref(false), error = ref('')
const nodes = ref<CacheNodes>(), node = shallowRef<CacheNodeSelection | null>(null), nodeError = ref('')
const policyEditing = ref(false)
const nodeKey = (value: CacheNodeSelection | null) => value ? value.nodeId + ':' + value.processId : ''
const nodeOptions = computed(() => nodes.value?.scope.members.map(member => ({
  key: nodeKey({ nodeId: member.nodeId, processId: member.processId || '' }), nodeId: member.nodeId, processId: member.processId || '', online: !!member.processId && member.stateAtCapture === 'ONLINE',
})) || [])
const emptyFilter = (): CacheFilter => ({ owner: '', group: '', scenario: '', kind: '', provider: '', status: '', keyword: '' })
const filter = reactive(emptyFilter()), applied = ref(emptyFilter())
const visible = computed(() => filterCaches(rows.value, applied.value))
const actions: CacheAction[] = ['CLEAR', 'PREWARM', 'PAUSE', 'RESUME', 'RESET_STATS']
const focusedTask = ref(''), focusedPolicy = ref('')
const detailsOpen = ref(false), detailsId = ref('')
const detailsRegion = computed(() => rows.value.find(row => row.id === detailsId.value))
const dialog = ref(false), actionBusy = ref(false), actionError = ref(''), action = ref<CacheAction>('CLEAR'), reason = ref(''), targetIds = ref<string[]>([])
const preview = ref<CacheTask>(), submission = shallowRef<CacheSubmission>(), uncertain = ref(false)
const opPermission = (value: CacheAction) => 'monitor:cache:' + ({ CLEAR: 'clear', PREWARM: 'prewarm', PAUSE: 'pause', RESUME: 'pause', RESET_STATS: 'reset-stats' }[value])
const label = (value: string) => te('monitor.cacheGovernance.states.' + value) ? t('monitor.cacheGovernance.states.' + value) : t('monitor.cacheGovernance.unknown')
const count = (value: unknown) => cacheCount(value, locale.value, t('monitor.cacheGovernance.unknown'))
const providerLabel = (value: string) => te('monitor.cacheGovernance.providers.' + value) ? t('monitor.cacheGovernance.providers.' + value) : value
const columns = computed(() => [
  { key: 'name', title: t('common.name'), width: 180 }, { key: 'owner', title: t('monitor.cacheGovernance.owner') },
  { key: 'group', title: t('monitor.cacheGovernance.group') }, { key: 'scenario', title: t('monitor.cacheGovernance.scenario') },
  { key: 'kind', title: t('monitor.cacheGovernance.kind') }, { key: 'provider', title: t('monitor.cacheGovernance.provider') },
  { key: 'state', title: t('common.status') }, { key: 'size', title: t('monitor.entries') }, { key: 'hits', title: t('monitor.hits') },
  { key: 'actions', title: t('common.actions'), width: 260 },
])
const fields = ['owner', 'group', 'scenario', 'kind', 'provider', 'status'] as const
function options(field: typeof fields[number]) {
  return [...new Set(rows.value.map(row => field === 'provider' ? cacheProvider(row) : field === 'status' ? row.state : row[field]))].sort()
}
async function load() {
  const current = gate(), token = getToken(), selectedNode = node.value; busy.value = true
  const [directory, inventory] = await Promise.allSettled([cacheApi.directory(selectedNode), cacheApi.nodes()])
  if (!current() || token !== getToken()) return
  if (directory.status === 'fulfilled') { rows.value = directory.value; error.value = '' } else { rows.value = []; selected.value = []; error.value = (directory.reason as Error).message }
  if (inventory.status === 'fulfilled') { nodes.value = inventory.value; nodeError.value = '' } else nodeError.value = (inventory.reason as Error).message
  busy.value = false
}
function chooseNode(event: Event) {
  if (dialog.value || policyEditing.value || actionBusy.value || submitAction.busy.value || submitAction.querying.value) return
  const key = (event.target as HTMLSelectElement).value, chosen = nodeOptions.value.find(value => value.key === key && value.online)
  if (key && !chosen) return
  node.value = chosen ? { nodeId: chosen.nodeId, processId: chosen.processId } : null
  rows.value = []; selected.value = []
  actionGate(); preview.value = undefined; submission.value?.invalidate(); submission.value = undefined
  detailsOpen.value = false; detailsId.value = ''; focusedPolicy.value = ''; void load()
}
function begin(value: CacheAction, row?: CacheRegion) {
  if (actionBusy.value || submitAction.busy.value || submitAction.querying.value) return
  const candidates = row ? [row] : selected.value.length ? selected.value : visible.value
  if (!candidates.length || !candidates.some(candidate => permitsCacheAction(candidate, value))) return
  submission.value?.invalidate(); actionGate(); submission.value = undefined; preview.value = undefined
  action.value = value; targetIds.value = candidates.map(candidate => candidate.id); reason.value = ''; uncertain.value = false; actionError.value = ''; dialog.value = true
}
function complete(view: CacheTask) { focusedTask.value = view.id; dialog.value = false; tab.value = 'tasks'; void load() }
async function save() {
  if (actionBusy.value || submitAction.busy.value) return
  if (!preview.value && !reason.value.trim()) { actionError.value = t('monitor.cacheGovernance.reasonRequired'); return }
  const current = actionGate(), token = getToken(); const owns = () => current() && token === getToken() && !auth.user?.locked
  actionBusy.value = true; actionError.value = ''
  try {
    if (!preview.value) {
      // Preview only records a frozen plan; it cannot execute or enqueue any cache mutation.
      const actionSnapshot = action.value, targets = [...targetIds.value], reasonSnapshot = reason.value.trim()
      const result = await cacheApi.preview(actionSnapshot, targets, reasonSnapshot, node.value)
      if (!owns()) return
      preview.value = result; submission.value = undefined
    } else {
      const plan = preview.value, resource = 'monitor.cache-submit.' + plan.id
      const result = await submitAction.run<CacheTask>({ operation: 'cache.submit', resource, nativeId: plan.id, resources: plan.targets.map(target => 'monitor.caches.' + target.region), protocol: 'NATIVE' }, () => options => {
        submission.value = new CacheSubmission(plan, cacheApi, owns, options.idempotencyKey)
        return submission.value.submit()
      }, { success: value => {
        const state = cacheTaskEvidence(value, plan.id).state
        return state === 'SUCCEEDED' ? 'completed' : state === 'FAILED_FINAL' ? 'failed' : state === 'PENDING' ? 'pending' : 'unknown'
      } })
      if (!owns()) return
      uncertain.value = submitAction.unresolved.value
      if (result.kind === 'success' || result.kind === 'pending' && result.value) complete(result.value!)
      else actionError.value = submitAction.error.value
    }
  } catch (failure) { if (owns() && !(failure instanceof CacheRequestSuperseded)) actionError.value = (failure as Error).message }
  finally { if (current()) actionBusy.value = false }
}
async function inspectIntent() {
  const record = submitAction.record.value
  if (!record || actionBusy.value || submitAction.querying.value) return
  const id = record.resource.slice('monitor.cache-submit.'.length)
  const result = await submitAction.checkNative(() => cacheApi.task(id), value => cacheTaskEvidence(value, id))
  uncertain.value = submitAction.unresolved.value
  if (result && result.value.state !== 'PREVIEW') complete(result.value)
  else actionError.value = submitAction.error.value || t('monitor.cacheGovernance.stillPreview')
}
function editAgain() { if (uncertain.value) return; submission.value?.invalidate(); submission.value = undefined; preview.value = undefined; actionError.value = '' }
function resetFilter() { Object.assign(filter, emptyFilter()); applied.value = emptyFilter() }
watch(locale, load, { immediate: true })
watch([() => auth.user?.id, () => auth.user?.portal, () => auth.user?.locked], () => {
  gate(); actionGate(); submission.value?.invalidate(); submission.value = undefined; preview.value = undefined
  dialog.value = false; rows.value = []; selected.value = []; reason.value = ''; error.value = ''; actionError.value = ''; focusedTask.value = ''; focusedPolicy.value = ''; tab.value = 'directory'; actionBusy.value = false
  detailsOpen.value = false; detailsId.value = ''
  node.value = null; nodes.value = undefined; nodeError.value = ''; busy.value = false; policyEditing.value = false
  if (auth.user && !auth.user.locked) void load()
})
watch(dialog, open => { if (!open) { actionGate(); submission.value?.invalidate(); actionBusy.value = false } })
onScopeDispose(() => submission.value?.invalidate())
</script>

<template>
  <Tabs v-model="tab" class="flex h-full min-h-0 flex-col gap-3">
    <div class="shrink-0">
      <PageHeader :title="t('monitor.caches')" :description="t('monitor.cacheGovernance.description')"><Button variant="outline" :disabled="busy || policyEditing || dialog" @click="load">{{ t('common.refresh') }}</Button></PageHeader>
      <p v-if="node" class="mb-2 break-all text-xs text-muted-foreground" role="status">{{ t('monitor.cacheGovernance.selectedNode') }}{{ t('monitor.cacheGovernance.colon') }} {{ node.nodeId }} {{ t('monitor.cacheGovernance.separator') }} {{ t('monitor.cacheGovernance.process') }}{{ t('monitor.cacheGovernance.colon') }} {{ node.processId }}</p>
      <OperationStatus :slow="submitAction.slow.value" :unresolved="submitAction.unresolved.value" :querying="submitAction.querying.value" queryable @check="inspectIntent" /><TabsList class="flex w-full justify-start overflow-x-auto sm:w-fit" :aria-label="t('monitor.caches')"><TabsTrigger v-for="value in ['directory', 'overview', 'tasks', 'policies']" :key="value" :value="value">{{ t('monitor.cacheGovernance.tabs.' + value) }}</TabsTrigger></TabsList>
    </div>
    <TabsContent value="directory" class="min-h-0">
      <TableWorkspace><template #tools>
        <div class="flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
          <label class="flex items-center gap-2">{{ t('monitor.cacheGovernance.selectedNode') }}<select :value="nodeKey(node)" :disabled="dialog || policyEditing || actionBusy || submitAction.busy.value || submitAction.querying.value" class="h-9 max-w-64 rounded-md border bg-background px-2 text-sm text-foreground" :aria-label="t('monitor.cacheGovernance.selectedNode')" @change="chooseNode">
            <option value="">{{ t('monitor.cacheGovernance.receivingNode') }}</option>
            <option v-if="node && !nodeOptions.some(value => value.key === nodeKey(node))" :value="nodeKey(node)" disabled>{{ node.nodeId }} {{ t('monitor.cacheGovernance.separator') }} {{ t('monitor.cacheGovernance.nodeChanged') }}</option>
            <option v-for="value in nodeOptions" :key="value.key" :value="value.key" :disabled="!value.online">{{ value.nodeId }} {{ t('monitor.cacheGovernance.separator') }} {{ value.online ? t('monitor.cacheGovernance.states.ONLINE') : t('monitor.cacheGovernance.states.OFFLINE') }}</option>
          </select></label>
          <span>{{ t('monitor.cacheGovernance.nodeSelectionHint') }}</span>
        </div>
        <ErrorNotice :message="nodeError" />
        <FilterBar resettable :busy="busy" @search="applied = { ...filter }" @reset="resetFilter">
          <label v-for="field in fields" :key="field" class="flex min-w-28 flex-col gap-1 text-xs text-muted-foreground">{{ t('monitor.cacheGovernance.' + field) }}<select v-model="filter[field]" :aria-label="t('monitor.cacheGovernance.' + field)" class="h-9 max-w-48 rounded-md border bg-background px-2 text-sm text-foreground"><option value="">{{ t('common.all') }}</option><option v-for="value in options(field)" :key="value" :value="value">{{ ['kind', 'status'].includes(field) ? label(value) : field === 'provider' ? providerLabel(value) : value }}</option></select></label>
          <Input v-model="filter.keyword" :aria-label="t('monitor.cacheGovernance.keyword')" :placeholder="t('monitor.cacheGovernance.keyword')" />
        </FilterBar>
        <div class="flex flex-wrap items-center gap-2 py-2"><span class="text-xs text-muted-foreground">{{ selected.length ? t('monitor.cacheGovernance.selected', { count: selected.length }) : t('monitor.cacheGovernance.filteredScope', { count: visible.length }) }}</span><PermissionButton v-for="value in actions" :key="value" :permission="opPermission(value)" variant="outline" size="sm" :disabled="busy || !(selected.length ? selected : visible).some(row => permitsCacheAction(row, value))" @click="begin(value)">{{ t('monitor.cacheGovernance.actions.' + value) }}</PermissionButton></div>
        <ErrorNotice :message="error" />
      </template>
        <DataTable :rows="visible" :columns="columns" :loading="busy" selectable storage-key="monitor.cache-directory" @select="selected = $event">
          <template #name="{ row }"><div class="font-medium">{{ row.name }}</div><div class="text-xs text-muted-foreground">{{ row.id }}</div></template>
          <template #kind="{ row }">{{ label(row.kind) }}</template><template #provider="{ row }">{{ providerLabel(cacheProvider(row)) }}</template>
          <template #state="{ row }"><StatusBadge :label="label(row.state)" :tone="row.state === 'ACTIVE' ? 'success' : row.state === 'UNAVAILABLE' ? 'danger' : 'neutral'" /></template>
          <template #size="{ row }">{{ count(row.observation?.snapshot.size ?? row.resource?.entries) }}</template><template #hits="{ row }">{{ count(row.observation?.snapshot.hits) }}</template>
          <template #actions="{ row }"><div class="flex flex-wrap gap-1"><Button variant="ghost" size="sm" @click="detailsId = row.id; detailsOpen = true">{{ t('monitor.cacheGovernance.details') }}</Button><PermissionButton v-if="permitsCacheAction(row, 'CLEAR')" permission="monitor:cache:clear" variant="ghost" size="sm" @click="begin('CLEAR', row)">{{ t('monitor.clear') }}</PermissionButton><PermissionButton v-if="permitsCacheAction(row, 'PREWARM')" permission="monitor:cache:prewarm" variant="ghost" size="sm" @click="begin('PREWARM', row)">{{ t('monitor.cacheGovernance.actions.PREWARM') }}</PermissionButton><PermissionButton v-if="row.allowedOperations.includes('PAUSE')" permission="monitor:cache:pause" variant="ghost" size="sm" @click="begin(row.state === 'PAUSED' ? 'RESUME' : 'PAUSE', row)">{{ t('monitor.cacheGovernance.actions.' + (row.state === 'PAUSED' ? 'RESUME' : 'PAUSE')) }}</PermissionButton><Button v-if="row.kind === 'DATA' && auth.hasPermission(row.managePermission)" variant="ghost" size="sm" @click="focusedPolicy = row.id; tab = 'policies'">{{ t('monitor.cacheGovernance.tabs.policies') }}</Button><span v-if="!row.allowedOperations.length" class="text-xs text-muted-foreground">{{ t('monitor.cacheGovernance.readonly') }}</span></div></template>
        </DataTable>
      </TableWorkspace>
    </TabsContent>
    <TabsContent value="overview" class="min-h-0"><CacheOverviewPanel :regions="rows" :node="node" /></TabsContent>
    <TabsContent value="tasks" class="min-h-0"><CacheTasksPanel :selected-id="focusedTask" @changed="load" /></TabsContent>
    <TabsContent value="policies" class="min-h-0"><CachePoliciesPanel :regions="rows" :selected-id="focusedPolicy" :node="node" @changed="load" @editing="policyEditing = $event" /></TabsContent>
  </Tabs>
  <CacheRegionDetails v-model="detailsOpen" :region="detailsRegion" />
  <EditorDialog v-model="dialog" :title="t('monitor.cacheGovernance.actions.' + action)" :description="t('monitor.cacheGovernance.previewHint')" :busy="actionBusy" :dirty="!!reason" wide @save="save">
    <div class="space-y-4"><ErrorNotice :message="actionError" /><p v-if="uncertain" class="rounded-lg border bg-accent p-3 text-sm" role="status">{{ t('monitor.cacheGovernance.uncertain') }}</p>
      <label class="block space-y-2 text-sm"><span>{{ t('monitor.cacheGovernance.reason') }}</span><textarea v-model="reason" rows="3" maxlength="256" :disabled="!!preview || actionBusy" class="w-full rounded-md border bg-background p-3" /></label>
      <p class="text-sm text-muted-foreground">{{ t('monitor.cacheGovernance.requested', { count: targetIds.length }) }}</p>
      <template v-if="preview"><p class="font-medium">{{ t('monitor.cacheGovernance.acceptedTargets', { count: preview.targets.length }) }}</p><ul class="space-y-2 text-sm"><li v-for="target in preview.targets" :key="target.region" class="rounded-md border p-3"><span class="font-medium">{{ rows.find(row => row.id === target.region)?.name || target.region }}</span><span class="ml-2 text-muted-foreground">{{ target.region }} {{ t('monitor.cacheGovernance.separator') }} {{ label(target.scope) }}</span><p v-if="target.resource" class="mt-1 break-all text-xs text-muted-foreground">{{ t('monitor.cacheGovernance.nativeTarget', { node: target.resource.nodeId, process: target.resource.processId }) }}</p><p v-if="target.warmup" class="mt-1 text-xs text-muted-foreground">{{ t('monitor.cacheGovernance.warmBudget', { page: target.warmup.limits.pageSize, total: target.warmup.limits.maxItems, rate: target.warmup.limits.itemsPerSecond }) }}</p></li></ul><p v-if="preview.skipped.length" class="font-medium">{{ t('monitor.cacheGovernance.skipped') }}</p><ul class="space-y-1 text-sm text-muted-foreground"><li v-for="skipped in preview.skipped" :key="skipped.region">{{ skipped.region }} — {{ label(skipped.reason) }}</li></ul><p class="text-xs text-muted-foreground">{{ t('monitor.cacheGovernance.logicalOnly') }}</p></template>
    </div>
    <template #footer><Button variant="outline" :disabled="actionBusy" @click="dialog = false">{{ t('common.cancel') }}</Button><Button v-if="preview && !uncertain" variant="outline" :disabled="actionBusy" @click="editAgain">{{ t('monitor.cacheGovernance.editAgain') }}</Button><Button v-if="uncertain" variant="outline" :disabled="actionBusy" @click="inspectIntent">{{ t('monitor.cacheGovernance.inspectOriginal') }}</Button><Button :disabled="actionBusy || uncertain || (!!preview && !preview.targets.length)" @click="save">{{ actionBusy ? t('common.loading') : preview ? t('monitor.cacheGovernance.submit') : t('monitor.cacheGovernance.preview') }}</Button></template>
  </EditorDialog>
</template>
