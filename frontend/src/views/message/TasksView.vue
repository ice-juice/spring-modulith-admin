<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { get, post, put, remove, type PageResult } from '@/api/http'
import { usePreferences } from '@/stores/preferences'
import { useMessages } from '@/stores/messages'
import { useDictionaries } from '@/features/message/dictionaries'
import { taskForm, taskInput, activeTask, type Task } from '@/features/message/tasks'
import { useMessageRefresh } from '@/features/message/realtime'
import type { Option } from '@/features/message/types'
import { formatInstant, localDateTimeToInstant } from '@/lib/time'
const props = defineProps<{ managed?: boolean }>(),
  { t, locale } = useI18n(),
  preferences = usePreferences(),
  messages = useMessages(),
  router = useRouter(),
  route = useRoute()
const {
  options,
  label,
  error: dictionaryError,
} = useDictionaries(['todo_state', 'todo_priority_level', 'todo_type'])
const rows = ref<Task[]>([]),
  selected = ref<Task[]>([]),
  keyword = ref(''),
  state = ref<number>(),
  type = ref<number>(),
  dates = ref<[string, string]>(),
  dateField = ref('dueAt')
const page = ref(1),
  total = ref(0),
  busy = ref(false),
  error = ref(''),
  dialog = ref(false),
  saving = ref(false),
  editingId = ref(''),
  detail = ref<Task>(),
  recipients = ref<Option[]>([]),
  form = reactive(taskForm(undefined, preferences.timeZone))
const dateFields = ['createdAt', 'updatedAt', 'dueAt', 'completedAt', 'cancelledAt'],
  sourceFields = ['system', 'messageId', 'requestId', 'url', 'senderKey', 'receiverKey'] as const
