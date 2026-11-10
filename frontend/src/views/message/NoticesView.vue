<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import { get, post, put, remove, type PageResult } from '@/api/http'
import { usePreferences } from '@/stores/preferences'
import { useAuth } from '@/stores/auth'
import { formatInstant } from '@/lib/time'
import { noticeInput, type Notice, type Option } from '@/features/message/types'
import { useDictionaries } from '@/features/message/dictionaries'
import RichEditor from '@/components/RichEditor.vue'
import AttachmentList from '@/components/AttachmentList.vue'
const { t, locale } = useI18n(),
  preferences = usePreferences(),
  auth = useAuth(),
  route = useRoute()
const {
  options: dictionaries,
  label,
  error: dictionaryError,
} = useDictionaries(['sys_notice_type', 'sys_notice_priority_level'])
const rows = ref<Notice[]>([]),
  selected = ref<Notice[]>([]),
  keyword = ref(''),
  type = ref(''),
  priority = ref<number>(),
  audienceType = ref(''),
  trash = ref(false)
const page = ref(1),
  total = ref(0),
  busy = ref(false),
  error = ref(''),
  saving = ref(false),
  dialog = ref(false),
  editingId = ref(''),
  detail = ref<Notice>()
const form = reactive(noticeInput()),
  audience = ref<Option[]>([]),
  readers = ref<{ userId: string; name: string; readAt: string | null }[]>([]),
  readerPage = ref(1),
  readerTotal = ref(0),
  readerId = ref('')
