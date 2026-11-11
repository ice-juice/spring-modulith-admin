<script setup lang="ts">
import PermissionButton from '@/components/business/PermissionButton.vue'

import TableWorkspace from '@/components/business/TableWorkspace.vue'
import { useRequestGate } from '@/composables/useRequestGate'
const loadGate = useRequestGate()

import PageHeader from '@/components/business/PageHeader.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'

import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { confirmOrCancel } from '@/lib/feedback'
import { get, remove, type PageResult } from '@/api/http'
import { formatInstant } from '@/lib/time'
import { usePreferences } from '@/stores/preferences'
interface Statement {
  id: string
  command: string
  calls: string
  errors: string
  rows: string
  slowCalls: string
  averageMillis: number
  maxMillis: number
  lastAt: string
}
const { t, locale } = useI18n(),
  preferences = usePreferences(),
  rows = ref<Statement[]>([]),
  keyword = ref(''),
  page = ref(1),
  total = ref(0),
  busy = ref(false),
  error = ref(''),
  sort = ref('maxMillis'),
  direction = ref('desc')
async function load() {
  const isCurrent = loadGate()
  busy.value = true
  try {
    const result = await get<PageResult<Statement>>('/monitor/sql', {
      page: page.value,
      size: 20,
      keyword: keyword.value,
      sort: sort.value,
      direction: direction.value,
    })
    if (!isCurrent()) return
    rows.value = result.items
    total.value = Number(result.total)
    error.value = ''
  } catch (e) {
    if (!isCurrent()) return
    error.value = (e as Error).message
  } finally {
    if (isCurrent()) { busy.value = false }
  }
}
async function reset() {
  if (actionBusy.value) return
  actionBusy.value = true
  try {
  try {
    await confirmOrCancel(t('monitor.sqlResetConfirm'), t('common.confirm'), {
      type: 'warning',
    })
    await remove('/monitor/sql')
    await load()
  } catch (e) {
    if (e instanceof Error) error.value = e.message
  }

  } finally { actionBusy.value = false }
}
function search() {
  page.value = 1
  void load()
}
function changeSort(value: { prop: string; order: string | null }) {
  sort.value = value.prop || 'maxMillis'
  direction.value = value.order === 'ascending' ? 'asc' : 'desc'
  search()
}
onMounted(load)

import { computed } from 'vue'
import { Input } from '@/components/ui/input'
import FilterBar from '@/components/business/FilterBar.vue'
import DataTable from '@/components/business/DataTable.vue'
const columns = computed(() => [{key:'id',title:t('monitor.statement'),width:380},{key:'command',title:t('monitor.command')},{key:'calls',title:t('monitor.calls'),sortable:true},{key:'errors',title:t('monitor.errors'),sortable:true},{key:'rows',title:t('monitor.resultRows')},{key:'slowCalls',title:t('monitor.slowCalls')},{key:'averageMillis',title:t('monitor.averageMillis'),sortable:true,width:160},{key:'maxMillis',title:t('monitor.maxMillis'),sortable:true,width:160},{key:'lastAt',title:t('monitor.lastAt'),width:190}])

const actionBusy = ref(false)
</script>
<template><TableWorkspace><template #tools><PageHeader :title="t('monitor.sql')"><PermissionButton :disabled="actionBusy" :permission="'monitor:sql:reset'" variant="outline" class="text-destructive" @click="reset">{{ t('common.reset') }}</PermissionButton></PageHeader><p class="mb-4 text-sm text-muted-foreground">{{ t('monitor.sqlHint') }}</p><FilterBar :busy="busy" @search="search"><Input v-model="keyword" :placeholder="t('monitor.statement')" :aria-label="t('monitor.statement')" /></FilterBar><ErrorNotice :message="error" /></template><DataTable :rows="rows" :columns="columns" :loading="busy" :page="page" :total="total" @update:page="page = $event; load()" @sort="(key, order) => changeSort({prop: key, order: order === 'asc' ? 'ascending' : 'descending'})"><template #averageMillis="{ row }">{{ row.averageMillis.toFixed(2) }}</template><template #maxMillis="{ row }">{{ row.maxMillis.toFixed(2) }}</template><template #lastAt="{ row }">{{ formatInstant(row.lastAt, preferences.timeZone, locale) }}</template></DataTable></TableWorkspace></template>
