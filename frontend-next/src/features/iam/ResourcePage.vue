<script setup lang="ts">
import PermissionButton from '@/components/business/PermissionButton.vue'

import AppIcon from '@/components/business/AppIcon.vue'
import TableWorkspace from '@/components/business/TableWorkspace.vue'
import RowActions from '@/components/business/RowActions.vue'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Plus, RefreshCw, ChevronRight, X } from '@lucide/vue'
import { get, remove, type PageResult } from '@/api/http'
import { useAuth } from '@/stores/auth'
import { useLatestRequest } from '@/composables/useLatestRequest'
import { useSubmissionPool } from '@/composables/useSubmissionPool'
import OperationStatus from '@/components/business/OperationStatus.vue'
import { notify, confirmAction } from '@/lib/feedback'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import PageHeader from '@/components/business/PageHeader.vue'
import FilterBar from '@/components/business/FilterBar.vue'
import DataTable from '@/components/business/DataTable.vue'
import StatusBadge from '@/components/business/StatusBadge.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import ResourceEditor from './ResourceEditor.vue'
import { resources, tableTree, type Row } from './resources'
const props = defineProps<{ resourceId: string; eyebrow?: string; description?: string; filters?: Record<string, unknown>; preset?: Record<string, unknown>; hierarchical?: boolean; embedded?: boolean; selectOnName?: boolean }>()
const emit = defineEmits<{ changed: []; select: [row: Row] }>()
const { t, locale } = useI18n(), auth = useAuth(), route = useRoute(), router = useRouter(), request = useLatestRequest()
const deletions = useSubmissionPool(), deletion = deletions.last
const resource = computed(() => resources[props.resourceId]!), rows = ref<Row[]>([]), keyword = ref(''), page = ref(1), size = ref(20), total = ref(0), editorOpen = ref(false), editing = ref<Row>(), actionError = ref(''), collapsed = ref(new Set<string>())
const resourceType = ref(''), resourceStatus = ref('')
const resourceTypes = computed(() => resource.value.fields.find(field => field.key === 'type')?.choices || [])
const filteredRows = computed(() => props.resourceId === 'permissions' ? rows.value.filter(row =>
  (!resourceType.value || row.type === resourceType.value) &&
  (!resourceStatus.value || row.enabled === (resourceStatus.value === 'enabled')),
) : rows.value)
const treeRows = computed(() => resource.value.tree ? tableTree(filteredRows.value) : [])
const branchIds = computed(() => {
  function collect(items: Row[]): string[] {
    return items.flatMap(row => {
      const children = (row.children || []) as Row[]
      return children.length ? [row.id, ...collect(children)] : []
    })
  }
  return collect(treeRows.value)
})
const canExpandAll = computed(() => branchIds.value.some(id => collapsed.value.has(id)))
const canCollapseAll = computed(() => branchIds.value.some(id => !collapsed.value.has(id)))
const columns = computed(() => [...resource.value.columns.map(column => ({ key: column.key, title: t((resource.value.module || 'iam') + '.fields.' + column.key), width: column.key === 'name' ? 190 : 145 })), { key: 'actions', title: t('common.actions'), width: props.resourceId === 'roles' ? 260 : props.resourceId === 'users' ? 230 : 150 }])
const shown = computed(() => { if (!resource.value.tree) return filteredRows.value; function flatten(items: Row[], depth = 0): Row[] { return items.flatMap(row => [{ ...row, __depth: depth, __hasChildren: !!(row.children as Row[] | undefined)?.length }, ...(!collapsed.value.has(row.id) ? flatten((row.children || []) as Row[], depth + 1) : [])]) }; return flatten(treeRows.value) })
function canEdit(row: Row) { return props.resourceId !== 'users' || (!row.superAdmin && row.id !== auth.user?.id) }
function toggle(id: string) { const next = new Set(collapsed.value); if (next.has(id)) next.delete(id); else next.add(id); collapsed.value = next }
function expandAll() { collapsed.value = new Set() }
function collapseAll() { collapsed.value = new Set(branchIds.value) }
async function load() {
  const text = keyword.value, endpoint = resource.value.endpoint,
    focus = props.resourceId === 'users' && typeof route.query.focus === 'string' && !text ? route.query.focus : undefined,
    params = { page: page.value, size: size.value, keyword: text || undefined, roleId: props.resourceId === 'users' ? route.query.roleId : undefined, ...props.filters }
  await request.run(async signal => {
    if (focus) return [await get<Row>(endpoint + '/' + encodeURIComponent(focus), undefined, { signal })]
    return get<Row[] | PageResult<Row>>(endpoint, params, { signal })
  }, result => { rows.value = Array.isArray(result) ? result.filter(row => !text.trim() || ['name', 'code', 'permissionKey', 'typeKey', 'paramKey', 'value', 'groupKey'].some(key => String(row[key] || '').toLocaleLowerCase().includes(text.trim().toLocaleLowerCase()))) : result.items; total.value = Array.isArray(result) ? rows.value.length : Number(result.total) }, JSON.stringify([endpoint, focus, params, locale.value]))
}
function search() { page.value = 1; if (props.resourceId === 'permissions') collapsed.value = new Set(); void load() }
function resetFilters() { keyword.value = ''; resourceType.value = ''; resourceStatus.value = ''; search() }
function openEditor(row?: Row) { editing.value = row; actionError.value = ''; editorOpen.value = true }
async function erase(row: Row) {
  actionError.value = ''
  const endpoint = resource.value.endpoint, id = row.id, scope = endpoint.slice(1).replaceAll('/', '.') + '.' + id, action = deletions.get(scope)
  const result = await action.run({ operation: resource.value.id + '.delete', resource: scope, protocol: 'LOCAL' }, async () => {
    if (!await confirmAction(t('common.deleteConfirm'), t('common.confirm'), true)) return
    return options => remove(endpoint + '/' + id, options)
  })
  if (result.kind === 'success' || result.kind === 'completed') { notify(t(result.kind === 'completed' ? 'common.operation.completed' : 'common.deleted')); request.cancel(); await load(); emit('changed') }
  else actionError.value = action.error.value
}
async function checkDeletion() { const state = await deletion.value?.check(); actionError.value = deletion.value?.error.value || ''; if (state === 'SUCCEEDED') { request.cancel(); await load(); emit('changed') } }
function saved() { request.cancel(); void load(); emit('changed') }
function clearRole() { const query = { ...route.query }; delete query.roleId; delete query.roleName; void router.replace({ query }) }
watch([() => props.resourceId, () => props.filters, locale, () => route.query.focus, () => route.query.roleId], () => { page.value = 1; keyword.value = ''; void load() }, { immediate: true, deep: true })
defineExpose({ load, openEditor })
watch(() => props.resourceId, () => { resourceType.value = ''; resourceStatus.value = ''; collapsed.value = new Set() })