async function load() {
  busy.value = true
  error.value = ''
  try {
    const result = await get<PageResult<Task>>(
      '/message/todos' + (props.managed ? '/managed' : ''),
      {
        page: page.value,
        size: 20,
        keyword: keyword.value,
        state: state.value,
        type: type.value,
        dateField: dateField.value,
        from: dates.value?.[0]
          ? localDateTimeToInstant(dates.value[0], preferences.timeZone)
          : undefined,
        to: dates.value?.[1]
          ? localDateTimeToInstant(dates.value[1], preferences.timeZone)
          : undefined,
      },
    )
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
    recipients.value = await get<Option[]>('/message/todos/recipients', { keyword })
  } catch (e) {
    error.value = (e as Error).message
  }
}
function edit(row?: Task) {
  Object.assign(form, taskForm(row, preferences.timeZone))
  if (!row) form.userId = null
  editingId.value = row?.id || ''
  dialog.value = true
  error.value = ''
  if (props.managed) void findRecipients()
}
async function save() {
  if (!form.title.trim() || !form.dueAt || (props.managed && !form.userId)) {
    error.value = t('common.required')
    return
  }
  saving.value = true
  error.value = ''
  try {
    const input = taskInput(form, preferences.timeZone)
    if (editingId.value) await put('/message/todos/' + editingId.value, input)
    else await post('/message/todos' + (props.managed ? '/assign' : ''), input)
    dialog.value = false
    await load()
    await messages.refresh()
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    saving.value = false
  }
}
async function transition(row: Task, value: number) {
  try {
    await put('/message/todos/' + row.id + '/state', { state: value })
    await load()
    await messages.refresh()
  } catch (e) {
    error.value = (e as Error).message
  }
}
async function batchState(value: number) {
  try {
    await post('/message/todos/batch-state', {
      ids: selected.value.map((row) => row.id),
      state: value,
    })
    await load()
    await messages.refresh()
  } catch (e) {
    error.value = (e as Error).message
  }
}
async function erase(row?: Task) {
  try {
    await ElMessageBox.confirm(t('common.deleteConfirm'), t('common.confirm'), { type: 'warning' })
    if (row) await remove('/message/todos/' + row.id)
    else await post('/message/todos/batch-delete', { ids: selected.value.map((row) => row.id) })
    await load()
    await messages.refresh()
  } catch (e) {
    if (e instanceof Error) error.value = e.message
  }
}
async function open(row: Task) {
  try {
    detail.value = props.managed ? row : await put<Task>('/message/todos/' + row.id + '/read')
  } catch (e) {
    error.value = (e as Error).message
  }
}
function openLink(url: string) {
  if (url.startsWith('/')) void router.push(url)
  else window.open(url, '_blank', 'noopener,noreferrer')
}
function search() {
  page.value = 1
  void load()
}
watch([() => props.managed, locale], search, { immediate: true })
watch(
  () => route.query.focus,
  async (id) => {
    if (typeof id === 'string')
      try {
        detail.value = await get<Task>(
          '/message/todos/' + (props.managed ? 'managed/' : '') + encodeURIComponent(id),
        )
      } catch (e) {
        error.value = (e as Error).message
      }
  },
  { immediate: true },
)
useMessageRefresh(load, ['todo'])
</script>
<template>
  <el-card shadow="never"
    ><div class="toolbar">
      <el-input
        v-model="keyword"
        :placeholder="t('common.search')"
        clearable
        @keyup.enter="search"
      /><el-select v-model="state" clearable :placeholder="t('common.status')"
        ><el-option
          v-for="option in options.todo_state"
          :key="option.value"
          :value="Number(option.value)"
          :label="option.label" /></el-select
      ><el-select v-model="type" clearable :placeholder="t('message.type')"
        ><el-option
          v-for="option in options.todo_type"
          :key="option.value"
          :value="Number(option.value)"
          :label="option.label" /></el-select
      ><el-select v-model="dateField"
        ><el-option
          v-for="value in dateFields"
          :key="value"
          :value="value"
          :label="t('message.dates.' + value)" /></el-select
      ><el-date-picker
        v-model="dates"
        type="datetimerange"
        value-format="YYYY-MM-DDTHH:mm:ss"
        :start-placeholder="t('message.from')"
        :end-placeholder="t('message.to')"
      />
      <el-button @click="search">{{ t('common.search') }}</el-button
      ><el-button v-if="!managed" type="primary" @click="edit()">{{
        t('message.createTask')
      }}</el-button
      ><el-button v-else v-permission="'message:todo:assign'" type="primary" @click="edit()">{{
        t('message.assignTask')
      }}</el-button
      ><template v-if="!managed"
        ><el-dropdown :disabled="!selected.length" @command="batchState"
          ><el-button :disabled="!selected.length">{{ t('message.batchState') }}</el-button
          ><template #dropdown
            ><el-dropdown-menu
              ><el-dropdown-item
                v-for="option in options.todo_state"
                :key="option.value"
                :command="Number(option.value)"
                >{{ option.label }}</el-dropdown-item
              ></el-dropdown-menu
            ></template
          ></el-dropdown
        ><el-button :disabled="!selected.length" @click="erase()">{{
          t('message.deleteSelected')
        }}</el-button></template
      >
    </div>
    <el-alert
      v-if="error || dictionaryError"
      :title="error || dictionaryError"
      type="error"
      :closable="false" /><el-table
      v-loading="busy"
      :data="rows"
      row-key="id"
      @selection-change="selected = $event"
      ><el-table-column v-if="!managed" type="selection" width="44" /><el-table-column
        :label="t('message.title')"
        min-width="220"
        ><template #default="{ row }"
          ><el-button link @click="open(row)">{{ row.title }}</el-button></template
        ></el-table-column
      ><el-table-column
        v-if="managed"
        prop="userName"
        :label="t('message.assignee')"
        width="190"
      /><el-table-column :label="t('common.status')" width="130"
        ><template #default="{ row }"
          ><el-tag :type="row.state === 3 ? 'success' : row.state === 4 ? 'info' : 'warning'">{{
            label('todo_state', row.state)
          }}</el-tag></template
        ></el-table-column
      ><el-table-column :label="t('message.priority')" width="100"
        ><template #default="{ row }">{{
          label('todo_priority_level', row.priority)
        }}</template></el-table-column
      ><el-table-column :label="t('message.dates.dueAt')" width="200"
        ><template #default="{ row }">{{
          formatInstant(row.dueAt, preferences.timeZone, locale)
        }}</template></el-table-column
      ><el-table-column v-if="!managed" :label="t('common.actions')" width="270"
        ><template #default="{ row }"
          ><template v-if="activeTask(row.state)"
            ><el-button link @click="edit(row)">{{ t('common.edit') }}</el-button
            ><el-dropdown @command="transition(row, $event)"
              ><el-button link>{{ t('message.changeState') }}</el-button
              ><template #dropdown
                ><el-dropdown-menu
                  ><el-dropdown-item
                    v-for="option in options.todo_state"
                    :key="option.value"
                    :command="Number(option.value)"
                    :disabled="Number(option.value) === row.state"
                    >{{ option.label }}</el-dropdown-item
                  ></el-dropdown-menu
                ></template
              ></el-dropdown
            ></template
          ><el-button link type="danger" @click="erase(row)">{{
            t('common.remove')
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
  <el-dialog
    v-model="dialog"
    :title="t(managed ? 'message.assignTask' : editingId ? 'common.edit' : 'message.createTask')"
    width="min(760px,95vw)"
    ><el-alert v-if="error" :title="error" type="error" :closable="false" /><el-form
      label-width="150px"
      ><el-form-item v-if="managed" :label="t('message.assignee')" required
        ><el-select v-model="form.userId" remote filterable :remote-method="findRecipients"
          ><el-option
            v-for="option in recipients"
            :key="option.value"
            :value="option.value"
            :label="option.label" /></el-select></el-form-item
      ><el-form-item :label="t('message.title')" required
        ><el-input v-model="form.title" maxlength="255" /></el-form-item
      ><el-form-item :label="t('message.type')"
        ><el-select v-model="form.type"
          ><el-option
            v-for="option in options.todo_type"
            :key="option.value"
            :value="Number(option.value)"
            :label="option.label" /></el-select></el-form-item
      ><el-form-item :label="t('message.priority')"
        ><el-select v-model="form.priority"
          ><el-option
            v-for="option in options.todo_priority_level"
            :key="option.value"
            :value="Number(option.value)"
            :label="option.label" /></el-select></el-form-item
      ><el-form-item :label="t('message.dates.dueAt')" required
        ><el-date-picker
          v-model="form.dueAt"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item
      ><el-form-item v-if="[2, 4].includes(form.type)" :label="t('message.content')" required
        ><el-input
          v-model="form.content"
          type="textarea"
          :rows="4"
          maxlength="30000" /></el-form-item
      ><el-form-item v-if="[3, 4].includes(form.type)" :label="t('message.url')" required
        ><el-input v-model="form.url" maxlength="512" /></el-form-item
      ><el-collapse
        ><el-collapse-item :title="t('message.source')" name="source"
          ><el-form-item
            v-for="field in sourceFields"
            :key="field"
            :label="t('message.sources.' + field)"
            ><el-input
              v-model="form.source[field]"
              :maxlength="
                field === 'url' ? 512 : 64
              " /></el-form-item></el-collapse-item></el-collapse></el-form
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
    width="min(760px,95vw)"
    @close="detail = undefined"
    ><template v-if="detail"
      ><p class="plain-content">{{ detail.content }}</p>
      <el-button v-if="detail.url" link @click="openLink(detail.url)">{{
        t('message.openLink')
      }}</el-button
      ><el-descriptions :column="1" border
        ><el-descriptions-item
          v-for="field in ['dueAt', 'completedAt', 'cancelledAt'] as const"
          :key="field"
          :label="t('message.dates.' + field)"
          >{{ formatInstant(detail[field], preferences.timeZone, locale) }}</el-descriptions-item
        ><el-descriptions-item v-if="detail.source?.system" :label="t('message.source')">{{
          detail.source.system
        }}</el-descriptions-item></el-descriptions
      ></template
    ></el-dialog
  >
</template>
