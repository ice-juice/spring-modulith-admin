<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'
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
  busy.value = true
  try {
    const result = await get<PageResult<Statement>>('/monitor/sql', {
      page: page.value,
      size: 20,
      keyword: keyword.value,
      sort: sort.value,
      direction: direction.value,
    })
    rows.value = result.items
    total.value = Number(result.total)
    error.value = ''
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
async function reset() {
  try {
    await ElMessageBox.confirm(t('monitor.sqlResetConfirm'), t('common.confirm'), {
      type: 'warning',
    })
    await remove('/monitor/sql')
    await load()
  } catch (e) {
    if (e instanceof Error) error.value = e.message
  }
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
</script>
<template>
  <el-card shadow="never"
    ><div class="toolbar">
      <el-input
        v-model="keyword"
        :placeholder="t('monitor.statement')"
        clearable
        @keyup.enter="search"
      /><el-button @click="search">{{ t('common.search') }}</el-button
      ><el-button v-permission="'monitor:sql:reset'" @click="reset">{{
        t('common.reset')
      }}</el-button>
    </div>
    <el-alert :title="t('monitor.sqlHint')" type="info" :closable="false" /><el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false" /><el-table
      v-loading="busy"
      :data="rows"
      row-key="id"
      @sort-change="changeSort"
      ><el-table-column
        prop="id"
        :label="t('monitor.statement')"
        min-width="380"
        show-overflow-tooltip
      /><el-table-column prop="command" :label="t('monitor.command')" width="100" /><el-table-column
        prop="calls"
        :label="t('monitor.calls')"
        sortable="custom"
        width="110"
      /><el-table-column
        prop="errors"
        :label="t('monitor.errors')"
        sortable="custom"
        width="110"
      /><el-table-column prop="rows" :label="t('monitor.resultRows')" width="110" /><el-table-column
        prop="slowCalls"
        :label="t('monitor.slowCalls')"
        width="110"
      /><el-table-column
        prop="averageMillis"
        :label="t('monitor.averageMillis')"
        sortable="custom"
        width="150"
        ><template #default="{ row }">{{ row.averageMillis.toFixed(2) }}</template></el-table-column
      ><el-table-column
        prop="maxMillis"
        :label="t('monitor.maxMillis')"
        sortable="custom"
        width="150"
        ><template #default="{ row }">{{ row.maxMillis.toFixed(2) }}</template></el-table-column
      ><el-table-column :label="t('monitor.lastAt')" width="190"
        ><template #default="{ row }">{{
          formatInstant(row.lastAt, preferences.timeZone, locale)
        }}</template></el-table-column
      ></el-table
    ><el-pagination
      v-model:current-page="page"
      :total="total"
      :page-size="20"
      layout="total, prev, pager, next"
      @current-change="load"
  /></el-card>
</template>
