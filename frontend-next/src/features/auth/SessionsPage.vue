<script setup lang="ts">
import { useRequestGate } from '@/composables/useRequestGate'
const loadGate = useRequestGate()

import PageHeader from '@/components/business/PageHeader.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import { Button } from '@/components/ui/button'

import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { confirmOrCancel } from '@/lib/feedback'
import { get, post, type PageResult } from '@/api/http'
import { usePreferences } from '@/stores/preferences'
import { formatInstant } from '@/lib/time'
interface Session {
  id: string
  username: string
  name: string
  ip: string
  userAgent: string
  lastSeenAt: string
  expiresAt: string
  locked: boolean
  current: boolean
}
const { t, locale } = useI18n()
const preferences = usePreferences()
const rows = ref<Session[]>([])
const keyword = ref('')
const page = ref(1)
const total = ref(0)
const busy = ref(false)
const error = ref('')
async function load() {
  const isCurrent = loadGate()
  busy.value = true
  error.value = ''
  try {
    const result = await get<PageResult<Session>>('/auth/sessions', {
      page: page.value,
      size: 20,
      keyword: keyword.value,
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
async function revoke(row: Session) {
  try {
    await confirmOrCancel(t('auth.forceLogoutConfirm'), t('common.confirm'), {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await post('/auth/sessions/' + row.id + '/revoke')
    await load()
  } catch (e) {
    error.value = (e as Error).message
  }
}
watch(locale, load, { immediate: true })
function search() {
  page.value = 1
  void load()
}

import { computed } from 'vue'
import { Input } from '@/components/ui/input'
import FilterBar from '@/components/business/FilterBar.vue'
import DataTable from '@/components/business/DataTable.vue'
import StatusBadge from '@/components/business/StatusBadge.vue'
const columns = computed(() => [{key:'username',title:t('auth.username')},{key:'name',title:t('common.name')},{key:'ip',title:t('auth.ip')},{key:'userAgent',title:t('auth.device'),width:240},{key:'lastSeenAt',title:t('auth.lastSeen'),width:190},{key:'locked',title:t('common.status')},{key:'actions',title:t('common.actions'),width:150}])
</script>
<template><PageHeader :title="t('auth.sessions')"><Button variant="outline" :disabled="busy" @click="load">{{ t('common.refresh') }}</Button></PageHeader><FilterBar :busy="busy" @search="search"><Input v-model="keyword" :placeholder="t('auth.username')" :aria-label="t('auth.username')" /></FilterBar><ErrorNotice :message="error" /><DataTable :rows="rows" :columns="columns" :loading="busy" :page="page" :total="total" @update:page="page = $event; load()"><template #lastSeenAt="{ row }">{{ formatInstant(row.lastSeenAt, preferences.timeZone, locale) }}</template><template #locked="{ row }"><StatusBadge :label="t(row.locked ? 'auth.locked' : 'auth.online')" :tone="row.locked ? 'warning' : 'success'" /></template><template #actions="{ row }"><StatusBadge v-if="row.current" :label="t('auth.currentSession')" /><Button v-else v-permission="'auth:session:revoke'" variant="ghost" size="sm" class="text-destructive" @click="revoke(row)">{{ t('auth.forceLogout') }}</Button></template></DataTable></template>
