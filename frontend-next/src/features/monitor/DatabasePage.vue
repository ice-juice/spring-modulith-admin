<script setup lang="ts">
import { useRequestGate } from '@/composables/useRequestGate'
const loadGate = useRequestGate()

import PageHeader from '@/components/business/PageHeader.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import { Button } from '@/components/ui/button'

import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { get, http } from '@/api/http'
import { saveBlob } from '@/features/file/files'
interface Table {
  name: string
  remarks: string
  columns: {
    name: string
    type: string
    nullable: boolean
    defaultValue: string | null
    remarks: string
    primaryKey: boolean
  }[]
  indexes: { name: string; unique: boolean; columns: string[] }[]
}
interface Schema {
  database: string
  product: string
  version: string
  tables: Table[]
}
const { t } = useI18n(),
  schema = ref<Schema>(),
  keyword = ref(''),
  selected = ref(''),
  busy = ref(false),
  exporting = ref(false),
  error = ref('')
const filtered = computed(
    () =>
      schema.value?.tables.filter((table) =>
        (table.name + ' ' + table.remarks).toLowerCase().includes(keyword.value.toLowerCase()),
      ) || [],
  ),
  table = computed(() => filtered.value.find((table) => table.name === selected.value))
watch(filtered, (rows) => {
  if (!rows.some((table) => table.name === selected.value)) selected.value = rows[0]?.name || ''
})
async function load() {
  const isCurrent = loadGate()
  busy.value = true
  try {
    const loaded: typeof schema.value = await get('/monitor/database', undefined, { timeout: 60000 })
    if (!isCurrent()) return
    schema.value = loaded
    error.value = ''
  } catch (e) {
    if (!isCurrent()) return
    error.value = (e as Error).message
  } finally {
    if (isCurrent()) { busy.value = false }
  }
}
async function download() {
  exporting.value = true
  try {
    saveBlob(
      (await http.get<Blob>('/monitor/database/markdown', { responseType: 'blob', timeout: 60000 })).data,
      'database-schema.md',
    )
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    exporting.value = false
  }
}
onMounted(load)

import { Input } from '@/components/ui/input'
import DataTable from '@/components/business/DataTable.vue'
import EmptyState from '@/components/business/EmptyState.vue'
const columnDefs = computed(() => [{key:'name',title:t('monitor.columns.name'),width:170},{key:'type',title:t('monitor.columns.type')},{key:'nullable',title:t('monitor.columns.nullable')},{key:'defaultValue',title:t('monitor.columns.default')},{key:'primaryKey',title:t('monitor.columns.primary')},{key:'remarks',title:t('monitor.columns.remarks'),width:180}])
const indexDefs = computed(() => [{key:'name',title:t('common.name')},{key:'unique',title:t('monitor.columns.unique')},{key:'columns',title:t('monitor.columns.fields'),width:220}])
</script>
<template><PageHeader :title="t('monitor.database')" :description="schema ? schema.database + ' · ' + schema.product + ' ' + schema.version : undefined"><Button variant="outline" :disabled="busy" @click="load">{{ t('common.refresh') }}</Button><Button :disabled="exporting || busy" @click="download">{{ t('monitor.export') }}</Button></PageHeader><ErrorNotice :message="error" /><div class="grid gap-5 lg:grid-cols-[240px_minmax(0,1fr)]"><aside class="rounded-xl border bg-card p-3"><Input v-model="keyword" :placeholder="t('monitor.searchTables')" :aria-label="t('monitor.searchTables')" class="mb-3" /><div class="max-h-[65dvh] overflow-y-auto"><button v-for="item in filtered" :key="item.name" class="mb-1 block w-full truncate rounded-md px-3 py-2 text-left text-sm" :class="selected === item.name ? 'bg-accent font-medium text-accent-foreground' : 'hover:bg-muted'" :title="item.name" @click="selected = item.name">{{ item.name }}</button><EmptyState v-if="!filtered.length && !busy" /></div></aside><section class="min-w-0"><p v-if="busy" class="py-10 text-center text-sm text-muted-foreground" role="status">{{ t('common.loading') }}</p><template v-if="table"><h2 class="text-lg font-semibold">{{ table.name }}</h2><p class="mt-1 mb-4 text-sm text-muted-foreground">{{ table.remarks }}</p><DataTable :rows="table.columns.map(column => ({ ...column, id: column.name }))" :columns="columnDefs" :loading="busy"><template #nullable="{ row }">{{ t(row.nullable ? 'common.yes' : 'common.no') }}</template><template #primaryKey="{ row }">{{ t(row.primaryKey ? 'common.yes' : 'common.no') }}</template></DataTable><h3 class="mt-6 mb-3 text-base font-semibold">{{ t('monitor.indexes') }}</h3><DataTable :rows="table.indexes.map(index => ({ ...index, id: index.name }))" :columns="indexDefs"><template #unique="{ row }">{{ t(row.unique ? 'common.yes' : 'common.no') }}</template><template #columns="{ row }">{{ row.columns.join(', ') }}</template></DataTable></template><EmptyState v-else-if="!busy && !error" /></section></div></template>
