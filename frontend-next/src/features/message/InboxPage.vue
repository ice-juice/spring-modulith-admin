<script setup lang="ts">
import { useRequestGate } from '@/composables/useRequestGate'
const loadGate = useRequestGate()

import { computed } from 'vue'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import PageHeader from '@/components/business/PageHeader.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import DataTable from '@/components/business/DataTable.vue'
import EditorDialog from '@/components/business/EditorDialog.vue'
import StatusBadge from '@/components/business/StatusBadge.vue'

import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { get, put, type PageResult } from '@/api/http'
import { usePreferences } from '@/stores/preferences'
import { useMessages } from '@/stores/messages'
import { useDictionaries } from '@/features/message/dictionaries'
import { useMessageRefresh } from '@/features/message/realtime'
import type { Notice } from '@/features/message/types'
import { formatInstant } from '@/lib/time'
import TasksPage from './TasksPage.vue'
import PrivatePage from './PrivatePage.vue'
import AttachmentList from '@/components/AttachmentList.vue'
const { t, locale } = useI18n(),
  route = useRoute(),
  preferences = usePreferences(),
  messages = useMessages(),
  { label } = useDictionaries(['sys_notice_priority_level'])
const tab = ref('notices'),
  rows = ref<Notice[]>([]),
  page = ref(1),
  total = ref(0),
  unread = ref(false),
  busy = ref(false),
  error = ref(''),
  detail = ref<Notice>(),
  selected = ref<Notice[]>([])
watch(
  () => route.query.tab,
  (value) => {
    if (typeof value === 'string' && ['notices', 'tasks', 'private'].includes(value))
      tab.value = value
  },
  { immediate: true },
)
watch(
  () => route.query.focus,
  async (id) => {
    if (typeof id === 'string' && (!route.query.tab || route.query.tab === 'notices'))
      try {
        detail.value = await put('/message/notices/inbox/' + encodeURIComponent(id) + '/read')
        await messages.refresh()
      } catch (e) {
        error.value = (e as Error).message
      }
  },
  { immediate: true },
)
async function load() {
  const isCurrent = loadGate()
  busy.value = true
  try {
    const result = await get<PageResult<Notice>>('/message/notices/inbox', {
      page: page.value,
      size: 20,
      unread: unread.value,
    })
    if (!isCurrent()) return
    rows.value = result.items
    total.value = Number(result.total)
    error.value = ''
  } catch (e) {
    if (!isCurrent()) return
    error.value = (e as Error).message
  } finally {
    if (isCurrent()) { busy.value = false }
  }
}
async function open(row: Notice) {
  try {
    detail.value = await put('/message/notices/inbox/' + row.id + '/read')
    await load()
    await messages.refresh()
  } catch (e) {
    error.value = (e as Error).message
  }
}
async function markAll() {
  try {
    await put('/message/notices/inbox/read-all')
    await load()
    await messages.refresh()
  } catch (e) {
    error.value = (e as Error).message
  }
}
async function markSelected() {
  try {
    await put('/message/notices/inbox/read-many', { ids: selected.value.map((row) => row.id) })
    await load()
    await messages.refresh()
  } catch (e) {
    error.value = (e as Error).message
  }
}
watch(
  [unread, locale],
  () => {
    page.value = 1
    void load()
  },
  { immediate: true },
)
useMessageRefresh(load, ['notice'])

import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { Card, CardContent } from '@/components/ui/card'
import { Switch } from '@/components/ui/switch'
import SafeHtml from '@/components/business/SafeHtml.vue'
const detailOpen = computed({get:()=>!!detail.value,set:value=>{if(!value) detail.value=undefined}})
const columns = computed(() => [{key:'title',title:t('message.title'),width:280},{key:'priority',title:t('message.priority')},{key:'readAt',title:t('common.status')},{key:'createdAt',title:t('common.createdAt'),width:200}])
</script>
<template><PageHeader :title="t('message.center')"><StatusBadge :label="t('message.connections.' + messages.state)" :tone="messages.state === 'connected' ? 'success' : 'neutral'" /></PageHeader><div class="mb-6 grid gap-4 sm:grid-cols-3"><Card v-for="item in [{ key: 'message.unreadNotices', value: messages.summary.unreadNotices }, { key: 'message.pendingTasks', value: messages.summary.pendingTodos }, { key: 'message.unreadPrivate', value: messages.summary.unreadMessages }]" :key="item.key" class="shadow-none"><CardContent><p class="text-sm text-muted-foreground">{{ t(item.key) }}</p><p class="mt-3 text-3xl font-semibold tabular-nums">{{ item.value }}</p></CardContent></Card></div><Tabs v-model="tab"><TabsList class="mb-5"><TabsTrigger value="notices">{{ t('message.notices') }}</TabsTrigger><TabsTrigger value="tasks">{{ t('message.myTasks') }}</TabsTrigger><TabsTrigger value="private">{{ t('message.privateMessages') }}</TabsTrigger></TabsList><TabsContent value="notices"><div class="mb-4 flex flex-wrap items-center gap-2"><div class="mr-3 flex items-center gap-2"><Switch id="only-unread" v-model="unread" /><Label for="only-unread">{{ t('message.unreadOnly') }}</Label></div><Button variant="outline" :disabled="busy" @click="load">{{ t('common.refresh') }}</Button><Button variant="outline" @click="markAll">{{ t('message.markAllRead') }}</Button><Button variant="outline" :disabled="!selected.length" @click="markSelected">{{ t('message.markSelectedRead') }}</Button></div><ErrorNotice :message="error" /><DataTable :rows="rows" :columns="columns" :loading="busy" :page="page" :total="total" selectable @select="selected = $event" @update:page="page = $event; load()"><template #title="{ row }"><button class="max-w-full truncate text-left text-primary hover:underline" @click="open(row)">{{ row.title }}</button></template><template #priority="{ row }">{{ label('sys_notice_priority_level', row.priority) }}</template><template #readAt="{ row }"><StatusBadge :label="t(row.readAt ? 'message.read' : 'message.unread')" :tone="row.readAt ? 'neutral' : 'warning'" /></template><template #createdAt="{ row }">{{ formatInstant(row.createdAt, preferences.timeZone, locale) }}</template></DataTable></TabsContent><TabsContent value="tasks"><TasksPage /></TabsContent><TabsContent value="private"><PrivatePage /></TabsContent></Tabs><EditorDialog v-model="detailOpen" :title="detail?.title || t('common.details')" wide><template v-if="detail"><SafeHtml :html="detail.content" /><AttachmentList class="mt-5" :model-value="detail.fileIds" /></template><template #footer><Button variant="outline" @click="detailOpen = false">{{ t('common.cancel') }}</Button></template></EditorDialog></template>
