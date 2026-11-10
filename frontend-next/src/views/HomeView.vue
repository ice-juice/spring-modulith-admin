<script setup lang="ts">
import { ArrowUpRight, Activity, Clock3, ShieldCheck, ArrowRight } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import PageHeader from '@/components/business/PageHeader.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import EmptyState from '@/components/business/EmptyState.vue'
import StatusBadge from '@/components/business/StatusBadge.vue'

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
  <PageHeader :eyebrow="t('common.workspaceEyebrow')" :title="t('common.welcome', { name: auth.user?.name || '' })" :description="t('workspace.hint')"><Button variant="outline" :disabled="busy" @click="load">{{ t('common.refresh') }}</Button></PageHeader>
  <ErrorNotice :message="error" />
  <div class="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4" :aria-busy="busy"><button v-for="metric in dashboard.metrics" :key="metric.id" class="group rounded-xl border bg-card p-5 text-left transition-shadow hover:shadow-sm" @click="router.push(metric.path)"><div class="flex items-center justify-between text-sm text-muted-foreground"><span>{{ metric.name }}</span><ArrowUpRight class="size-4 transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5" /></div><p class="mt-4 text-3xl font-semibold tracking-tight tabular-nums">{{ metric.value }}</p></button></div>
  <div class="mb-6 grid gap-4 lg:grid-cols-[1fr_1fr_1fr]"><Card class="gap-0 py-0 shadow-none"><CardContent class="flex items-center gap-4 p-5"><div class="rounded-lg bg-accent p-3 text-primary"><Activity class="size-5" /></div><div><p class="mb-2 text-xs text-muted-foreground">{{ t('common.health') }}</p><StatusBadge :label="t(healthy ? 'common.healthy' : 'common.unavailable')" :tone="healthy ? 'success' : 'warning'" /></div></CardContent></Card><Card class="gap-0 py-0 shadow-none"><CardContent class="flex items-center gap-4 p-5"><div class="rounded-lg bg-muted p-3 text-muted-foreground"><Clock3 class="size-5" /></div><div class="min-w-0"><p class="mb-1 text-xs text-muted-foreground">{{ t('common.currentTime') }}</p><p class="text-sm font-medium tabular-nums">{{ formatInstant(now, preferences.timeZone, locale) }}</p><p class="text-xs text-muted-foreground">{{ preferences.timeZone }}</p></div></CardContent></Card><Card class="gap-0 py-0 shadow-none"><CardContent class="flex items-center gap-4 p-5"><div class="rounded-lg bg-muted p-3 text-muted-foreground"><ShieldCheck class="size-5" /></div><div><p class="mb-1 text-xs text-muted-foreground">{{ t('common.portal') }}</p><p class="text-sm font-medium">{{ auth.user?.portal }}</p><p class="text-xs text-muted-foreground">{{ auth.user?.username }}</p></div></CardContent></Card></div>
  <div class="grid gap-5 xl:grid-cols-[1.5fr_1fr]"><Card class="shadow-none"><CardHeader><CardTitle class="text-base">{{ t('workspace.pending') }}</CardTitle></CardHeader><CardContent><EmptyState v-if="!dashboard.entries.length" :title="t('workspace.noPending')" /><button v-for="(entry, index) in dashboard.entries" :key="entry.path + index" class="flex w-full items-center gap-3 border-b py-4 text-left last:border-0 hover:text-primary" @click="router.push(entry.path)"><span class="rounded-md bg-muted px-2 py-1 text-xs text-muted-foreground">{{ entry.kind }}</span><span class="min-w-0 flex-1 truncate">{{ entry.title }}</span><small class="hidden shrink-0 text-muted-foreground sm:block">{{ formatInstant(entry.time, preferences.timeZone, locale) }}</small><ArrowRight class="size-4 shrink-0" /></button></CardContent></Card><Card class="shadow-none"><CardHeader><CardTitle class="text-base">{{ t('workspace.shortcuts') }}</CardTitle></CardHeader><CardContent><div class="grid grid-cols-1 gap-2 sm:grid-cols-2"><button v-for="menu in shortcuts" :key="menu.id" class="flex min-h-12 items-center justify-between gap-2 rounded-lg border px-3 py-2 text-left text-sm hover:border-primary/40 hover:bg-accent" @click="router.push(menu.path!)"><span class="truncate">{{ menu.name }}</span><ArrowUpRight class="size-3.5 shrink-0 text-muted-foreground" /></button></div><EmptyState v-if="!shortcuts.length" /></CardContent></Card></div>
</template>
