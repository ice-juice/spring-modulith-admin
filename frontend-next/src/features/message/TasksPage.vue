<script setup lang="ts">
import PermissionButton from '@/components/business/PermissionButton.vue'
import { safeLocalPath, safeHttpLink } from '@/lib/web-addresses'

import TableWorkspace from '@/components/business/TableWorkspace.vue'
import RowActions from '@/components/business/RowActions.vue'
const findRecipientsGate = useRequestGate()


import { useRequestGate } from '@/composables/useRequestGate'
const loadGate = useRequestGate()

import { computed } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import PageHeader from '@/components/business/PageHeader.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import DataTable from '@/components/business/DataTable.vue'
import EditorDialog from '@/components/business/EditorDialog.vue'
import EntitySelect from '@/components/business/EntitySelect.vue'
import StatusBadge from '@/components/business/StatusBadge.vue'

import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { confirmAction } from '@/lib/feedback'
import { useSubmission } from '@/composables/useSubmission'
import { useSubmissionPool } from '@/composables/useSubmissionPool'
import { useMessageDetail } from './useMessageDetail'
import OperationStatus from '@/components/business/OperationStatus.vue'
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
const formAction = useSubmission(), actions = useSubmissionPool(), rowAction = actions.last
const page = ref(1),
  total = ref(0),
  busy = ref(false),
  error = ref(''),
  dialog = ref(false),
  saving = formAction.busy,
  editingId = ref(''),
  detail = ref<Task>(),
  recipients = ref<Option[]>([]),
  form = reactive(taskForm(undefined, preferences.timeZone))
const dateFields = ['createdAt', 'updatedAt', 'dueAt', 'completedAt', 'cancelledAt'],
  sourceFields = ['system', 'messageId', 'requestId', 'url', 'senderKey', 'receiverKey'] as const
async function load() {
  const isCurrent = loadGate()
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
    const loaded: typeof recipients.value = await get<Option[]>('/message/todos/recipients', { keyword })
    if (!isCurrent()) return
    recipients.value = loaded
  } catch (e) {
    if (!isCurrent()) return
    error.value = (e as Error).message
  }
}
function edit(row?: Task) {
  if (saving.value || formAction.querying.value) return
  formAction.resetView(); formAction.bind('message.todos.' + (row?.id || (props.managed ? 'assign' : 'new')))
  Object.assign(form, taskForm(row, preferences.timeZone))
  if (!row) form.userId = null
  editingId.value = row?.id || ''
  dialog.value = true
  error.value = ''
  if (props.managed) void findRecipients()
}
async function refresh() { await load(); await messages.refresh() }
async function save() {
  const id = editingId.value, managed = props.managed, snapshot: typeof form = JSON.parse(JSON.stringify(form)), zone = preferences.timeZone
  const result = await formAction.run({ operation: 'todos.save', resource: 'message.todos.' + (id || (managed ? 'assign' : 'new')), protocol: 'LOCAL' }, () => {
    if (!snapshot.title.trim() || !snapshot.dueAt || (managed && !snapshot.userId)) { error.value = t('common.required'); return }
    const input = JSON.parse(JSON.stringify(taskInput(snapshot, zone)))
    return options => id ? put('/message/todos/' + id, input, options) : post('/message/todos' + (managed ? '/assign' : ''), input, options)
  })
  if (result.kind === 'success' || result.kind === 'completed') { dialog.value = false; await refresh() }
  else if (formAction.error.value) error.value = formAction.error.value
}
async function transition(row: Task, value: number) {
  const id = row.id, resource = 'message.todos.' + id, action = actions.get(resource)
  const result = await action.run({ operation: 'todos.state', resource, protocol: 'LOCAL' }, () => options => put('/message/todos/' + id + '/state', { state: value }, options))
  if (result.kind === 'success' || result.kind === 'completed') await refresh(); else error.value = action.error.value
}
async function batchState(value: number) {
  const ids = [...new Set(selected.value.map(row => row.id))].sort()
  if (!ids.length) return
  const resources = ids.map(id => 'message.todos.' + id), resource = resources[0]!, action = actions.get(resource)
  const result = await action.run({ operation: 'todos.batch-state', resource, resources, protocol: 'LOCAL' }, () => options => post('/message/todos/batch-state', { ids, state: value }, options))
  if (result.kind === 'success' || result.kind === 'completed') await refresh(); else error.value = action.error.value
}
async function erase(row?: Task) {
  const ids = [...new Set(row ? [row.id] : selected.value.map(item => item.id))].sort(), single = !!row
  if (!ids.length) return
  const resources = ids.map(id => 'message.todos.' + id), resource = resources[0]!, action = actions.get(resource)
  const result = await action.run({ operation: 'todos.delete', resource, resources, protocol: 'LOCAL' }, async () => {
    if (!await confirmAction(t('common.deleteConfirm'), t('common.confirm'), true)) return
    return options => single ? remove('/message/todos/' + ids[0], options) : post('/message/todos/batch-delete', { ids }, options)
  })
  if (result.kind === 'success' || result.kind === 'completed') await refresh(); else error.value = action.error.value
}
async function checkForm() { if (await formAction.check() === 'SUCCEEDED') { dialog.value = false; await refresh() }; error.value = formAction.error.value }
async function checkRow() { const action = rowAction.value; if (await action?.check() === 'SUCCEEDED') await refresh(); error.value = action?.error.value || '' }
const detailAction = useMessageDetail<Task>(actions, 'message.todos', value => { detail.value = value }, refresh, message => { error.value = message })
function open(row: Task) { return detailAction.open(row.id, '/message/todos/' + (props.managed ? 'managed/' : '') + encodeURIComponent(row.id), !props.managed && !row.readAt) }
function openLink(url: string) {
  if (safeLocalPath(url)) void router.push(url)
  else if (safeHttpLink(url)) window.open(url, '_blank', 'noopener,noreferrer')
}
function search() {
  page.value = 1
  void load()
}
watch([() => props.managed, locale], search, { immediate: true })
watch(
  () => route.query.focus,
  (id) => {
    if (typeof id === 'string') void detailAction.open(id, '/message/todos/' + (props.managed ? 'managed/' : '') + encodeURIComponent(id), false)
  },
  { immediate: true },
)
useMessageRefresh(load, ['todo'])