</script>
<template>
  <TableWorkspace><template #tools>
  <PageHeader v-if="!embedded" :title="t(resource.titleKey)" :eyebrow="eyebrow" :description="description"><slot name="header-actions" /><PermissionButton :permission="resource.permission + ':create'" @click="openEditor()"><Plus class="size-4" />{{ t('common.create') }}</PermissionButton></PageHeader>
  <slot name="overview" :rows="rows" :total="total" :loading="request.loading.value" />
  <FilterBar :busy="request.loading.value" resettable @search="search" @reset="resetFilters">
    <Input v-model="keyword" :placeholder="t(resourceId === 'permissions' ? 'iam.permissionSearch' : 'common.search')" :aria-label="t('common.search')" />
    <template v-if="resourceId === 'permissions'">
      <label class="flex w-full flex-col gap-1.5 text-xs text-muted-foreground sm:w-40">{{ t('iam.fields.type') }}
        <select v-model="resourceType" class="h-9 w-full rounded-md border border-input bg-background px-3 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" @change="search">
          <option value="">{{ t('iam.allResourceTypes') }}</option>
          <option v-for="type in resourceTypes" :key="type" :value="type">{{ t('iam.choices.' + type) }}</option>
        </select>
      </label>
      <label class="flex w-full flex-col gap-1.5 text-xs text-muted-foreground sm:w-36">{{ t('iam.fields.enabled') }}
        <select v-model="resourceStatus" class="h-9 w-full rounded-md border border-input bg-background px-3 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" @change="search">
          <option value="">{{ t('iam.allResourceStatuses') }}</option>
          <option value="enabled">{{ t('common.enabled') }}</option>
          <option value="disabled">{{ t('common.disabled') }}</option>
        </select>
      </label>
    </template>
    <Badge v-if="resourceId === 'users' && route.query.roleId" variant="secondary" class="gap-2">{{ t('iam.roleMembers', { name: String(route.query.roleName || route.query.roleId) }) }}<button :aria-label="t('common.reset')" @click="clearRole"><X class="size-3" /></button></Badge>
    <template #actions>
      <template v-if="resource.tree">
        <Button type="button" variant="ghost" size="sm" :disabled="request.loading.value || !canExpandAll" @click="expandAll">{{ t('common.expandAll') }}</Button>
        <Button type="button" variant="ghost" size="sm" :disabled="request.loading.value || !canCollapseAll" @click="collapseAll">{{ t('common.collapseAll') }}</Button>
      </template>
      <PermissionButton v-if="embedded" :permission="resource.permission + ':create'" type="button" @click="openEditor()"><Plus class="size-4" />{{ t('common.create') }}</PermissionButton><Button type="button" variant="ghost" size="icon" :disabled="request.loading.value" :aria-label="t('common.refresh')" @click="load"><RefreshCw class="size-4" /></Button>
    </template>
  </FilterBar>
  <ErrorNotice :message="request.error.value || actionError" />
  <OperationStatus v-if="deletion" :slow="deletion.slow.value" :unresolved="deletion.unresolved.value" :querying="deletion.querying.value" queryable @check="checkDeletion" />
  </template><DataTable :page-sizes="[10, 20, 50, 100, 200]" :rows="shown" :columns="columns" :loading="request.loading.value" :page="page" :size="size" :total="resource.paged ? total : undefined" :storage-key="'resource-' + resourceId" :focused-id="typeof route.query.focus === 'string' ? route.query.focus : undefined" @update:page="page = $event; load()" @update:size="size = $event; search()">
    <template v-for="column in resource.columns" :key="column.key" #[column.key]="{ row }"><div class="flex items-center gap-2" :style="column.key === resource.columns[0]?.key ? { paddingLeft: Number(row.__depth || 0) * 18 + 'px' } : undefined"><button v-if="column.key === resource.columns[0]?.key && row.__hasChildren" class="shrink-0 rounded p-1 hover:bg-accent" :aria-label="String(row.name)" :aria-expanded="!collapsed.has(row.id)" @click="toggle(row.id)"><ChevronRight class="size-3.5" :class="{ 'rotate-90': !collapsed.has(row.id) }" /></button><AppIcon v-if="column.key === 'name' && resourceId === 'permissions' && row.icon" :name="String(row.icon)" :size="22" /><template v-if="column.key === 'icon'"><AppIcon v-if="row.icon" :name="String(row.icon)" :size="24" /><span class="truncate text-xs text-muted-foreground">{{ row.icon === 'none' ? t('common.icons.none') : row.icon || '—' }}</span></template><StatusBadge v-else-if="column.kind === 'status'" :label="t(row[column.key] ? 'common.enabled' : 'common.disabled')" :tone="row[column.key] ? 'success' : 'neutral'" /><span v-else-if="column.kind === 'boolean'">{{ t(row[column.key] ? 'common.yes' : 'common.no') }}</span><span v-else-if="column.kind === 'scope' || column.kind === 'type'">{{ t((resource.module || 'iam') + '.choices.' + row[column.key]) }}</span><button v-else-if="column.key === resource.columns[0]?.key && selectOnName" class="truncate font-medium text-primary hover:underline" @click="emit('select', row)">{{ row[column.key] }}</button><span v-else-if="resourceId === 'users' && column.key === 'name'" class="flex min-w-0 items-center gap-3"><span aria-hidden="true" class="grid size-9 shrink-0 place-items-center rounded-full bg-accent text-sm font-medium text-accent-foreground">{{ String(row.name || row.username || '').slice(0,1) }}</span><span class="truncate font-medium">{{ row.name }}</span></span><span v-else class="block truncate" :title="String(row[column.key] ?? '')">{{ resourceId === 'parameters' && column.key === 'value' && row.secret ? t('config.masked') : row[column.key] ?? '—' }}</span></div></template>
    <template #actions="{ row }"><RowActions><PermissionButton v-if="canEdit(row)" :permission="resource.permission + ':update'" variant="ghost" size="sm" @click.stop="openEditor(row)">{{ t('common.edit') }}</PermissionButton><slot name="row-actions" :row="row" /><PermissionButton :disabled="deletions.busy(resource.endpoint.slice(1).replaceAll('/', '.') + '.' + row.id)" v-if="canEdit(row) && !row.builtin" :permission="resource.permission + ':delete'" variant="ghost" size="sm" class="text-destructive" @click.stop="erase(row)">{{ t('common.remove') }}</PermissionButton></RowActions></template>
  </DataTable></TableWorkspace>
  <ResourceEditor v-model="editorOpen" :resource="resource" :row="editing" :filters="filters" :preset="preset" :hierarchical="hierarchical" @saved="saved" />
</template>
