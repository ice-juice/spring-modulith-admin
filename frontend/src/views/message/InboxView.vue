<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { get, put, type PageResult } from '@/api/http'
import { usePreferences } from '@/stores/preferences'
import { useMessages } from '@/stores/messages'
import { useDictionaries } from '@/features/message/dictionaries'
import { useMessageRefresh } from '@/features/message/realtime'
import type { Notice } from '@/features/message/types'
import { formatInstant } from '@/lib/time'
import TasksView from './TasksView.vue'
import PrivateView from './PrivateView.vue'
import AttachmentList from '@/components/AttachmentList.vue'
const { t, locale } = useI18n(),
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
async function load() {
  busy.value = true
  try {
    const result = await get<PageResult<Notice>>('/message/notices/inbox', {
      page: page.value,
      size: 20,
      unread: unread.value,
    })
    rows.value = result.items
    total.value = Number(result.total)
    error.value = ''
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
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
</script>
<template>
  <div class="summary-grid">
    <el-card shadow="never"
      ><small>{{ t('message.unreadNotices') }}</small
      ><strong>{{ messages.summary.unreadNotices }}</strong></el-card
    ><el-card shadow="never"
      ><small>{{ t('message.pendingTasks') }}</small
      ><strong>{{ messages.summary.pendingTodos }}</strong></el-card
    ><el-card shadow="never"
      ><small>{{ t('message.unreadPrivate') }}</small
      ><strong>{{ messages.summary.unreadMessages }}</strong></el-card
    >
  </div>
  <el-tabs v-model="tab"
    ><el-tab-pane :label="t('message.notices')" name="notices"
      ><el-card shadow="never"
        ><div class="toolbar">
          <el-switch v-model="unread" :active-text="t('message.unreadOnly')" /><el-button
            @click="load"
            >{{ t('common.refresh') }}</el-button
          ><el-button @click="markAll">{{ t('message.markAllRead') }}</el-button
          ><el-button :disabled="!selected.length" @click="markSelected">{{
            t('message.markSelectedRead')
          }}</el-button
          ><el-tag :type="messages.state === 'connected' ? 'success' : 'info'">{{
            t('message.connections.' + messages.state)
          }}</el-tag>
        </div>
        <el-alert v-if="error" :title="error" type="error" :closable="false" /><el-table
          v-loading="busy"
          :data="rows"
          row-key="id"
          @selection-change="selected = $event"
          ><el-table-column type="selection" width="44" /><el-table-column
            :label="t('message.title')"
            min-width="260"
            ><template #default="{ row }"
              ><el-button link @click="open(row)">{{ row.title }}</el-button></template
            ></el-table-column
          ><el-table-column :label="t('message.priority')" width="120"
            ><template #default="{ row }">{{
              label('sys_notice_priority_level', row.priority)
            }}</template></el-table-column
          ><el-table-column :label="t('common.status')" width="120"
            ><template #default="{ row }"
              ><el-tag :type="row.readAt ? 'info' : 'warning'">{{
                t(row.readAt ? 'message.read' : 'message.unread')
              }}</el-tag></template
            ></el-table-column
          ><el-table-column :label="t('common.createdAt')" width="200"
            ><template #default="{ row }">{{
              formatInstant(row.createdAt, preferences.timeZone, locale)
            }}</template></el-table-column
          ></el-table
        ><el-pagination
          v-model:current-page="page"
          :page-size="20"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="load" /></el-card></el-tab-pane
    ><el-tab-pane :label="t('message.myTasks')" name="tasks" lazy><TasksView /></el-tab-pane
    ><el-tab-pane :label="t('message.privateMessages')" name="private" lazy
      ><PrivateView /></el-tab-pane
  ></el-tabs>
  <el-dialog
    :model-value="!!detail"
    :title="detail?.title"
    width="min(860px,95vw)"
    @close="detail = undefined"
    ><template v-if="detail"
      ><div class="notice-content" v-html="detail.content" />
      <AttachmentList :model-value="detail.fileIds" /></template
  ></el-dialog>
</template>
