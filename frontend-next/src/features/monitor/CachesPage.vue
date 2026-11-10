<script setup lang="ts">
import { useRequestGate } from '@/composables/useRequestGate'
const loadGate = useRequestGate()

import PageHeader from '@/components/business/PageHeader.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import { Button } from '@/components/ui/button'

import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { confirmOrCancel } from '@/lib/feedback'
import { get, remove } from '@/api/http'
interface Cache {
  id: string
  name: string
  size: string
  hits: string
  misses: string
}
const { t, locale } = useI18n(),
  rows = ref<Cache[]>([]),
  busy = ref(false),
  error = ref('')
async function load() {
  const isCurrent = loadGate()
  busy.value = true
  try {
    const loaded: typeof rows.value = await get('/monitor/caches')
    if (!isCurrent()) return
    rows.value = loaded
    error.value = ''
  } catch (e) {
    if (!isCurrent()) return
    error.value = (e as Error).message
  } finally {
    if (isCurrent()) { busy.value = false }
  }
}
async function clear(row?: Cache) {
  try {
    await confirmOrCancel(t('monitor.clearConfirm'), t('common.confirm'), { type: 'warning' })
    await remove('/monitor/caches' + (row ? '/' + encodeURIComponent(row.id) : ''))
    await load()
  } catch (e) {
    if (e instanceof Error) error.value = e.message
  }
}
watch(locale, load, { immediate: true })

import { computed } from 'vue'
import DataTable from '@/components/business/DataTable.vue'
const columns = computed(() => [{key:'name',title:t('common.name')},{key:'id',title:t('monitor.cacheId')},{key:'size',title:t('monitor.entries')},{key:'hits',title:t('monitor.hits')},{key:'misses',title:t('monitor.misses')},{key:'actions',title:t('common.actions')}])
</script>
<template><PageHeader :title="t('monitor.caches')"><Button variant="outline" :disabled="busy" @click="load">{{ t('common.refresh') }}</Button><Button v-permission="'monitor:cache:clear'" variant="outline" class="text-destructive" @click="clear()">{{ t('monitor.clearAll') }}</Button></PageHeader><ErrorNotice :message="error" /><DataTable :rows="rows" :columns="columns" :loading="busy"><template #actions="{ row }"><Button v-permission="'monitor:cache:clear'" variant="ghost" size="sm" @click="clear(row)">{{ t('monitor.clear') }}</Button></template></DataTable></template>
