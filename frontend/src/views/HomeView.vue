<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuth, type MenuItem } from '@/stores/auth'
import { usePreferences } from '@/stores/preferences'
import { formatInstant } from '@/lib/time'
import { get } from '@/api/http'
import { useMessageRefresh } from '@/features/message/realtime'
interface Dashboard {
  metrics: { id: string; name: string; value: string; path: string }[]
  entries: { kind: string; title: string; path: string; time: string }[]
}
const { t, locale } = useI18n(),
  auth = useAuth(),
  preferences = usePreferences(),
  router = useRouter(),
  healthy = ref(false),
  now = ref(new Date().toISOString()),
  dashboard = ref<Dashboard>({ metrics: [], entries: [] }),
  error = ref(''),
  busy = ref(false)
let timer: ReturnType<typeof setInterval>
const shortcuts = computed(() => {
  const result: MenuItem[] = []
  function visit(items: MenuItem[]) {
    for (const item of items) {
      if (
        item.type === 'MENU' &&
        item.openMode === 'ROUTE' &&
        item.path?.startsWith('/') &&
        !item.path.startsWith('//') &&
        auth.hasPermission(item.permission || undefined)
      )
        result.push(item)
      visit(item.children)
    }
  }
  visit(auth.user?.menus || [])
  return result.slice(0, 8)
})
async function load() {
  if (busy.value) return
  busy.value = true
  try {
    dashboard.value = await get('/workspace/dashboard')
    error.value = ''
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
onMounted(async () => {
  timer = setInterval(() => {
    now.value = new Date().toISOString()
  }, 1000)
  try {
    healthy.value = (await axios.get('/actuator/health')).data.status === 'UP'
  } catch {
    healthy.value = false
  }
})
onUnmounted(() => clearInterval(timer))
watch(locale, load, { immediate: true })
useMessageRefresh(load, ['notice', 'todo', 'private-message'])
</script>
<template>
  <section class="welcome-card">
    <div class="eyebrow">{{ t('common.workspace') }}</div>
    <h1>{{ t('common.welcome', { name: auth.user?.name || '' }) }}</h1>
    <p>{{ t('workspace.hint') }}</p>
  </section>
  <el-alert v-if="error" :title="error" type="error" :closable="false" />
  <div v-loading="busy" class="home-metrics">
    <button
      v-for="metric in dashboard.metrics"
      :key="metric.id"
      class="metric-tile"
      @click="router.push(metric.path)"
    >
      <span>{{ metric.name }}</span
      ><strong>{{ metric.value }}</strong>
    </button>
  </div>
  <div class="dashboard-grid">
    <el-card shadow="never"
      ><div class="muted">{{ t('common.health') }}</div>
      <p>
        <el-tag :type="healthy ? 'success' : 'danger'">{{
          t(healthy ? 'common.healthy' : 'common.unavailable')
        }}</el-tag>
      </p></el-card
    ><el-card shadow="never"
      ><div class="muted">{{ t('common.currentTime') }}</div>
      <p class="metric-value">{{ formatInstant(now, preferences.timeZone, locale) }}</p>
      <small>{{ preferences.timeZone }}</small></el-card
    ><el-card shadow="never"
      ><div class="muted">{{ t('common.portal') }}</div>
      <p class="metric-value">{{ auth.user?.portal }}</p>
      <small>{{ auth.user?.username }}</small></el-card
    >
  </div>
  <div class="home-panels">
    <el-card shadow="never"
      ><template #header
        ><strong>{{ t('workspace.pending') }}</strong></template
      ><el-empty
        v-if="!dashboard.entries.length"
        :description="t('workspace.noPending')"
        :image-size="70"
      />
      <div
        v-for="(entry, index) in dashboard.entries"
        :key="entry.path + index"
        class="activity-row"
      >
        <el-tag size="small">{{ entry.kind }}</el-tag
        ><el-button link @click="router.push(entry.path)">{{ entry.title }}</el-button
        ><small>{{ formatInstant(entry.time, preferences.timeZone, locale) }}</small>
      </div></el-card
    ><el-card shadow="never"
      ><template #header
        ><strong>{{ t('workspace.shortcuts') }}</strong></template
      >
      <div class="shortcut-grid">
        <el-button v-for="menu in shortcuts" :key="menu.id" @click="router.push(menu.path!)">{{
          menu.name
        }}</el-button>
      </div>
      <el-empty v-if="!shortcuts.length" :description="t('common.empty')" :image-size="70"
    /></el-card>
  </div>
</template>
