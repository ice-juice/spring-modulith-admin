<script setup lang="ts">
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
  busy.value = true
  try {
    data.value = await get('/monitor/server')
    error.value = ''
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
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
</script>
<template>
  <div class="toolbar">
    <el-button :loading="busy" @click="load">{{ t('common.refresh') }}</el-button
    ><el-switch v-model="automatic" :active-text="t('monitor.autoRefresh')" /><small v-if="data">{{
      formatInstant(data.sampledAt, preferences.timeZone, locale)
    }}</small>
  </div>
  <el-alert v-if="error" :title="error" type="error" :closable="false" /><template v-if="data"
    ><div class="metric-grid">
      <el-card shadow="never"
        ><h3>{{ t('monitor.cpu') }}</h3>
        <el-progress
          v-if="data.cpuLoad != null"
          type="dashboard"
          :percentage="percent(data.cpuLoad) || 0"
        />
        <p v-else>{{ t('common.unavailable') }}</p>
        <p>{{ t('monitor.cores', { count: data.processors }) }}</p>
        <small
          >{{ t('monitor.processCpu') }}
          {{ percent(data.processCpuLoad) ?? t('common.unavailable') }}</small
        ></el-card
      ><el-card shadow="never"
        ><h3>{{ t('monitor.physicalMemory') }}</h3>
        <strong>{{
          formatBytes(Number(data.physicalTotalBytes) - Number(data.physicalFreeBytes))
        }}</strong>
        <p>
          {{ t('monitor.totalMemory', { total: formatBytes(data.physicalTotalBytes) }) }}
        </p></el-card
      ><el-card shadow="never"
        ><h3>{{ t('monitor.heap') }}</h3>
        <el-progress :percentage="usage(data.heapUsedBytes, data.heapMaxBytes)" />
        <p>
          {{ formatBytes(data.heapUsedBytes) }}
          {{ t('monitor.ofTotal', { total: formatBytes(data.heapMaxBytes) }) }}
        </p>
        <small
          >{{ t('monitor.committed') }} {{ formatBytes(data.heapCommittedBytes) }}</small
        ></el-card
      >
    </div>
    <el-card shadow="never"
      ><el-descriptions :column="2" border
        ><el-descriptions-item :label="t('monitor.os')">{{ data.os }}</el-descriptions-item
        ><el-descriptions-item :label="t('monitor.arch')">{{ data.arch }}</el-descriptions-item
        ><el-descriptions-item :label="t('monitor.java')">{{
          data.javaVersion
        }}</el-descriptions-item
        ><el-descriptions-item :label="t('monitor.vm')">{{ data.vm }}</el-descriptions-item
        ><el-descriptions-item :label="t('monitor.threads')">{{
          data.threads
        }}</el-descriptions-item
        ><el-descriptions-item :label="t('monitor.uptime')">{{
          uptime(data.uptimeMillis)
        }}</el-descriptions-item
        ><el-descriptions-item :label="t('monitor.nonHeap')">{{
          formatBytes(data.nonHeapUsedBytes)
        }}</el-descriptions-item></el-descriptions
      ></el-card
    ><el-card v-if="data.pool" shadow="never"
      ><h3>{{ t('monitor.pool') }}</h3>
      <el-descriptions :column="4"
        ><el-descriptions-item
          v-for="key in ['active', 'idle', 'total', 'waiting'] as const"
          :key="key"
          :label="t('monitor.poolFields.' + key)"
          >{{ data.pool[key] }}</el-descriptions-item
        ></el-descriptions
      ></el-card
    ><el-card shadow="never"
      ><h3>{{ t('monitor.disks') }}</h3>
      <el-table :data="data.disks"
        ><el-table-column prop="name" :label="t('common.name')" /><el-table-column
          prop="type"
          :label="t('monitor.columns.type')"
        /><el-table-column :label="t('monitor.capacity')"
          ><template #default="{ row }">{{
            formatBytes(row.totalBytes)
          }}</template></el-table-column
        ><el-table-column :label="t('monitor.free')"
          ><template #default="{ row }">{{
            formatBytes(row.usableBytes)
          }}</template></el-table-column
        ></el-table
      ></el-card
    ></template
  >
</template>
