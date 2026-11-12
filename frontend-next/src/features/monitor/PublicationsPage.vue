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
import { Button } from '@/components/ui/button'

import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { get, type PageResult } from '@/api/http'
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
  total = ref(0)
const statuses = ['FAILED', 'PUBLISHED', 'PROCESSING', 'RESUBMITTED', 'COMPLETED']
const request = useLatestRequest(), commands = useMonitorCommands(), auth = useAuth()
async function load() {
  if (!auth.user || auth.user.locked || !auth.hasPermission('monitor:event:view')) return
  const token = getToken(), params = { page: page.value, size: 20, status: status.value }
  await request.run(signal => get<PageResult<Publication>>('/monitor/publications', params, { signal, expectedIdentity: token }), result => {
    rows.value = result.items; total.value = Number(result.total)
  }, JSON.stringify([params, locale.value]))
}
async function refresh() { request.cancel(); await load() }
async function retry(row: Publication) { if (row.status === 'FAILED' && await commands.run(row.id)) await refresh() }
watch(commands.identity, () => { request.cancel(); rows.value = []; total.value = 0; page.value = 1; request.error.value = ''; void load() }, { flush: 'sync' })
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


</script>
<template><TableWorkspace><template #tools><PageHeader :title="t('monitor.events')"><Button variant="outline" :disabled="request.loading.value" @click="refresh">{{ t('common.refresh') }}</Button></PageHeader><div class="mb-4 max-w-xs"><EntitySelect :model-value="status" :options="statusOptions" :placeholder="t('common.status')" @update:model-value="status = String($event || 'FAILED')" /></div><ErrorNotice :message="request.error.value || commands.error.value" /><OperationStatus :slow="commands.slow('monitor.publication.retry')" /><div v-if="commands.unknown('monitor.publication.retry')" role="status" aria-live="polite" class="mb-3 rounded-lg border p-3 text-sm">{{ t('monitor.nativeCommandUnknown') }}</div></template><DataTable :rows="rows" :columns="columns" :loading="request.loading.value" :page="page" :total="total" @update:page="page = $event; load()"><template #status="{ row }"><StatusBadge :label="t('monitor.eventStates.' + row.status)" :tone="row.status === 'FAILED' ? 'danger' : row.status === 'COMPLETED' ? 'success' : 'neutral'" /></template><template #publishedAt="{ row }">{{ formatInstant(row.publishedAt, preferences.timeZone, locale) }}</template><template #lastResubmissionAt="{ row }">{{ formatInstant(row.lastResubmissionAt, preferences.timeZone, locale) }}</template><template #actions="{ row }"><PermissionButton :disabled="commands.blocked(commands.publicationResource(row.id))" v-if="row.status === 'FAILED'" :permission="'monitor:event:retry'" variant="ghost" size="sm" @click="retry(row)">{{ t('monitor.retry') }}</PermissionButton></template></DataTable></TableWorkspace></template>
