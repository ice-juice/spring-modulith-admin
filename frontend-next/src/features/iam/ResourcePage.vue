<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Plus, RefreshCw, ChevronRight, X } from '@lucide/vue'
import { get, remove, type PageResult } from '@/api/http'
import { useAuth } from '@/stores/auth'
import { useLatestRequest } from '@/composables/useLatestRequest'
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
const props = defineProps<{ resourceId: string; filters?: Record<string, unknown>; preset?: Record<string, unknown>; hierarchical?: boolean; embedded?: boolean; selectOnName?: boolean }>()
const emit = defineEmits<{ changed: []; select: [row: Row] }>()
const { t, locale } = useI18n(), auth = useAuth(), route = useRoute(), router = useRouter(), request = useLatestRequest()
const resource = computed(() => resources[props.resourceId]!), rows = ref<Row[]>([]), keyword = ref(''), page = ref(1), size = ref(20), total = ref(0), editorOpen = ref(false), editing = ref<Row>(), actionError = ref(''), collapsed = ref(new Set<string>())
const columns = computed(() => [...resource.value.columns.map(column => ({ key: column.key, title: t((resource.value.module || 'iam') + '.fields.' + column.key), width: column.key === 'name' ? 190 : 145 })), { key: 'actions', title: t('common.actions'), width: props.resourceId === 'roles' ? 260 : props.resourceId === 'users' ? 230 : 150 }])
const shown = computed(() => { if (!resource.value.tree) return rows.value; function flatten(items: Row[], depth = 0): Row[] { return items.flatMap(row => [{ ...row, __depth: depth, __hasChildren: !!(row.children as Row[] | undefined)?.length }, ...(!collapsed.value.has(row.id) ? flatten((row.children || []) as Row[], depth + 1) : [])]) }; return flatten(tableTree(rows.value)) })
function canEdit(row: Row) { return props.resourceId !== 'users' || (!row.superAdmin && row.id !== auth.user?.id) }
function toggle(id: string) { const next = new Set(collapsed.value); if (next.has(id)) next.delete(id); else next.add(id); collapsed.value = next }
async function load() { await request.run(async () => {
  if (props.resourceId === 'users' && typeof route.query.focus === 'string' && !keyword.value) return [await get<Row>(resource.value.endpoint + '/' + encodeURIComponent(route.query.focus))]
  return get<Row[] | PageResult<Row>>(resource.value.endpoint, { page: page.value, size: size.value, keyword: keyword.value || undefined, roleId: props.resourceId === 'users' ? route.query.roleId : undefined, ...props.filters })
}, result => { rows.value = Array.isArray(result) ? result.filter(row => !keyword.value.trim() || ['name', 'code', 'permissionKey', 'typeKey', 'paramKey', 'value', 'groupKey'].some(key => String(row[key] || '').toLocaleLowerCase().includes(keyword.value.trim().toLocaleLowerCase()))) : result.items; total.value = Array.isArray(result) ? rows.value.length : Number(result.total) }) }
function search() { page.value = 1; void load() }
function openEditor(row?: Row) { editing.value = row; actionError.value = ''; editorOpen.value = true }
async function erase(row: Row) { if (!await confirmAction(t('common.deleteConfirm'), t('common.confirm'), true)) return; try { actionError.value = ''; await remove(resource.value.endpoint + '/' + row.id); notify(t('common.deleted')); await load(); emit('changed') } catch (cause) { actionError.value = (cause as Error).message } }
function saved() { void load(); emit('changed') }
function clearRole() { const query = { ...route.query }; delete query.roleId; delete query.roleName; void router.replace({ query }) }
watch([() => props.resourceId, () => props.filters, locale, () => route.query.focus, () => route.query.roleId], () => { page.value = 1; keyword.value = ''; void load() }, { immediate: true, deep: true })
defineExpose({ load, openEditor })
</script>
<template>
  <PageHeader v-if="!embedded" :title="t(resource.titleKey)"><slot name="header-actions" /><Button v-permission="resource.permission + ':create'" @click="openEditor()"><Plus class="size-4" />{{ t('common.create') }}</Button></PageHeader>
  <FilterBar :busy="request.loading.value" resettable @search="search" @reset="keyword = ''; search()"><Input v-model="keyword" :placeholder="t('common.search')" :aria-label="t('common.search')" /><Badge v-if="resourceId === 'users' && route.query.roleId" variant="secondary" class="gap-2">{{ t('iam.roleMembers', { name: String(route.query.roleName || route.query.roleId) }) }}<button :aria-label="t('common.reset')" @click="clearRole"><X class="size-3" /></button></Badge><template #actions><Button v-if="embedded" v-permission="resource.permission + ':create'" type="button" @click="openEditor()"><Plus class="size-4" />{{ t('common.create') }}</Button><Button type="button" variant="ghost" size="icon" :disabled="request.loading.value" :aria-label="t('common.refresh')" @click="load"><RefreshCw class="size-4" /></Button></template></FilterBar>
  <ErrorNotice :message="request.error.value || actionError" />
  <DataTable :page-sizes="[10, 20, 50, 100, 200]" :rows="shown" :columns="columns" :loading="request.loading.value" :page="page" :size="size" :total="resource.paged ? total : undefined" :storage-key="'resource-' + resourceId" :focused-id="typeof route.query.focus === 'string' ? route.query.focus : undefined" @update:page="page = $event; load()" @update:size="size = $event; search()">
    <template v-for="column in resource.columns" :key="column.key" #[column.key]="{ row }"><div class="flex items-center gap-2" :style="column.key === resource.columns[0]?.key ? { paddingLeft: Number(row.__depth || 0) * 18 + 'px' } : undefined"><button v-if="column.key === resource.columns[0]?.key && row.__hasChildren" class="shrink-0 rounded p-1 hover:bg-accent" :aria-label="String(row.name)" :aria-expanded="!collapsed.has(row.id)" @click="toggle(row.id)"><ChevronRight class="size-3.5" :class="{ 'rotate-90': !collapsed.has(row.id) }" /></button><StatusBadge v-if="column.kind === 'status'" :label="t(row[column.key] ? 'common.enabled' : 'common.disabled')" :tone="row[column.key] ? 'success' : 'neutral'" /><span v-else-if="column.kind === 'boolean'">{{ t(row[column.key] ? 'common.yes' : 'common.no') }}</span><span v-else-if="column.kind === 'scope' || column.kind === 'type'">{{ t((resource.module || 'iam') + '.choices.' + row[column.key]) }}</span><button v-else-if="column.key === resource.columns[0]?.key && selectOnName" class="truncate font-medium text-primary hover:underline" @click="emit('select', row)">{{ row[column.key] }}</button><span v-else class="block truncate" :title="String(row[column.key] ?? '')">{{ resourceId === 'parameters' && column.key === 'value' && row.secret ? t('config.masked') : row[column.key] ?? '—' }}</span></div></template>
    <template #actions="{ row }"><div class="flex flex-wrap items-center gap-0.5"><Button v-if="canEdit(row)" v-permission="resource.permission + ':update'" variant="ghost" size="sm" @click.stop="openEditor(row)">{{ t('common.edit') }}</Button><slot name="row-actions" :row="row" /><Button v-if="canEdit(row) && !row.builtin" v-permission="resource.permission + ':delete'" variant="ghost" size="sm" class="text-destructive" @click.stop="erase(row)">{{ t('common.remove') }}</Button></div></template>
  </DataTable>
  <ResourceEditor v-model="editorOpen" :resource="resource" :row="editing" :filters="filters" :preset="preset" :hierarchical="hierarchical" @saved="saved" />
</template>
