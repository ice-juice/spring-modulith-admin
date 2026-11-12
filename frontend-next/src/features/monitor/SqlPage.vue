<script setup lang="ts">
import PermissionButton from '@/components/business/PermissionButton.vue'

import TableWorkspace from '@/components/business/TableWorkspace.vue'
import { useLatestRequest } from '@/composables/useLatestRequest'
import { useMonitorCommands } from '@/composables/useMonitorCommands'
import { useAuth } from '@/stores/auth'
import { getToken } from '@/lib/token'
import OperationStatus from '@/components/business/OperationStatus.vue'

import PageHeader from '@/components/business/PageHeader.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'

import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { get, type PageResult } from '@/api/http'
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
  sort = ref('maxMillis'),
  direction = ref('desc')
const request = useLatestRequest(), commands = useMonitorCommands(), auth = useAuth()
async function load() {
  if (!auth.user || auth.user.locked || !auth.hasPermission('monitor:sql:view')) return
  const token = getToken(), params = { page: page.value, size: 20, keyword: keyword.value, sort: sort.value, direction: direction.value }
  await request.run(signal => get<PageResult<Statement>>('/monitor/sql', params, { signal, expectedIdentity: token }), result => {
    rows.value = result.items; total.value = Number(result.total)
  }, JSON.stringify([params, locale.value]))
}
async function reset() { if (await commands.run()) { request.cancel(); await load() } }
watch(commands.identity, () => { request.cancel(); rows.value = []; total.value = 0; keyword.value = ''; page.value = 1; request.error.value = ''; void load() }, { flush: 'sync' })
function search() {
  page.value = 1
  void load()
}
function changeSort(value: { prop: string; order: string | null }) {
  sort.value = value.prop || 'maxMillis'
  direction.value = value.order === 'ascending' ? 'asc' : 'desc'
  search()
}
watch(locale, load, { immediate: true })

import { computed } from 'vue'
import { Input } from '@/components/ui/input'
import FilterBar from '@/components/business/FilterBar.vue'
import DataTable from '@/components/business/DataTable.vue'
const columns = computed(() => [{key:'id',title:t('monitor.statement'),width:380},{key:'command',title:t('monitor.command')},{key:'calls',title:t('monitor.calls'),sortable:true},{key:'errors',title:t('monitor.errors'),sortable:true},{key:'rows',title:t('monitor.resultRows')},{key:'slowCalls',title:t('monitor.slowCalls')},{key:'averageMillis',title:t('monitor.averageMillis'),sortable:true,width:160},{key:'maxMillis',title:t('monitor.maxMillis'),sortable:true,width:160},{key:'lastAt',title:t('monitor.lastAt'),width:190}])


</script>
<template><TableWorkspace><template #tools><PageHeader :title="t('monitor.sql')"><PermissionButton :disabled="commands.blocked(commands.sqlResource)" :permission="'monitor:sql:reset'" variant="outline" class="text-destructive" @click="reset">{{ t('common.reset') }}</PermissionButton></PageHeader><p class="mb-4 text-sm text-muted-foreground">{{ t('monitor.sqlHint') }}</p><FilterBar :busy="request.loading.value" @search="search"><Input v-model="keyword" :placeholder="t('monitor.statement')" :aria-label="t('monitor.statement')" /></FilterBar><ErrorNotice :message="request.error.value || commands.error.value" /><OperationStatus :slow="commands.slow('monitor.sql.reset')" /><div v-if="commands.unknown('monitor.sql.reset')" role="status" aria-live="polite" class="mb-3 rounded-lg border p-3 text-sm">{{ t('monitor.nativeCommandUnknown') }}</div></template><DataTable :rows="rows" :columns="columns" :loading="request.loading.value" :page="page" :total="total" @update:page="page = $event; load()" @sort="(key, order) => changeSort({prop: key, order: order === 'asc' ? 'ascending' : 'descending'})"><template #averageMillis="{ row }">{{ row.averageMillis.toFixed(2) }}</template><template #maxMillis="{ row }">{{ row.maxMillis.toFixed(2) }}</template><template #lastAt="{ row }">{{ formatInstant(row.lastAt, preferences.timeZone, locale) }}</template></DataTable></TableWorkspace></template>
