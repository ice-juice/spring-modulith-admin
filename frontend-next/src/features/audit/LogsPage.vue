<script setup lang="ts">
import { useRequestGate } from '@/composables/useRequestGate'
const loadGate = useRequestGate()

import PageHeader from '@/components/business/PageHeader.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import { Button } from '@/components/ui/button'

import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { get, type PageResult } from '@/api/http'
import { usePreferences } from '@/stores/preferences'
import { formatInstant, localDateTimeToInstant } from '@/lib/time'
const route = useRoute(),
  router = useRouter()
const { t, locale } = useI18n()
const preferences = usePreferences()
const kind = computed(() => String(route.meta.logKind))
interface Log {
  id: string
  username: string
  status?: string
  message?: string
  action?: string
  clientPath?: string
  resultCode?: number
  ip: string
  country?: string
  province?: string
  city?: string
  browser?: string
  operatingSystem?: string
  ipChanged?: boolean
  unusualLocation?: boolean
  localAddress?: boolean
  durationMs?: string
  occurredAt: string
}
const rows = ref<Log[]>([]),
  username = ref(''),
  page = ref(1),
  total = ref(0),
  busy = ref(false),
  error = ref('')
const dates = ref<[string, string]>()
const status = ref(''),
  success = ref<boolean>()
const statuses = ['SUCCESS', 'PASSWORD_FAILURE', 'CAPTCHA_FAILURE']
async function load() {
  const isCurrent = loadGate()
  busy.value = true
  error.value = ''
  try {
    const result = await get<PageResult<Log>>('/audit/' + kind.value, {
      page: page.value,
      size: 20,
      username: username.value,
      status: status.value || undefined,
      success: success.value,
      from: dates.value?.[0]
        ? localDateTimeToInstant(dates.value[0], preferences.timeZone)
        : undefined,
      to: dates.value?.[1]
        ? localDateTimeToInstant(dates.value[1], preferences.timeZone)
        : undefined,
    })
    if (!isCurrent()) return
    rows.value = result.items
    total.value = Number(result.total)
  } catch (e) {
    if (!isCurrent()) return
    error.value = (e as Error).message
  } finally {
    if (isCurrent()) { busy.value = false }
  }
}
function search() {
  page.value = 1
  void load()
}
watch([kind, locale], search, { immediate: true })

import { Input } from '@/components/ui/input'
import FilterBar from '@/components/business/FilterBar.vue'
import DataTable from '@/components/business/DataTable.vue'
import EntitySelect from '@/components/business/EntitySelect.vue'
import DateRangeField from '@/components/business/DateRangeField.vue'
import StatusBadge from '@/components/business/StatusBadge.vue'
const columns = computed(() => [{key:'username',title:t('audit.username')},...(kind.value === 'operations' ? [{key:'message',title:t('audit.operation'),width:250}] : []),{key:'status',title:t('common.status'),width:150},{key:'ip',title:t('audit.ip')},...(kind.value === 'logins' ? [{key:'location',title:t('audit.location'),width:220},{key:'device',title:t('audit.device'),width:180}] : [{key:'durationMs',title:t('audit.duration')}]),{key:'occurredAt',title:t('audit.occurredAt'),width:190},...(kind.value === 'operations' ? [{key:'actions',title:t('common.actions')}] : [])])
const statusOptions = computed(() => statuses.map(value => ({value,label:t('audit.statuses.'+value)})))
const resultOptions = computed(() => [{value:'true',label:t('audit.success')},{value:'false',label:t('audit.failure')}])
</script>
<template><PageHeader :title="t(kind === 'logins' ? 'audit.logins' : 'audit.operations')"><Button variant="outline" :disabled="busy" @click="load">{{ t('common.refresh') }}</Button></PageHeader><FilterBar :busy="busy" @search="search"><Input v-model="username" :placeholder="t('audit.username')" :aria-label="t('audit.username')" /><EntitySelect v-if="kind === 'logins'" :model-value="status" :options="statusOptions" :placeholder="t('common.status')" class="w-full sm:w-48" @update:model-value="status = String($event || '')" /><EntitySelect v-else :model-value="success == null ? null : String(success)" :options="resultOptions" :placeholder="t('common.status')" class="w-full sm:w-40" @update:model-value="success = $event == null ? undefined : $event === 'true'" /><DateRangeField v-model="dates" :from-label="t('audit.from')" :to-label="t('audit.to')" /><span class="text-xs text-muted-foreground">{{ preferences.timeZone }}</span></FilterBar><ErrorNotice :message="error" /><DataTable :rows="rows" :columns="columns" :loading="busy" :page="page" :total="total" @update:page="page = $event; load()"><template #status="{ row }"><StatusBadge :tone="(kind === 'logins' ? row.status === 'SUCCESS' : row.resultCode === 0) ? 'success' : 'danger'" :label="kind === 'logins' ? t('audit.statuses.' + row.status) : t(row.resultCode === 0 ? 'audit.success' : 'audit.failure')" /></template><template #location="{ row }"><span>{{ row.localAddress ? t('audit.localAddress') : [row.country, row.province, row.city].filter(Boolean).join(' / ') || t('audit.unknown') }}</span><StatusBadge v-if="row.unusualLocation" class="ml-2" tone="warning" :label="t('audit.unusual')" /></template><template #device="{ row }">{{ [row.browser, row.operatingSystem].filter(Boolean).join(' / ') }}</template><template #occurredAt="{ row }">{{ formatInstant(row.occurredAt, preferences.timeZone, locale) }}</template><template #actions="{ row }"><Button v-if="row.clientPath" variant="ghost" size="sm" @click="router.push(row.clientPath)">{{ t('audit.viewObject') }}</Button></template></DataTable></template>
