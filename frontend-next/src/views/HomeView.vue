<script setup lang="ts">
import { ArrowUpRight, Activity, Clock3, ShieldCheck, ArrowRight, Layers, Users, Sparkles } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import SoftlightArtwork from '@/components/business/SoftlightArtwork.vue'
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

function reviewPending() { document.getElementById('workspace-pending')?.scrollIntoView({ behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'center' }) }
</script>
<template>
  <PageHeader :eyebrow="t('common.workspaceEyebrow')" :title="t('common.welcome', { name: auth.user?.name || '' })" :description="t('workspace.hint')"><Button variant="outline" :disabled="busy" @click="load">{{ t('common.refresh') }}</Button></PageHeader>
  <ErrorNotice :message="error" />
  <section class="softlight-hero workspace-welcome mb-6 p-6 sm:p-8"><div class="relative z-10 max-w-2xl"><span class="inline-flex items-center gap-2 rounded-full border border-primary/10 bg-card/70 px-3 py-1 text-xs text-accent-foreground"><Sparkles class="size-3.5" />{{ t('workspace.eyebrow') }}</span><h2 class="mt-4 text-2xl leading-snug font-semibold tracking-tight sm:text-[28px]">{{ t('workspace.heroTitle') }}</h2><p class="mt-3 max-w-lg text-sm leading-6 text-muted-foreground">{{ t('workspace.heroHint') }}</p><Button class="mt-5" @click="reviewPending">{{ t('workspace.reviewPending') }}<ArrowUpRight class="size-4" /></Button></div><SoftlightArtwork /></section>
  <div class="workspace-metrics mb-6 grid gap-4 sm:grid-cols-2" :style="{ '--metric-count': Math.max(1, Math.min(dashboard.metrics.length, 4)) }" :aria-busy="busy"><button v-for="(metric,index) in dashboard.metrics" :key="metric.id" class="softlight-surface group min-w-0 p-6 text-left transition-shadow hover:shadow-sm" @click="router.push(metric.path)"><div class="flex items-center justify-between gap-3 text-sm text-muted-foreground"><span>{{ metric.name }}</span><component :is="[Users, Layers, Clock3, Activity][index % 4]" class="size-4" /></div><div class="mt-4 flex items-end justify-between gap-3"><p class="break-all text-[34px] leading-tight font-semibold tracking-tight tabular-nums">{{ metric.value }}</p><ArrowUpRight class="mb-1 size-4 text-primary transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5" /></div></button></div>
  <div class="mb-6 grid gap-6 xl:grid-cols-[1.45fr_1fr]"><Card id="workspace-pending" class="min-w-0 gap-4 shadow-none"><CardHeader><CardTitle class="text-base">{{ t('workspace.pending') }}</CardTitle><p class="mt-1 text-xs text-muted-foreground">{{ t('workspace.pendingHint') }}</p></CardHeader><CardContent><EmptyState v-if="!dashboard.entries.length" :title="t('workspace.noPending')" /><button v-for="(entry,index) in dashboard.entries" :key="entry.path+index" class="flex w-full items-center gap-3 border-b py-4 text-left last:border-0 hover:text-primary" @click="router.push(entry.path)"><span class="grid size-9 shrink-0 place-items-center rounded-xl bg-accent text-xs tabular-nums text-accent-foreground">{{ String(index+1).padStart(2, '0') }}</span><span class="min-w-0 flex-1"><span class="block break-words text-sm font-medium">{{ entry.title }}</span><span class="mt-1.5 block text-xs text-muted-foreground">{{ entry.kind }} {{ t('common.detailSeparator') }} {{ formatInstant(entry.time, preferences.timeZone, locale) }}</span></span><ArrowRight class="size-4 shrink-0 text-muted-foreground" /></button></CardContent></Card><Card class="min-w-0 gap-4 shadow-none"><CardHeader><CardTitle class="text-base">{{ t('workspace.shortcuts') }}</CardTitle><p class="mt-1 text-xs text-muted-foreground">{{ t('workspace.shortcutsHint') }}</p></CardHeader><CardContent><div class="grid grid-cols-1 gap-3 sm:grid-cols-2"><button v-for="menu in shortcuts" :key="menu.id" class="group flex min-h-14 items-center justify-between gap-3 rounded-xl border bg-muted/20 px-4 py-3 text-left text-sm transition-colors hover:border-primary/30 hover:bg-accent" @click="router.push(menu.path!)"><span class="break-words">{{ menu.name }}</span><ArrowUpRight class="size-3.5 shrink-0 text-muted-foreground group-hover:text-primary" /></button></div><EmptyState v-if="!shortcuts.length" /></CardContent></Card></div>
  <section class="softlight-surface p-5 sm:p-6"><div class="mb-5"><h2 class="text-sm font-semibold">{{ t('workspace.context') }}</h2><p class="mt-1 text-xs text-muted-foreground">{{ t('workspace.contextHint') }}</p></div><div class="grid gap-6 lg:grid-cols-3"><div class="flex items-center gap-4"><span class="rounded-xl bg-accent p-3 text-primary"><Activity class="size-5" /></span><div><p class="mb-2 text-xs text-muted-foreground">{{ t('common.health') }}</p><StatusBadge :label="t(healthy ? 'common.healthy' : 'common.unavailable')" :tone="healthy ? 'success' : 'warning'" /></div></div><div class="flex min-w-0 items-center gap-4"><span class="rounded-xl bg-muted p-3 text-muted-foreground"><Clock3 class="size-5" /></span><div class="min-w-0"><p class="mb-1 text-xs text-muted-foreground">{{ t('common.currentTime') }}</p><p class="text-sm font-medium tabular-nums">{{ formatInstant(now, preferences.timeZone, locale) }}</p><p class="text-xs text-muted-foreground">{{ preferences.timeZone }}</p></div></div><div class="flex min-w-0 items-center gap-4"><span class="rounded-xl bg-muted p-3 text-muted-foreground"><ShieldCheck class="size-5" /></span><div class="min-w-0"><p class="mb-1 text-xs text-muted-foreground">{{ t('common.portal') }}</p><p class="text-sm font-medium">{{ auth.user?.portal }}</p><p class="break-words text-xs text-muted-foreground">{{ auth.user?.username }}</p></div></div></div></section>
</template>