import { Textarea } from '@/components/ui/textarea'
import { DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem } from '@/components/ui/dropdown-menu'
import DateRangeField from '@/components/business/DateRangeField.vue'
import FilterBar from '@/components/business/FilterBar.vue'
const original = ref('')
watch(dialog, value => { if(value) original.value = JSON.stringify(form) })
const dirty = computed(() => JSON.stringify(form) !== original.value)
const detailOpen = computed({get:()=>!!detail.value,set:value=>{if(!value) detail.value=undefined}})
const columns = computed(() => [{key:'title',title:t('message.title'),width:240},...(props.managed ? [{key:'userName',title:t('message.assignee'),width:160}] : []),{key:'state',title:t('common.status')},{key:'priority',title:t('message.priority')},{key:'dueAt',title:t('message.dates.dueAt'),width:200},...(!props.managed ? [{key:'actions',title:t('common.actions'),width:270}] : [])])
const dateOptions = computed(() => dateFields.map(value=>({value,label:t('message.dates.'+value)})))

const actionBusy = computed(() => selected.value.some(row => actions.busy('message.todos.' + row.id)))
</script>
<template><TableWorkspace><template #tools>
  <PageHeader :title="t(managed ? 'message.taskManagement' : 'message.myTasks')"><Button v-if="!managed" @click="edit()">{{ t('message.createTask') }}</Button><PermissionButton v-else :permission="'message:todo:assign'" @click="edit()">{{ t('message.assignTask') }}</PermissionButton><template v-if="!managed"><DropdownMenu><DropdownMenuTrigger as-child><Button variant="outline" :disabled="actionBusy || !selected.length">{{ t('message.batchState') }}</Button></DropdownMenuTrigger><DropdownMenuContent><DropdownMenuItem v-for="option in options.todo_state" :key="option.value" @select="batchState(Number(option.value))">{{ option.label }}</DropdownMenuItem></DropdownMenuContent></DropdownMenu><Button variant="outline" class="text-destructive" :disabled="actionBusy || !selected.length" @click="erase()">{{ t('message.deleteSelected') }}</Button></template></PageHeader>
  <FilterBar :busy="busy" @search="search"><Input v-model="keyword" :placeholder="t('common.search')" :aria-label="t('common.search')" /><EntitySelect :model-value="state == null ? null : String(state)" :options="options.todo_state || []" :placeholder="t('common.status')" class="w-full sm:w-40" @update:model-value="state = $event == null ? undefined : Number($event)" /><EntitySelect :model-value="type == null ? null : String(type)" :options="options.todo_type || []" :placeholder="t('message.type')" class="w-full sm:w-44" @update:model-value="type = $event == null ? undefined : Number($event)" /><EntitySelect :model-value="dateField" :options="dateOptions" class="w-full sm:w-40" :placeholder="t('message.dates.dueAt')" @update:model-value="dateField = String($event || 'dueAt')" /><DateRangeField v-model="dates" /></FilterBar><ErrorNotice :message="error || dictionaryError" /><OperationStatus v-if="rowAction" :slow="rowAction.slow.value" :unresolved="rowAction.unresolved.value" :querying="rowAction.querying.value" queryable @check="checkRow" />
  </template><DataTable :rows="rows" :columns="columns" :loading="busy" :page="page" :total="total" :selectable="!managed" @select="selected = $event" @update:page="page = $event; load()"><template #title="{ row }"><Button variant="link" class="max-w-full truncate p-0" @click="open(row)">{{ row.title }}</Button></template><template #state="{ row }"><StatusBadge :label="label('todo_state', row.state)" :tone="row.state === 3 ? 'success' : row.state === 4 ? 'neutral' : 'warning'" /></template><template #priority="{ row }">{{ label('todo_priority_level', row.priority) }}</template><template #dueAt="{ row }">{{ formatInstant(row.dueAt, preferences.timeZone, locale) }}</template><template #actions="{ row }"><RowActions><template v-if="activeTask(row.state)"><Button variant="ghost" size="sm" @click="edit(row)">{{ t('common.edit') }}</Button><DropdownMenu><DropdownMenuTrigger as-child><Button variant="ghost" size="sm">{{ t('message.changeState') }}</Button></DropdownMenuTrigger><DropdownMenuContent><DropdownMenuItem v-for="option in options.todo_state" :key="option.value" :disabled="actions.busy('message.todos.' + row.id) || Number(option.value) === row.state" @select="transition(row, Number(option.value))">{{ option.label }}</DropdownMenuItem></DropdownMenuContent></DropdownMenu></template><Button :disabled="actions.busy('message.todos.' + row.id)" variant="ghost" size="sm" class="text-destructive" @click="erase(row)">{{ t('common.remove') }}</Button></RowActions></template></DataTable></TableWorkspace>
  <EditorDialog v-model="dialog" :title="t(managed ? 'message.assignTask' : editingId ? 'common.edit' : 'message.createTask')" :busy="saving" :submit-disabled="formAction.unresolved.value" :dirty="dirty" wide @save="save"><ErrorNotice :message="error" /><OperationStatus :slow="formAction.slow.value" :unresolved="formAction.unresolved.value" :querying="formAction.querying.value" queryable @check="checkForm" /><form @submit.prevent="save"><fieldset :disabled="saving || formAction.unresolved.value" class="grid gap-4 sm:grid-cols-2"><div v-if="managed" class="space-y-2 sm:col-span-2"><Label for="task-user">{{ t('message.assignee') }}</Label><EntitySelect :disabled="saving || formAction.unresolved.value" id="task-user" :model-value="form.userId" :options="recipients" remote :placeholder="t('message.assignee')" @search="findRecipients" @update:model-value="form.userId = typeof $event === 'string' ? $event : null" /></div><div class="space-y-2 sm:col-span-2"><Label for="task-title">{{ t('message.title') }}</Label><Input id="task-title" v-model="form.title" maxlength="255" required /></div><div class="space-y-2"><Label for="task-type">{{ t('message.type') }}</Label><EntitySelect :disabled="saving || formAction.unresolved.value" id="task-type" :model-value="String(form.type)" :options="options.todo_type || []" @update:model-value="form.type = Number($event || 1)" /></div><div class="space-y-2"><Label for="task-priority">{{ t('message.priority') }}</Label><EntitySelect :disabled="saving || formAction.unresolved.value" id="task-priority" :model-value="String(form.priority)" :options="options.todo_priority_level || []" @update:model-value="form.priority = Number($event || 1)" /></div><div class="space-y-2 sm:col-span-2"><Label for="task-due">{{ t('message.dates.dueAt') }}</Label><Input id="task-due" v-model="form.dueAt" type="datetime-local" required /><p class="text-xs text-muted-foreground">{{ preferences.timeZone }}</p></div><div v-if="[2, 4].includes(form.type)" class="space-y-2 sm:col-span-2"><Label for="task-content">{{ t('message.content') }}</Label><Textarea id="task-content" v-model="form.content" :rows="4" maxlength="30000" required /></div><div v-if="[3, 4].includes(form.type)" class="space-y-2 sm:col-span-2"><Label for="task-url">{{ t('message.url') }}</Label><Input id="task-url" v-model="form.url" maxlength="512" required /></div><details class="rounded-lg border p-3 sm:col-span-2"><summary class="cursor-pointer text-sm font-medium">{{ t('message.source') }}</summary><div class="mt-4 grid gap-4 sm:grid-cols-2"><div v-for="field in sourceFields" :key="field" class="space-y-2"><Label :for="'source-' + field">{{ t('message.sources.' + field) }}</Label><Input :id="'source-' + field" v-model="form.source[field]" :maxlength="field === 'url' ? 512 : 64" /></div></div></details><button type="submit" hidden tabindex="-1" aria-hidden="true">{{ t('common.save') }}</button></fieldset></form></EditorDialog>
  <EditorDialog v-model="detailOpen" :title="detail?.title || t('common.details')"><template v-if="detail"><p class="whitespace-pre-wrap break-words">{{ detail.content }}</p><Button v-if="detail.url" variant="link" class="my-3 px-0" @click="openLink(detail.url)">{{ t('message.openLink') }}</Button><dl class="mt-4 space-y-4 border-t pt-4"><div v-for="field in ['dueAt', 'completedAt', 'cancelledAt'] as const" :key="field"><dt class="text-xs text-muted-foreground">{{ t('message.dates.' + field) }}</dt><dd class="mt-1 text-sm">{{ formatInstant(detail[field], preferences.timeZone, locale) }}</dd></div><div v-if="detail.source?.system"><dt class="text-xs text-muted-foreground">{{ t('message.source') }}</dt><dd>{{ detail.source.system }}</dd></div></dl></template><template #footer><Button variant="outline" @click="detailOpen = false">{{ t('common.cancel') }}</Button></template></EditorDialog>
</template>