const audienceTypes = ['ALL', 'ROLE', 'DEPARTMENT', 'POST', 'USER']
async function load() {
  busy.value = true
  error.value = ''
  try {
    const result = await get<PageResult<Notice>>('/message/notices', {
      page: page.value,
      size: 20,
      keyword: keyword.value,
      type: type.value || undefined,
      priority: priority.value,
      audienceType: audienceType.value || undefined,
      trash: trash.value,
    })
    rows.value = result.items
    total.value = Number(result.total)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
async function loadAudience(keyword = '') {
  try {
    audience.value =
      form.audienceType === 'ALL'
        ? []
        : await get<Option[]>('/message/notices/audience', { type: form.audienceType, keyword })
  } catch (e) {
    error.value = (e as Error).message
  }
}
function changeAudience() {
  form.audienceIds = []
  void loadAudience()
}
async function edit(row?: Notice) {
  Object.assign(form, noticeInput(row))
  editingId.value = row?.id || ''
  error.value = ''
  dialog.value = true
  await loadAudience()
}
async function save() {
  if (
    !form.title.trim() ||
    !form.content ||
    (form.audienceType !== 'ALL' && !form.audienceIds.length)
  ) {
    error.value = t('common.required')
    return
  }
  saving.value = true
  error.value = ''
  try {
    if (editingId.value) await put('/message/notices/' + editingId.value, form)
    else await post('/message/notices', form)
    dialog.value = false
    ElMessage.success(t('common.saved'))
    await load()
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    saving.value = false
  }
}
async function erase(row: Notice) {
  try {
    await ElMessageBox.confirm(t('message.recycleConfirm'), t('common.confirm'), {
      type: 'warning',
    })
    await remove('/message/notices/' + row.id)
    await load()
  } catch (e) {
    if (e instanceof Error) error.value = e.message
  }
}
async function restore(row: Notice) {
  try {
    await put('/message/notices/' + row.id + '/restore')
    await load()
  } catch (e) {
    error.value = (e as Error).message
  }
}
async function batch() {
  try {
    await ElMessageBox.confirm(
      t(trash.value ? 'message.restoreConfirm' : 'message.recycleConfirm'),
      t('common.confirm'),
      { type: 'warning' },
    )
    await post('/message/notices/' + (trash.value ? 'batch-restore' : 'batch-delete'), {
      ids: selected.value.map((row) => row.id),
    })
    await load()
  } catch (e) {
    if (e instanceof Error) error.value = e.message
  }
}
async function loadReaders() {
  try {
    const result = await get<PageResult<{ userId: string; name: string; readAt: string | null }>>(
      '/message/notices/' + readerId.value + '/readers',
      { page: readerPage.value, size: 20 },
    )
    readers.value = result.items
    readerTotal.value = Number(result.total)
  } catch (e) {
    error.value = (e as Error).message
  }
}
function openReaders(row: Notice) {
  readerId.value = row.id
  readerPage.value = 1
  void loadReaders()
}
function search() {
  page.value = 1
  void load()
}
watch([trash, locale], search, { immediate: true })
watch(
  () => route.query.focus,
  async (value) => {
    if (typeof value === 'string' && !trash.value)
      try {
        detail.value = await get<Notice>('/message/notices/' + encodeURIComponent(value))
      } catch (e) {
        error.value = (e as Error).message
      }
  },
  { immediate: true },
)
</script>
<template>
  <el-card shadow="never"
    ><div class="toolbar">
      <el-input
        v-model="keyword"
        :placeholder="t('common.search')"
        clearable
        @keyup.enter="search"
      /><el-select v-model="type" clearable :placeholder="t('message.type')"
        ><el-option
          v-for="option in dictionaries.sys_notice_type"
          :key="option.value"
          :value="option.value"
          :label="option.label" /></el-select
      ><el-select v-model="priority" clearable :placeholder="t('message.priority')"
        ><el-option
          v-for="option in dictionaries.sys_notice_priority_level"
          :key="option.value"
          :value="Number(option.value)"
          :label="option.label" /></el-select
      ><el-select v-model="audienceType" clearable :placeholder="t('message.audience')"
        ><el-option
          v-for="value in audienceTypes"
          :key="value"
          :value="value"
          :label="t('message.audiences.' + value)" /></el-select
      ><el-button @click="search">{{ t('common.search') }}</el-button
      ><el-switch v-model="trash" :active-text="t('message.recycleBin')" /><el-button
        v-if="!trash"
        v-permission="'message:notice:create'"
        type="primary"
        @click="edit()"
        >{{ t('message.publish') }}</el-button
      ><el-button
        v-if="auth.hasPermission(trash ? 'message:notice:restore' : 'message:notice:delete')"
        :disabled="!selected.length"
        @click="batch"
        >{{ t(trash ? 'message.restoreSelected' : 'message.recycleSelected') }}</el-button
      >
    </div>
    <el-alert
      v-if="error || dictionaryError"
      :title="error || dictionaryError"
      type="error"
      :closable="false"
    />
    <el-table v-loading="busy" :data="rows" row-key="id" @selection-change="selected = $event"
      ><el-table-column type="selection" width="44" /><el-table-column
        :label="t('message.title')"
        min-width="220"
        ><template #default="{ row }"
          ><el-button v-if="!trash" link @click="detail = row">{{ row.title }}</el-button
          ><span v-else>{{ row.title }}</span></template
        ></el-table-column
      ><el-table-column :label="t('message.type')" width="100"
        ><template #default="{ row }">{{
          label('sys_notice_type', row.type)
        }}</template></el-table-column
      ><el-table-column :label="t('message.priority')" width="100"
        ><template #default="{ row }"
          ><el-tag :type="row.priority === 3 ? 'danger' : 'info'">{{
            label('sys_notice_priority_level', row.priority)
          }}</el-tag></template
        ></el-table-column
      ><el-table-column :label="t('message.audience')" width="150"
        ><template #default="{ row }">{{
          t('message.audiences.' + row.audienceType)
        }}</template></el-table-column
      ><el-table-column :label="t('message.readStats')" width="120"
        ><template #default="{ row }"
          ><el-button v-if="!trash" link @click="openReaders(row)">{{
            t('message.readRatio', { read: row.readCount, total: row.recipientCount })
          }}</el-button
          ><span v-else>{{
            t('message.readRatio', { read: row.readCount, total: row.recipientCount })
          }}</span></template
        ></el-table-column
      ><el-table-column :label="t('common.createdAt')" width="190"
        ><template #default="{ row }">{{
          formatInstant(row.createdAt, preferences.timeZone, locale)
        }}</template></el-table-column
      ><el-table-column :label="t('common.actions')" width="190"
        ><template #default="{ row }"
          ><template v-if="!trash"
            ><el-button v-permission="'message:notice:update'" link @click="edit(row)">{{
              t('common.edit')
            }}</el-button
            ><el-button
              v-permission="'message:notice:delete'"
              link
              type="danger"
              @click="erase(row)"
              >{{ t('message.recycle') }}</el-button
            ></template
          ><el-button v-else v-permission="'message:notice:restore'" link @click="restore(row)">{{
            t('message.restore')
          }}</el-button></template
        ></el-table-column
      ></el-table
    >
    <el-pagination
      v-model:current-page="page"
      :page-size="20"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="load"
    />
  </el-card>
  <el-dialog
    v-model="dialog"
    :title="t(editingId ? 'message.editNotice' : 'message.publish')"
    width="min(860px, 95vw)"
    destroy-on-close
    ><el-alert v-if="error" :title="error" type="error" :closable="false" /><el-form
      label-width="130px"
      ><el-form-item :label="t('message.title')" required
        ><el-input v-model="form.title" maxlength="255" /></el-form-item
      ><el-form-item :label="t('message.type')"
        ><el-select v-model="form.type"
          ><el-option
            v-for="option in dictionaries.sys_notice_type"
            :key="option.value"
            :value="option.value"
            :label="option.label" /></el-select></el-form-item
      ><el-form-item :label="t('message.priority')"
        ><el-select v-model="form.priority"
          ><el-option
            v-for="option in dictionaries.sys_notice_priority_level"
            :key="option.value"
            :value="Number(option.value)"
            :label="option.label" /></el-select></el-form-item
      ><el-form-item :label="t('message.audience')"
        ><el-select v-model="form.audienceType" @change="changeAudience"
          ><el-option
            v-for="value in audienceTypes"
            :key="value"
            :value="value"
            :label="t('message.audiences.' + value)" /></el-select></el-form-item
      ><el-form-item v-if="form.audienceType !== 'ALL'" :label="t('message.recipients')" required
        ><el-select
          v-model="form.audienceIds"
          multiple
          filterable
          remote
          :remote-method="loadAudience"
          ><el-option
            v-for="option in audience"
            :key="option.value"
            :value="option.value"
            :label="option.label" /></el-select></el-form-item
      ><el-form-item :label="t('message.content')" required
        ><RichEditor v-model="form.content" /></el-form-item
      ><el-form-item :label="t('message.attachments')"
        ><AttachmentList v-model="form.fileIds" editable /></el-form-item></el-form
    ><template #footer
      ><el-button @click="dialog = false">{{ t('common.cancel') }}</el-button
      ><el-button type="primary" :loading="saving" @click="save">{{
        t('common.save')
      }}</el-button></template
    ></el-dialog
  >
  <el-dialog
    :model-value="!!detail"
    :title="detail?.title"
    width="min(860px, 95vw)"
    @close="detail = undefined"
    ><template v-if="detail"
      ><div class="notice-content" v-html="detail.content" />
      <AttachmentList :model-value="detail.fileIds" /></template
  ></el-dialog>
  <el-dialog
    :model-value="!!readerId"
    :title="t('message.readers')"
    width="680px"
    @close="readerId = ''"
    ><el-table :data="readers"
      ><el-table-column prop="name" :label="t('common.name')" /><el-table-column
        :label="t('message.readAt')"
        ><template #default="{ row }">{{
          row.readAt ? formatInstant(row.readAt, preferences.timeZone, locale) : t('message.unread')
        }}</template></el-table-column
      ></el-table
    ><el-pagination
      v-model:current-page="readerPage"
      :page-size="20"
      :total="readerTotal"
      layout="total, prev, pager, next"
      @current-change="loadReaders"
  /></el-dialog>
</template>
