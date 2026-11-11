<script setup lang="ts">
import TableWorkspace from '@/components/business/TableWorkspace.vue'
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
import { useRoute, useRouter, type LocationQuery } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useSubmissionPool } from '@/composables/useSubmissionPool'
import { useMessageDetail } from './useMessageDetail'
import OperationStatus from '@/components/business/OperationStatus.vue'
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
  router = useRouter(),
  preferences = usePreferences(),
  messages = useMessages(),
  { label } = useDictionaries(['sys_notice_priority_level'])
const actions = useSubmissionPool(), rowAction = actions.last
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
    tab.value = typeof value === 'string' && ['notices', 'tasks', 'private'].includes(value) ? value : 'notices'
  },
  { immediate: true },
)
watch(tab, value => {
  if (value === (route.query.tab || 'notices')) return
  const query: LocationQuery = { ...route.query, tab: value }
  delete query.focus
  void router.replace({ query })
})
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
async function refresh() { await load(); await messages.refresh() }
const detailAction = useMessageDetail<Notice>(actions, 'message.notices.inbox', value => { detail.value = value }, refresh, message => { error.value = message })
function open(row: Notice) { return detailAction.open(row.id, '/message/notices/inbox/' + encodeURIComponent(row.id), !row.readAt) }
async function markAll() {
  const resource = 'message.notices.inbox.*', action = actions.get(resource)
  const result = await action.run({ operation: 'notices.read-all', resource, protocol: 'LOCAL' }, () => options => put('/message/notices/inbox/read-all', undefined, options))
  if (result.kind === 'success' || result.kind === 'completed') await refresh(); else error.value = action.error.value
}
async function markSelected() {
  const ids = [...new Set(selected.value.map(row => row.id))].sort()
  if (!ids.length) return
  const resources = ids.map(id => 'message.notices.inbox.' + id), resource = resources[0]!, action = actions.get(resource)
  const result = await action.run({ operation: 'notices.read-many', resource, resources, protocol: 'LOCAL' }, () => options => put('/message/notices/inbox/read-many', { ids }, options))
  if (result.kind === 'success' || result.kind === 'completed') await refresh(); else error.value = action.error.value
}
async function checkRow() { if (await rowAction.value?.check() === 'SUCCEEDED') await refresh(); error.value = rowAction.value?.error.value || '' }
watch(() => route.query.focus, id => {
  if (typeof id === 'string' && (!route.query.tab || route.query.tab === 'notices')) void detailAction.open(id, '/message/notices/inbox/' + encodeURIComponent(id), true)
}, { immediate: true })
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
<template><TableWorkspace class="message-center-workspace"><template #tools><PageHeader :title="t('message.center')"><StatusBadge :label="t('message.connections.' + messages.state)" :tone="messages.state === 'connected' ? 'success' : 'neutral'" /></PageHeader><div class="inbox-summary mb-6 grid gap-4 sm:grid-cols-3"><Card v-for="item in [{ key: 'message.unreadNotices', value: messages.summary.unreadNotices }, { key: 'message.pendingTasks', value: messages.summary.pendingTodos }, { key: 'message.unreadPrivate', value: messages.summary.unreadMessages }]" :key="item.key" class="shadow-none"><CardContent><p class="text-sm text-muted-foreground">{{ t(item.key) }}</p><p class="mt-3 text-3xl font-semibold tabular-nums">{{ item.value }}</p></CardContent></Card></div></template><Tabs v-model="tab"><TabsList class="mb-5"><TabsTrigger value="notices">{{ t('message.notices') }}</TabsTrigger><TabsTrigger value="tasks">{{ t('message.myTasks') }}</TabsTrigger><TabsTrigger value="private">{{ t('message.privateMessages') }}</TabsTrigger></TabsList><TabsContent value="notices"><TableWorkspace><template #tools><div class="mb-4 flex flex-wrap items-center gap-2"><div class="mr-3 flex items-center gap-2"><Switch id="only-unread" v-model="unread" /><Label for="only-unread">{{ t('message.unreadOnly') }}</Label></div><Button variant="outline" :disabled="busy" @click="load">{{ t('common.refresh') }}</Button><Button variant="outline" :disabled="actions.busy('message.notices.inbox.*')" @click="markAll">{{ t('message.markAllRead') }}</Button><Button variant="outline" :disabled="!selected.length || selected.some(row => actions.busy('message.notices.inbox.' + row.id))" @click="markSelected">{{ t('message.markSelectedRead') }}</Button></div><ErrorNotice :message="error" /><OperationStatus v-if="rowAction" :slow="rowAction.slow.value" :unresolved="rowAction.unresolved.value" :querying="rowAction.querying.value" queryable @check="checkRow" /></template><DataTable :rows="rows" :columns="columns" :loading="busy" :page="page" :total="total" selectable @select="selected = $event" @update:page="page = $event; load()"><template #title="{ row }"><button class="max-w-full truncate text-left text-primary hover:underline" @click="open(row)">{{ row.title }}</button></template><template #priority="{ row }">{{ label('sys_notice_priority_level', row.priority) }}</template><template #readAt="{ row }"><StatusBadge :label="t(row.readAt ? 'message.read' : 'message.unread')" :tone="row.readAt ? 'neutral' : 'warning'" /></template><template #createdAt="{ row }">{{ formatInstant(row.createdAt, preferences.timeZone, locale) }}</template></DataTable></TableWorkspace></TabsContent><TabsContent value="tasks"><TasksPage /></TabsContent><TabsContent value="private"><PrivatePage /></TabsContent></Tabs></TableWorkspace><EditorDialog v-model="detailOpen" :title="detail?.title || t('common.details')" wide><template v-if="detail"><SafeHtml :html="detail.content" /><AttachmentList class="mt-5" :model-value="detail.fileIds" /></template><template #footer><Button variant="outline" @click="detailOpen = false">{{ t('common.cancel') }}</Button></template></EditorDialog></template>

<style scoped>
/* Nested filters must leave enough space below the sticky table header for row controls. */
.message-center-workspace :deep(.table-workspace-tools) { max-height: 30%; }
</style>
