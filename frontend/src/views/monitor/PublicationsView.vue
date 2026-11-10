<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'
import { get, post, type PageResult } from '@/api/http'
import { formatInstant } from '@/lib/time'
import { usePreferences } from '@/stores/preferences'
interface Publication {
  id: string
  eventType: string
  listener: string
  status: string
  attempts: number
  publishedAt: string
  lastResubmissionAt: string | null
  completedAt: string | null
}
const { t, locale } = useI18n(),
  preferences = usePreferences(),
  rows = ref<Publication[]>([]),
  status = ref('FAILED'),
  page = ref(1),
  total = ref(0),
  busy = ref(false),
  error = ref('')
const statuses = ['FAILED', 'PUBLISHED', 'PROCESSING', 'RESUBMITTED', 'COMPLETED']
async function load() {
  busy.value = true
  try {
    const result = await get<PageResult<Publication>>('/monitor/publications', {
      page: page.value,
      size: 20,
      status: status.value,
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
async function retry(row: Publication) {
  try {
    await ElMessageBox.confirm(t('monitor.retryConfirm'), t('common.confirm'))
    await post('/monitor/publications/' + row.id + '/retry')
    await load()
  } catch (e) {
    if (e instanceof Error) error.value = e.message
  }
}
watch(
  [status, locale],
  () => {
    page.value = 1
    void load()
  },
  { immediate: true },
)
</script>
<template>
  <el-card shadow="never"
    ><div class="toolbar">
      <el-select v-model="status"
        ><el-option
          v-for="value in statuses"
          :key="value"
          :value="value"
          :label="t('monitor.eventStates.' + value)" /></el-select
      ><el-button @click="load">{{ t('common.refresh') }}</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" /><el-table
      v-loading="busy"
      :data="rows"
      row-key="id"
      ><el-table-column
        prop="eventType"
        :label="t('monitor.eventType')"
        min-width="180"
      /><el-table-column
        prop="listener"
        :label="t('monitor.listener')"
        min-width="260"
        show-overflow-tooltip
      /><el-table-column :label="t('common.status')" width="130"
        ><template #default="{ row }">{{
          t('monitor.eventStates.' + row.status)
        }}</template></el-table-column
      ><el-table-column
        prop="attempts"
        :label="t('monitor.attempts')"
        width="110"
      /><el-table-column :label="t('monitor.publishedAt')" width="190"
        ><template #default="{ row }">{{
          formatInstant(row.publishedAt, preferences.timeZone, locale)
        }}</template></el-table-column
      ><el-table-column :label="t('monitor.lastRetry')" width="190"
        ><template #default="{ row }">{{
          formatInstant(row.lastResubmissionAt, preferences.timeZone, locale)
        }}</template></el-table-column
      ><el-table-column :label="t('common.actions')" width="100"
        ><template #default="{ row }"
          ><el-button
            v-if="row.status === 'FAILED'"
            v-permission="'monitor:event:retry'"
            link
            @click="retry(row)"
            >{{ t('monitor.retry') }}</el-button
          ></template
        ></el-table-column
      ></el-table
    ><el-pagination
      v-model:current-page="page"
      :page-size="20"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="load"
  /></el-card>
</template>
