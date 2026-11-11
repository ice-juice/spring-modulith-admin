<script setup lang="ts" generic="T extends { id: string }">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { useAuth } from '@/stores/auth'
import { useTable, tableFeatures, columnVisibilityFeature } from '@tanstack/vue-table'
import { ChevronLeft, ChevronRight, ChevronsUpDown, SlidersHorizontal, LoaderCircle } from '@lucide/vue'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuCheckboxItem, DropdownMenuLabel } from '@/components/ui/dropdown-menu'
import EmptyState from './EmptyState.vue'
import type { DataColumn } from './table'
const props = withDefaults(defineProps<{ rows: T[]; columns: DataColumn[]; loading?: boolean; page?: number; size?: number; pageSizes?: number[]; total?: number; selectable?: boolean; storageKey?: string; focusedId?: string; scrollLabel?: string; scrollMode?: 'internal' | 'page' }>(), { page: 1, size: 20, pageSizes: () => [20], scrollMode: 'internal' })
const root = ref<HTMLElement>()
const emit = defineEmits<{ 'update:page': [page: number]; 'update:size': [size: number]; select: [rows: T[]]; open: [row: T]; sort: [key: string, direction: 'asc' | 'desc'] }>()
const { t } = useI18n(), compact = ref(false), selected = ref<string[]>([]), sorting = ref<{ key: string; direction: 'asc' | 'desc' }>()
const table = useTable({ features: tableFeatures({ columnVisibilityFeature }), data: computed(() => props.rows), columns: computed(() => props.columns.map(c => ({ id: c.key, accessorFn: (row: T) => (row as Record<string, unknown>)[c.key], header: c.title }))), getRowId: (row: T) => row.id })
const visible = computed(() => table.getVisibleLeafColumns())
const pages = computed(() => Math.max(1, Math.ceil((props.total || 0) / props.size)))
const allSelected = computed(() => props.rows.length > 0 && props.rows.every(r => selected.value.includes(r.id)))
const route = useRoute(), auth = useAuth()
const columnIds = computed(() => props.columns.map(column => column.key).join('|'))
const preferenceKey = computed(() => 'sma.next.table.v1.' + (auth.user?.id || 'anonymous') + '.' + (props.storageKey || route.path + '.' + columnIds.value))
watch([preferenceKey, columnIds], () => {
  try {
    const stored = JSON.parse(localStorage.getItem(preferenceKey.value) || '{}')
    compact.value = stored.version === 1 && stored.compact === true
    const visibility = Object.fromEntries(props.columns.filter(column => typeof stored.visibility?.[column.key] === 'boolean').map(column => [column.key, stored.visibility[column.key] as boolean]))
    table.setColumnVisibility(stored.version === 1 ? visibility : {})
    if (stored.version === 1 && props.columns.some(column => column.key === stored.sort?.key && column.sortable) && ['asc', 'desc'].includes(stored.sort?.direction)) { sorting.value = stored.sort; emit('sort', stored.sort.key, stored.sort.direction) }
  } catch { compact.value = false; table.setColumnVisibility({}) }
}, { immediate: true })
watch([compact, sorting, () => table.getAllLeafColumns().map(column => [column.id, column.getIsVisible()])], () => {
  try { localStorage.setItem(preferenceKey.value, JSON.stringify({ version: 1, compact: compact.value, sort: sorting.value, visibility: Object.fromEntries(table.getAllLeafColumns().map(column => [column.id, column.getIsVisible()])) })) } catch { /* Storage limits do not prevent table use. */ }
}, { deep: true })
function toggle(row?: T) { selected.value = row ? selected.value.includes(row.id) ? selected.value.filter(id => id !== row.id) : [...selected.value, row.id] : allSelected.value ? [] : props.rows.map(r => r.id); emit('select', props.rows.filter(r => selected.value.includes(r.id))) }
function sort(key: string) { const direction = sorting.value?.key === key && sorting.value.direction === 'asc' ? 'desc' : 'asc'; sorting.value = { key, direction }; emit('sort', key, direction) }
watch(() => props.rows, () => { selected.value = []; emit('select', []) })
watch([() => props.page, () => props.size, sorting], async () => { await nextTick(); root.value?.querySelector<HTMLElement>('[data-slot=scroll-area-viewport]')?.scrollTo({ top: 0 }) })
watch([() => props.focusedId, () => props.rows], async () => { if (!props.focusedId) return; await nextTick(); const row = Array.from(root.value?.querySelectorAll<HTMLElement>('[data-row-id]') || []).find(el => el.dataset.rowId === props.focusedId); row?.scrollIntoView({ block: 'nearest', inline: 'nearest' }) })
defineExpose({ table })
</script>
<template>
  <section ref="root" :data-scroll-mode="scrollMode" class="data-table overflow-hidden rounded-2xl border bg-card shadow-[var(--shadow-surface)]" :aria-busy="loading">
    <div class="data-table-toolbar flex shrink-0 items-center justify-between gap-2 border-b px-5 py-3"><span class="text-xs text-muted-foreground" aria-live="polite">{{ loading ? t('common.loading') : t('common.total', { total: total ?? rows.length }) }}</span><div class="flex gap-1"><Button variant="ghost" size="sm" @click="compact = !compact">{{ t(compact ? 'common.standardDensity' : 'common.compactDensity') }}</Button><DropdownMenu><DropdownMenuTrigger as-child><Button variant="ghost" size="icon-sm" :aria-label="t('common.columns')"><SlidersHorizontal class="size-4" /></Button></DropdownMenuTrigger><DropdownMenuContent align="end"><DropdownMenuLabel>{{ t('common.columns') }}</DropdownMenuLabel><DropdownMenuCheckboxItem v-for="column in table.getAllLeafColumns()" :key="column.id" :model-value="column.getIsVisible()" @update:model-value="column.toggleVisibility(!!$event)">{{ columns.find(c => c.key === column.id)?.title }}</DropdownMenuCheckboxItem></DropdownMenuContent></DropdownMenu></div></div>
    <ScrollArea :orientation="scrollMode === 'internal' ? 'both' : 'horizontal'" :label="scrollLabel || t('common.tableContent')" class="data-table-scroll relative max-w-full" data-scroll-region="table"><table class="w-full text-left text-sm"><thead class="data-table-head border-b bg-muted text-muted-foreground"><tr><th v-if="selectable" class="w-10 px-4 py-3"><Checkbox :model-value="allSelected ? true : selected.length ? 'indeterminate' : false" :aria-label="t('common.selectAll')" @update:model-value="toggle()" /></th><th v-for="column in visible" :key="column.id" class="whitespace-nowrap px-5 py-3 text-xs font-medium" :style="{ minWidth: (columns.find(c => c.key === column.id)?.width || 120) + 'px' }" :aria-sort="sorting?.key === column.id ? sorting.direction === 'asc' ? 'ascending' : 'descending' : undefined"><button v-if="columns.find(c => c.key === column.id)?.sortable" class="inline-flex items-center gap-1" @click="sort(column.id)">{{ columns.find(c => c.key === column.id)?.title }}<ChevronsUpDown class="size-3" /></button><template v-else>{{ columns.find(c => c.key === column.id)?.title }}</template></th></tr></thead><tbody><tr v-for="row in table.getRowModel().rows" :key="row.id" :data-row-id="row.id" class="border-b last:border-0 transition-colors hover:bg-accent/25" :class="{ 'bg-accent/60': row.id === focusedId }"><td v-if="selectable" class="px-4"><Checkbox :model-value="selected.includes(row.id)" :aria-label="t('common.selectRecord', { id: row.id })" @update:model-value="toggle(row.original)" /></td><td v-for="cell in row.getVisibleCells()" :key="cell.id" class="max-w-[32rem] px-5 align-middle" :class="[compact ? 'py-2' : 'py-4', columns.find(c => c.key === cell.column.id)?.align === 'right' ? 'text-right' : '']"><slot :name="cell.column.id" :row="row.original" :value="cell.getValue()"><span class="block truncate" :title="String(cell.getValue() ?? '')">{{ cell.getValue() ?? '—' }}</span></slot></td></tr></tbody></table><div v-if="loading && !rows.length" class="flex min-h-40 items-center justify-center gap-2 text-muted-foreground"><LoaderCircle class="size-4 animate-spin" />{{ t('common.loading') }}</div><EmptyState v-else-if="!rows.length" /></ScrollArea>
    <div v-if="total !== undefined" class="data-table-pagination flex shrink-0 flex-wrap items-center justify-between gap-3 border-t px-5 py-4"><label class="flex items-center gap-2 text-xs text-muted-foreground">{{ t('common.pageSize') }}<select v-if="pageSizes.length > 1" class="h-8 rounded-md border bg-background px-2 text-foreground" :value="size" @change="emit('update:size', Number(($event.target as HTMLSelectElement).value))"><option v-for="n in pageSizes" :key="n" :value="n">{{ n }}</option></select><span v-else class="tabular-nums">{{ size }}</span></label><div class="flex items-center gap-3"><span class="text-xs tabular-nums text-muted-foreground">{{ t('common.pageOf', { page, pages }) }}</span><Button variant="outline" size="icon-sm" :disabled="page <= 1 || loading" :aria-label="t('common.previousPage')" @click="emit('update:page', page - 1)"><ChevronLeft class="size-4" /></Button><Button variant="outline" size="icon-sm" :disabled="page >= pages || loading" :aria-label="t('common.nextPage')" @click="emit('update:page', page + 1)"><ChevronRight class="size-4" /></Button></div></div>
  </section>
</template>
