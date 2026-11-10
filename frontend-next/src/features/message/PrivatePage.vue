<script setup lang="ts">
const findRecipientsGate = useRequestGate()
const openGate = useRequestGate()

import { useRequestGate } from '@/composables/useRequestGate'
const loadGate = useRequestGate()

import { computed } from 'vue'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import PageHeader from '@/components/business/PageHeader.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import DataTable from '@/components/business/DataTable.vue'
import EditorDialog from '@/components/business/EditorDialog.vue'
import EntitySelect from '@/components/business/EntitySelect.vue'
import StatusBadge from '@/components/business/StatusBadge.vue'

import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { get, post, put, type PageResult } from '@/api/http'
import { useAuth } from '@/stores/auth'
import { usePreferences } from '@/stores/preferences'
import { useMessages } from '@/stores/messages'
import type { Option } from '@/features/message/types'
import { useMessageRefresh } from '@/features/message/realtime'
import { formatInstant } from '@/lib/time'
interface PrivateMessage {
  id: string
  senderId: string
  recipientId: string
  senderName: string
  recipientName: string
  content: string
  createdAt: string
  readAt: string | null
}
const { t, locale } = useI18n(),
  auth = useAuth(),
  preferences = usePreferences(),
  messages = useMessages()
const box = ref('inbox'),
  peerId = ref<string>(),
  rows = ref<PrivateMessage[]>([]),
  page = ref(1),
  total = ref(0),
  busy = ref(false),
  error = ref(''),
  detail = ref<PrivateMessage>()
const dialog = ref(false),
  recipientId = ref<string>(),
  content = ref(''),
  recipients = ref<Option[]>([]),
  saving = ref(false)
async function load() {
  const isCurrent = loadGate()
  busy.value = true
  error.value = ''
  try {
    const result = await get<PageResult<PrivateMessage>>('/message/private', {
      page: page.value,
      size: 20,
      box: box.value,
      peerId: peerId.value,
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
async function findRecipients(keyword = '') {
  const isCurrent = findRecipientsGate()
  try {
    const loaded: typeof recipients.value = await get<Option[]>('/message/private/recipients', { keyword })
    if (!isCurrent()) return
    recipients.value = loaded
  } catch (e) {
    if (!isCurrent()) return
    error.value = (e as Error).message
  }
}
function compose(row?: PrivateMessage) {
  recipientId.value = row?.senderId
  content.value = ''
  error.value = ''
  dialog.value = true
  void findRecipients()
}
async function send() {
  if (saving.value) return
  if (!recipientId.value || !content.value.trim()) {
    error.value = t('common.required')
    return
  }
  saving.value = true
  try {
    await post('/message/private', { recipientId: recipientId.value, content: content.value })
    dialog.value = false
    await load()
    await messages.refresh()
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    saving.value = false
  }
}
async function open(row: PrivateMessage) {
  const isCurrent = openGate()
  try {
    const loaded: typeof detail.value = row.recipientId === auth.user?.id ? await put('/message/private/' + row.id + '/read') : row
    if (!isCurrent()) return
    detail.value = loaded
    await messages.refresh()
    await load()
  } catch (e) {
    if (!isCurrent()) return
    error.value = (e as Error).message
  }
}
async function readAll() {
  try {
    await put('/message/private/read-all')
    await load()
    await messages.refresh()
  } catch (e) {
    error.value = (e as Error).message
  }
}
function conversation(row: PrivateMessage) {
  peerId.value = row.senderId === auth.user?.id ? row.recipientId : row.senderId
  box.value = 'conversation'
  page.value = 1
  void load()
}
watch(
  [box, locale],
  () => {
    page.value = 1
    void load()
  },
  { immediate: true },
)
useMessageRefresh(load, ['private-message'])

import { Textarea } from '@/components/ui/textarea'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
const detailOpen = computed({get:()=>!!detail.value,set:value=>{if(!value) detail.value=undefined}})
const columns = computed(() => [{key:'senderName',title:t('message.sender'),width:150},{key:'recipientName',title:t('message.recipient'),width:150},{key:'content',title:t('message.content'),width:280},{key:'createdAt',title:t('common.createdAt'),width:190},{key:'readAt',title:t('common.status')},{key:'actions',title:t('common.actions'),width:180}])
</script>
<template><PageHeader :title="t('message.privateMessages')"><Button variant="outline" :disabled="busy" @click="load">{{ t('common.refresh') }}</Button><Button v-if="box === 'inbox'" variant="outline" @click="readAll">{{ t('message.markAllRead') }}</Button><Button @click="compose()">{{ t('message.compose') }}</Button></PageHeader><Tabs v-model="box"><TabsList class="mb-4"><TabsTrigger value="inbox">{{ t('message.inbox') }}</TabsTrigger><TabsTrigger value="outbox">{{ t('message.outbox') }}</TabsTrigger><TabsTrigger v-if="peerId" value="conversation">{{ t('message.conversation') }}</TabsTrigger></TabsList></Tabs><ErrorNotice :message="error" /><DataTable :rows="rows" :columns="columns" :loading="busy" :page="page" :total="total" @update:page="page = $event; load()"><template #content="{ row }"><button class="block max-w-full truncate text-left text-primary hover:underline" @click="open(row)">{{ row.content }}</button></template><template #createdAt="{ row }">{{ formatInstant(row.createdAt, preferences.timeZone, locale) }}</template><template #readAt="{ row }"><StatusBadge :label="t(row.readAt ? 'message.read' : 'message.unread')" :tone="row.readAt ? 'neutral' : 'warning'" /></template><template #actions="{ row }"><div class="flex gap-1"><Button v-if="row.recipientId === auth.user?.id" variant="ghost" size="sm" @click="compose(row)">{{ t('message.reply') }}</Button><Button variant="ghost" size="sm" @click="conversation(row)">{{ t('message.conversation') }}</Button></div></template></DataTable><EditorDialog v-model="dialog" :title="t('message.compose')" :busy="saving" :dirty="!!content" :save-label="t('message.send')" @save="send"><ErrorNotice :message="error" /><div class="space-y-4"><div class="space-y-2"><Label for="private-recipient">{{ t('message.recipient') }}</Label><EntitySelect id="private-recipient" :model-value="recipientId || null" :options="recipients" remote :placeholder="t('message.recipient')" @search="findRecipients" @update:model-value="recipientId = typeof $event === 'string' ? $event : undefined" /></div><div class="space-y-2"><Label for="private-content">{{ t('message.content') }}</Label><Textarea id="private-content" v-model="content" :rows="6" maxlength="10000" /></div></div></EditorDialog><EditorDialog v-model="detailOpen" :title="t('message.privateMessages')"><p class="whitespace-pre-wrap break-words">{{ detail?.content }}</p><template #footer><Button variant="outline" @click="detailOpen = false">{{ t('common.cancel') }}</Button></template></EditorDialog></template>
