<script setup lang="ts">
import PermissionButton from '@/components/business/PermissionButton.vue'

import TableWorkspace from '@/components/business/TableWorkspace.vue'
import { useRequestGate } from '@/composables/useRequestGate'
const loadGate = useRequestGate()

import PageHeader from '@/components/business/PageHeader.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import { Button } from '@/components/ui/button'

import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { confirmOrCancel } from '@/lib/feedback'
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
  const isCurrent = loadGate()
  busy.value = true
  try {
    const result = await get<PageResult<Publication>>('/monitor/publications', {
      page: page.value,
      size: 20,
      status: status.value,
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
async function retry(row: Publication) {
  if (actionBusy.value) return
  actionBusy.value = true
  try {
  try {
    await confirmOrCancel(t('monitor.retryConfirm'), t('common.confirm'))
    await post('/monitor/publications/' + row.id + '/retry')
    await load()
  } catch (e) {
    if (e instanceof Error) error.value = e.message
  }

  } finally { actionBusy.value = false }
}
watch(
  [status, locale],
  () => {
    page.value = 1
    void load()
  },
  { immediate: true },
)

import { computed } from 'vue'
import DataTable from '@/components/business/DataTable.vue'
import EntitySelect from '@/components/business/EntitySelect.vue'
import StatusBadge from '@/components/business/StatusBadge.vue'
const columns = computed(() => [{key:'eventType',title:t('monitor.eventType'),width:220},{key:'listener',title:t('monitor.listener'),width:260},{key:'status',title:t('common.status')},{key:'attempts',title:t('monitor.attempts')},{key:'publishedAt',title:t('monitor.publishedAt'),width:190},{key:'lastResubmissionAt',title:t('monitor.lastRetry'),width:190},{key:'actions',title:t('common.actions')}])
const statusOptions = computed(() => statuses.map(value=>({value,label:t('monitor.eventStates.'+value)})))

const actionBusy = ref(false)
</script>
<template><TableWorkspace><template #tools><PageHeader :title="t('monitor.events')"><Button variant="outline" :disabled="busy" @click="load">{{ t('common.refresh') }}</Button></PageHeader><div class="mb-4 max-w-xs"><EntitySelect :model-value="status" :options="statusOptions" :placeholder="t('common.status')" @update:model-value="status = String($event || 'FAILED')" /></div><ErrorNotice :message="error" /></template><DataTable :rows="rows" :columns="columns" :loading="busy" :page="page" :total="total" @update:page="page = $event; load()"><template #status="{ row }"><StatusBadge :label="t('monitor.eventStates.' + row.status)" :tone="row.status === 'FAILED' ? 'danger' : row.status === 'COMPLETED' ? 'success' : 'neutral'" /></template><template #publishedAt="{ row }">{{ formatInstant(row.publishedAt, preferences.timeZone, locale) }}</template><template #lastResubmissionAt="{ row }">{{ formatInstant(row.lastResubmissionAt, preferences.timeZone, locale) }}</template><template #actions="{ row }"><PermissionButton :disabled="actionBusy" v-if="row.status === 'FAILED'" :permission="'monitor:event:retry'" variant="ghost" size="sm" @click="retry(row)">{{ t('monitor.retry') }}</PermissionButton></template></DataTable></TableWorkspace></template>
