<script setup lang="ts">
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
  busy.value = true
  error.value = ''
  try {
    const result = await get<PageResult<PrivateMessage>>('/message/private', {
      page: page.value,
      size: 20,
      box: box.value,
      peerId: peerId.value,
    })
    rows.value = result.items
    total.value = Number(result.total)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
async function findRecipients(keyword = '') {
  try {
    recipients.value = await get<Option[]>('/message/private/recipients', { keyword })
  } catch (e) {
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
  try {
    detail.value =
      row.recipientId === auth.user?.id ? await put('/message/private/' + row.id + '/read') : row
    await messages.refresh()
    await load()
  } catch (e) {
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
</script>
<template>
  <el-card shadow="never"
    ><div class="toolbar">
      <el-radio-group v-model="box"
        ><el-radio-button value="inbox">{{ t('message.inbox') }}</el-radio-button
        ><el-radio-button value="outbox">{{ t('message.outbox') }}</el-radio-button
        ><el-radio-button v-if="peerId" value="conversation">{{
          t('message.conversation')
        }}</el-radio-button></el-radio-group
      ><el-button @click="load">{{ t('common.refresh') }}</el-button
      ><el-button type="primary" @click="compose()">{{ t('message.compose') }}</el-button
      ><el-button v-if="box === 'inbox'" @click="readAll">{{ t('message.markAllRead') }}</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" /><el-table
      v-loading="busy"
      :data="rows"
      row-key="id"
      ><el-table-column
        prop="senderName"
        :label="t('message.sender')"
        width="150"
      /><el-table-column
        prop="recipientName"
        :label="t('message.recipient')"
        width="150"
      /><el-table-column :label="t('message.content')" min-width="260"
        ><template #default="{ row }"
          ><el-button link class="message-excerpt" @click="open(row)">{{
            row.content
          }}</el-button></template
        ></el-table-column
      ><el-table-column :label="t('common.createdAt')" width="190"
        ><template #default="{ row }">{{
          formatInstant(row.createdAt, preferences.timeZone, locale)
        }}</template></el-table-column
      ><el-table-column :label="t('common.status')" width="100"
        ><template #default="{ row }"
          ><el-tag :type="row.readAt ? 'info' : 'warning'">{{
            t(row.readAt ? 'message.read' : 'message.unread')
          }}</el-tag></template
        ></el-table-column
      ><el-table-column :label="t('common.actions')" width="170"
        ><template #default="{ row }"
          ><el-button v-if="row.recipientId === auth.user?.id" link @click="compose(row)">{{
            t('message.reply')
          }}</el-button
          ><el-button link @click="conversation(row)">{{
            t('message.conversation')
          }}</el-button></template
        ></el-table-column
      ></el-table
    ><el-pagination
      v-model:current-page="page"
      :page-size="20"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="load"
  /></el-card>
  <el-dialog v-model="dialog" :title="t('message.compose')" width="min(680px,95vw)"
    ><el-alert v-if="error" :title="error" type="error" :closable="false" /><el-form
      label-width="120px"
      ><el-form-item :label="t('message.recipient')" required
        ><el-select v-model="recipientId" filterable remote :remote-method="findRecipients"
          ><el-option
            v-for="option in recipients"
            :key="option.value"
            :value="option.value"
            :label="option.label" /></el-select></el-form-item
      ><el-form-item :label="t('message.content')" required
        ><el-input
          v-model="content"
          type="textarea"
          :rows="6"
          maxlength="10000"
          show-word-limit /></el-form-item></el-form
    ><template #footer
      ><el-button @click="dialog = false">{{ t('common.cancel') }}</el-button
      ><el-button type="primary" :loading="saving" @click="send">{{
        t('message.send')
      }}</el-button></template
    ></el-dialog
  >
  <el-dialog
    :model-value="!!detail"
    :title="t('message.privateMessages')"
    width="min(680px,95vw)"
    @close="detail = undefined"
    ><p v-if="detail" class="plain-content">{{ detail.content }}</p></el-dialog
  >
</template>
