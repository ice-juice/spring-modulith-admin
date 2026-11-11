<script setup lang="ts">
import { useRequestGate } from '@/composables/useRequestGate'
const loadGate = useRequestGate()

import PageHeader from '@/components/business/PageHeader.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import { Button } from '@/components/ui/button'

import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { get } from '@/api/http'
import { formatBytes } from '@/features/file/files'
import { formatInstant } from '@/lib/time'
import { usePreferences } from '@/stores/preferences'
interface Server {
  sampledAt: string
  os: string
  arch: string
  processors: number
  cpuLoad: number | null
  processCpuLoad: number | null
  physicalTotalBytes: string
  physicalFreeBytes: string
  javaVersion: string
  vm: string
  heapUsedBytes: string
  heapCommittedBytes: string
  heapMaxBytes: string
  nonHeapUsedBytes: string
  threads: number
  uptimeMillis: string
  disks: { name: string; type: string; totalBytes: string; usableBytes: string }[]
  pool: { active: number; idle: number; total: number; waiting: number } | null
}
const { t, locale } = useI18n(),
  preferences = usePreferences(),
  data = ref<Server>(),
  error = ref(''),
  busy = ref(false),
  automatic = ref(false)
let timer: ReturnType<typeof setInterval> | undefined
function percent(value: number | null | undefined) {
  return value == null ? null : Math.round(value * 1000) / 10
}
function usage(used: string, total: string) {
  return Number(total) > 0
    ? Math.min(100, Math.round((Number(used) / Number(total)) * 1000) / 10)
    : 0
}
async function load() {
  if (busy.value) return
  const isCurrent = loadGate()
  busy.value = true
  try {
    const loaded: typeof data.value = await get('/monitor/server')
    if (!isCurrent()) return
    data.value = loaded
    error.value = ''
  } catch (e) {
    if (!isCurrent()) return
    error.value = (e as Error).message
  } finally {
    if (isCurrent()) { busy.value = false }
  }
}
function uptime(value: string) {
  const minutes = Math.floor(Number(value) / 60000)
  return t('monitor.uptimeValue', {
    days: Math.floor(minutes / 1440),
    hours: Math.floor((minutes % 1440) / 60),
    minutes: minutes % 60,
  })
}
watch(automatic, (value) => {
  if (timer) clearInterval(timer)
  timer = value ? setInterval(() => void load(), 10000) : undefined
})
onMounted(load)
onUnmounted(() => {
  if (timer) clearInterval(timer)
})

import { computed } from 'vue'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { Switch } from '@/components/ui/switch'
import { Label } from '@/components/ui/label'
import DataTable from '@/components/business/DataTable.vue'
import EmptyState from '@/components/business/EmptyState.vue'
const diskColumns = computed(() => [{key:'name',title:t('common.name')},{key:'type',title:t('monitor.columns.type')},{key:'totalBytes',title:t('monitor.capacity')},{key:'usableBytes',title:t('monitor.free')}])
const serverProperties = computed(() => data.value ? [{label:t('monitor.os'),value:data.value.os},{label:t('monitor.arch'),value:data.value.arch},{label:t('monitor.java'),value:data.value.javaVersion},{label:t('monitor.vm'),value:data.value.vm},{label:t('monitor.threads'),value:data.value.threads},{label:t('monitor.uptime'),value:uptime(data.value.uptimeMillis)},{label:t('monitor.nonHeap'),value:formatBytes(data.value.nonHeapUsedBytes)}] : [])
const separator = '·'
</script>
<template><PageHeader :title="t('monitor.server')"><div class="mr-3 flex items-center gap-2"><Switch id="auto-refresh" v-model="automatic" /><Label for="auto-refresh">{{ t('monitor.autoRefresh') }}</Label></div><Button variant="outline" :disabled="busy" @click="load">{{ t('common.refresh') }}</Button></PageHeader><ErrorNotice :message="error" /><p v-if="data" class="mb-4 text-xs text-muted-foreground">{{ formatInstant(data.sampledAt, preferences.timeZone, locale) }}</p><template v-if="data"><div class="mb-6 grid gap-5 lg:grid-cols-3"><Card class="metric-card shadow-none"><CardHeader><CardTitle class="text-sm text-muted-foreground">{{ t('monitor.cpu') }}</CardTitle></CardHeader><CardContent><p class="mb-4 text-3xl font-semibold tabular-nums">{{ data.cpuLoad == null ? t('common.unavailable') : percent(data.cpuLoad) + '%' }}</p><Progress v-if="data.cpuLoad != null" :model-value="percent(data.cpuLoad) || 0" :aria-label="t('monitor.cpu')" /><p class="mt-4 text-xs text-muted-foreground">{{ t('monitor.cores', { count: data.processors }) }} {{ separator }} {{ t('monitor.processCpu') }} {{ percent(data.processCpuLoad) ?? t('common.unavailable') }}</p></CardContent></Card><Card class="metric-card shadow-none"><CardHeader><CardTitle class="text-sm text-muted-foreground">{{ t('monitor.physicalMemory') }}</CardTitle></CardHeader><CardContent><p class="text-3xl font-semibold">{{ formatBytes(Number(data.physicalTotalBytes) - Number(data.physicalFreeBytes)) }}</p><p class="mt-4 text-xs text-muted-foreground">{{ t('monitor.totalMemory', { total: formatBytes(data.physicalTotalBytes) }) }}</p></CardContent></Card><Card class="metric-card shadow-none"><CardHeader><CardTitle class="text-sm text-muted-foreground">{{ t('monitor.heap') }}</CardTitle></CardHeader><CardContent><p class="mb-4 text-3xl font-semibold">{{ formatBytes(data.heapUsedBytes) }}</p><Progress :model-value="usage(data.heapUsedBytes, data.heapMaxBytes)" :aria-label="t('monitor.heap')" /><p class="mt-4 text-xs text-muted-foreground">{{ t('monitor.ofTotal', { total: formatBytes(data.heapMaxBytes) }) }} {{ separator }} {{ t('monitor.committed') }} {{ formatBytes(data.heapCommittedBytes) }}</p></CardContent></Card></div><Card class="mb-5 shadow-none"><CardContent><dl class="grid gap-5 sm:grid-cols-2 xl:grid-cols-3"><div v-for="entry in serverProperties" :key="entry.label"><dt class="mb-1 text-xs text-muted-foreground">{{ entry.label }}</dt><dd class="break-words text-sm">{{ entry.value }}</dd></div></dl></CardContent></Card><Card v-if="data.pool" class="mb-5 shadow-none"><CardHeader><CardTitle class="text-base">{{ t('monitor.pool') }}</CardTitle></CardHeader><CardContent><dl class="grid grid-cols-2 gap-5 sm:grid-cols-4"><div v-for="key in ['active', 'idle', 'total', 'waiting'] as const" :key="key"><dt class="text-xs text-muted-foreground">{{ t('monitor.poolFields.' + key) }}</dt><dd class="mt-2 text-2xl font-semibold">{{ data.pool[key] }}</dd></div></dl></CardContent></Card><h2 class="mb-3 text-base font-semibold">{{ t('monitor.disks') }}</h2><DataTable :rows="data.disks.map(disk => ({ ...disk, id: disk.name }))" :columns="diskColumns"><template #totalBytes="{ row }">{{ formatBytes(row.totalBytes) }}</template><template #usableBytes="{ row }">{{ formatBytes(row.usableBytes) }}</template></DataTable></template><EmptyState v-else-if="!busy && !error" /></template>
