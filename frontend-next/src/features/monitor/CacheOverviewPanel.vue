<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import TableWorkspace from '@/components/business/TableWorkspace.vue'
import DataTable from '@/components/business/DataTable.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import StatusBadge from '@/components/business/StatusBadge.vue'
import { useRequestGate } from '@/composables/useRequestGate'
import { getToken } from '@/lib/token'
import { formatInstant } from '@/lib/time'
import { usePreferences } from '@/stores/preferences'
import { cacheApi, cacheCount, cacheProvider, type CacheNodes, type CacheRegion, type CacheNodeSelection } from './cache'
const props = defineProps<{ regions: CacheRegion[]; node?: CacheNodeSelection | null }>()
const { t, te, locale } = useI18n(), preferences = usePreferences(), gate = useRequestGate()
const nodes = ref<CacheNodes>(), busy = ref(false), error = ref('')
const members = computed(() => nodes.value?.scope.members.map(node => ({ ...node, id: node.nodeId })) || [])
const providerCounts = computed(() => [...new Set(props.regions.map(region => cacheProvider(region)))].map(provider => ({ provider, count: props.regions.filter(region => (cacheProvider(region)) === provider).length })))
const label = (value: string) => te('monitor.cacheGovernance.states.' + value) ? t('monitor.cacheGovernance.states.' + value) : t('monitor.cacheGovernance.unknown')
const columns = computed(() => [{ key: 'nodeId', title: t('monitor.cacheGovernance.node') }, { key: 'stateAtCapture', title: t('monitor.cacheGovernance.availability') }, { key: 'processId', title: t('monitor.cacheGovernance.process'), width: 300 }, { key: 'epoch', title: t('monitor.cacheGovernance.epoch') }, { key: 'leaseUntil', title: t('monitor.cacheGovernance.leaseUntil'), width: 200 }])
async function load() {
  const current = gate(), token = getToken(); busy.value = true; nodes.value = undefined
  try { const value = await cacheApi.nodes(props.node); if (current() && token === getToken()) { nodes.value = value; error.value = '' } }
  catch (failure) { if (current() && token === getToken()) error.value = (failure as Error).message }
  finally { if (current()) busy.value = false }
}
watch(() => props.node, load, { immediate: true })
</script>
<template><TableWorkspace><template #tools><div class="grid gap-3 sm:grid-cols-3"><div class="rounded-xl border bg-card p-4"><div class="text-sm text-muted-foreground">{{ t('monitor.cacheGovernance.registered') }}</div><div class="mt-2 text-2xl font-semibold">{{ regions.length }}</div></div><div class="rounded-xl border bg-card p-4"><div class="text-sm text-muted-foreground">{{ t('monitor.cacheGovernance.provider') }}</div><div class="mt-2 flex flex-wrap gap-3 text-sm"><span v-for="entry in providerCounts" :key="entry.provider">{{ te('monitor.cacheGovernance.providers.' + entry.provider) ? t('monitor.cacheGovernance.providers.' + entry.provider) : entry.provider }} {{ t('monitor.cacheGovernance.separator') }} {{ entry.count }}</span></div></div><div class="rounded-xl border bg-card p-4"><div class="text-sm text-muted-foreground">{{ t('monitor.cacheGovernance.observer') }}</div><div class="mt-2">{{ nodes ? label(nodes.observerState) : t('monitor.cacheGovernance.unknown') }}</div></div></div><p class="my-3 text-sm text-muted-foreground">{{ t('monitor.cacheGovernance.statisticsHint') }}</p><div class="flex flex-wrap items-center justify-between gap-2"><p class="text-sm">{{ t('monitor.cacheGovernance.nodesHint') }}</p><Button variant="outline" :disabled="busy" @click="load">{{ t('common.refresh') }}</Button></div><p v-if="nodes" class="my-2 break-all text-xs text-muted-foreground">{{ t('monitor.cacheGovernance.process') }}{{ t('monitor.cacheGovernance.colon') }} {{ nodes.localProcessId }} {{ t('monitor.cacheGovernance.separator') }} {{ t('monitor.cacheGovernance.sampledAt') }}{{ t('monitor.cacheGovernance.colon') }} {{ formatInstant(nodes.scope.capturedAt, preferences.timeZone, locale) }}</p><ErrorNotice :message="error" /></template><DataTable :rows="members" :columns="columns" :loading="busy" storage-key="monitor.cache-nodes"><template #stateAtCapture="{ row }"><StatusBadge :label="label(row.stateAtCapture)" :tone="row.stateAtCapture === 'ONLINE' ? 'success' : 'warning'" /></template><template #processId="{ row }">{{ row.processId || t('monitor.cacheGovernance.unknown') }}</template><template #epoch="{ row }">{{ cacheCount(row.epoch, locale, t('monitor.cacheGovernance.unknown')) }}</template><template #leaseUntil="{ row }">{{ formatInstant(row.leaseUntil, preferences.timeZone, locale) }}</template></DataTable></TableWorkspace></template>
