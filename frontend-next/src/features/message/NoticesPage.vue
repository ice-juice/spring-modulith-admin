<script setup lang="ts">
import PermissionButton from '@/components/business/PermissionButton.vue'

import TableWorkspace from '@/components/business/TableWorkspace.vue'
import RowActions from '@/components/business/RowActions.vue'
const loadAudienceGate = useRequestGate()
const loadReadersGate = useRequestGate()

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
import { notify, confirmAction } from '@/lib/feedback'
import { useSubmission } from '@/composables/useSubmission'
import { useSubmissionPool } from '@/composables/useSubmissionPool'
import OperationStatus from '@/components/business/OperationStatus.vue'
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
const formAction = useSubmission(), actions = useSubmissionPool(), rowAction = actions.last
const page = ref(1),
  total = ref(0),
  busy = ref(false),
  error = ref(''),
  saving = formAction.busy,
  dialog = ref(false),
  editingId = ref(''),
  detail = ref<Notice>()
const uploadContext = ref(''), uploadBusy = ref(false)
const form = reactive(noticeInput()),
  audience = ref<Option[]>([]),
  readers = ref<{ userId: string; name: string; readAt: string | null }[]>([]),
  readerPage = ref(1),
  readerTotal = ref(0),
  readerId = ref('')
const audienceTypes = ['ALL', 'ROLE', 'DEPARTMENT', 'POST', 'USER']
async function load() {
  const isCurrent = loadGate()
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
async function loadAudience(keyword = '') {
  const isCurrent = loadAudienceGate()
  try {
    const loaded: typeof audience.value = form.audienceType === 'ALL'
        ? []
        : await get<Option[]>('/message/notices/audience', { type: form.audienceType, keyword })
    if (!isCurrent()) return
    audience.value = loaded
  } catch (e) {
    if (!isCurrent()) return
    error.value = (e as Error).message
  }
}
function changeAudience() {
  form.audienceIds = []
  void loadAudience()
}
async function edit(row?: Notice) {
  if (formAction.busy.value || formAction.querying.value) return
  formAction.resetView(); formAction.bind('message.notices.manage.' + (row?.id || 'new'))
  Object.assign(form, noticeInput(row))
  editingId.value = row?.id || ''
  uploadContext.value = 'file.notice.' + (row?.id || 'new') + '.' + crypto.randomUUID()
  error.value = ''
  dialog.value = true
  await loadAudience()
}
async function save() {
  if (saving.value || uploadBusy.value || formAction.querying.value) return
  error.value = ''
  const id = editingId.value, input: typeof form = JSON.parse(JSON.stringify(form))
  const result = await formAction.run({ operation: 'notices.save', resource: 'message.notices.manage.' + (id || 'new'), protocol: 'LOCAL' }, () => {
    if (!input.title.trim() || !input.content || (input.audienceType !== 'ALL' && !input.audienceIds.length)) { error.value = t('common.required'); return }
    return options => id ? put('/message/notices/' + id, input, options) : post('/message/notices', input, options)
  })
  if (result.kind === 'success' || result.kind === 'completed') { dialog.value = false; notify(t('common.saved')); await load() }
  else if (formAction.error.value) error.value = formAction.error.value
}
async function erase(row: Notice) {
  const id = row.id, resource = 'message.notices.manage.' + id, action = actions.get(resource)
  const result = await action.run({ operation: 'notices.delete', resource, protocol: 'LOCAL' }, async () => {
    if (!await confirmAction(t('message.recycleConfirm'), t('common.confirm'), true)) return
    return options => remove('/message/notices/' + id, options)
  })
  if (result.kind === 'success' || result.kind === 'completed') await load(); else error.value = action.error.value
}
async function restore(row: Notice) {
  const id = row.id, resource = 'message.notices.manage.' + id, action = actions.get(resource)
  const result = await action.run({ operation: 'notices.restore', resource, protocol: 'LOCAL' }, () => options => put('/message/notices/' + id + '/restore', undefined, options))
  if (result.kind === 'success' || result.kind === 'completed') await load(); else error.value = action.error.value
}
async function batch() {
  const ids = [...new Set(selected.value.map(row => row.id))].sort(), restore = trash.value
  if (!ids.length) return
  const resources = ids.map(id => 'message.notices.manage.' + id), resource = resources[0]!, action = actions.get(resource)
  const result = await action.run({ operation: restore ? 'notices.batch-restore' : 'notices.batch-delete', resource, resources, protocol: 'LOCAL' }, async () => {
    if (!await confirmAction(t(restore ? 'message.restoreConfirm' : 'message.recycleConfirm'), t('common.confirm'), true)) return
    return options => post('/message/notices/' + (restore ? 'batch-restore' : 'batch-delete'), { ids }, options)
  })
  if (result.kind === 'success' || result.kind === 'completed') await load(); else error.value = action.error.value
}
async function checkForm() { if (await formAction.check() === 'SUCCEEDED') { dialog.value = false; await load() }; error.value = formAction.error.value }
async function checkRow() { if (await rowAction.value?.check() === 'SUCCEEDED') await load(); error.value = rowAction.value?.error.value || '' }
async function loadReaders() {
  const isCurrent = loadReadersGate()
  try {
    const result = await get<PageResult<{ userId: string; name: string; readAt: string | null }>>(
      '/message/notices/' + readerId.value + '/readers',
      { page: readerPage.value, size: 20 },
    )
    if (!isCurrent()) return
    readers.value = result.items
    readerTotal.value = Number(result.total)
  } catch (e) {
    if (!isCurrent()) return
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

import { Switch } from '@/components/ui/switch'
import FilterBar from '@/components/business/FilterBar.vue'
import SafeHtml from '@/components/business/SafeHtml.vue'
const original = ref('')
watch(dialog, value => { if(value) original.value = JSON.stringify(form) })
const dirty = computed(() => JSON.stringify(form) !== original.value)
const detailOpen = computed({get:()=>!!detail.value,set:value=>{if(!value) detail.value=undefined}})
const readersOpen = computed({get:()=>!!readerId.value,set:value=>{if(!value) readerId.value=''}})
const columns = computed(() => [{key:'title',title:t('message.title'),width:240},{key:'type',title:t('message.type')},{key:'priority',title:t('message.priority')},{key:'audienceType',title:t('message.audience'),width:160},{key:'readStats',title:t('message.readStats'),width:160},{key:'createdAt',title:t('common.createdAt'),width:190},{key:'actions',title:t('common.actions'),width:180}])
const readerColumns = computed(() => [{key:'name',title:t('common.name')},{key:'readAt',title:t('message.readAt'),width:200}])
const audienceOptions = computed(() => audienceTypes.map(value=>({value,label:t('message.audiences.'+value)})))

const actionBusy = computed(() => selected.value.some(row => actions.busy('message.notices.manage.' + row.id)))
</script>
<template><TableWorkspace><template #tools>
  <PageHeader :title="t('message.notices')"><div class="mr-3 flex items-center gap-2"><Switch id="notice-trash" v-model="trash" /><Label for="notice-trash">{{ t('message.recycleBin') }}</Label></div><PermissionButton v-if="!trash" :permission="'message:notice:create'" @click="edit()">{{ t('message.publish') }}</PermissionButton><Button v-if="auth.hasPermission(trash ? 'message:notice:restore' : 'message:notice:delete')" variant="outline" :disabled="actionBusy || !selected.length" @click="batch">{{ t(trash ? 'message.restoreSelected' : 'message.recycleSelected') }}</Button></PageHeader>
  <FilterBar :busy="busy" @search="search"><Input v-model="keyword" :placeholder="t('common.search')" :aria-label="t('common.search')" /><EntitySelect :model-value="type" :options="dictionaries.sys_notice_type || []" :placeholder="t('message.type')" class="w-full sm:w-40" @update:model-value="type = String($event || '')" /><EntitySelect :model-value="priority == null ? null : String(priority)" :options="dictionaries.sys_notice_priority_level || []" :placeholder="t('message.priority')" class="w-full sm:w-40" @update:model-value="priority = $event == null ? undefined : Number($event)" /><EntitySelect :model-value="audienceType" :options="audienceOptions" :placeholder="t('message.audience')" class="w-full sm:w-48" @update:model-value="audienceType = String($event || '')" /></FilterBar><ErrorNotice :message="error || dictionaryError" /><OperationStatus v-if="rowAction" :slow="rowAction.slow.value" :unresolved="rowAction.unresolved.value" :querying="rowAction.querying.value" queryable @check="checkRow" />
  </template><DataTable :rows="rows" :columns="columns" :loading="busy" :page="page" :total="total" selectable @select="selected = $event" @update:page="page = $event; load()"><template #title="{ row }"><button v-if="!trash" class="max-w-full truncate text-left text-primary hover:underline" @click="detail = row">{{ row.title }}</button><span v-else>{{ row.title }}</span></template><template #type="{ row }">{{ label('sys_notice_type', row.type) }}</template><template #priority="{ row }"><StatusBadge :label="label('sys_notice_priority_level', row.priority)" :tone="row.priority === 3 ? 'danger' : 'neutral'" /></template><template #audienceType="{ row }">{{ t('message.audiences.' + row.audienceType) }}</template><template #readStats="{ row }"><Button v-if="!trash" variant="link" size="sm" class="p-0" @click="openReaders(row)">{{ t('message.readRatio', { read: row.readCount, total: row.recipientCount }) }}</Button><span v-else>{{ t('message.readRatio', { read: row.readCount, total: row.recipientCount }) }}</span></template><template #createdAt="{ row }">{{ formatInstant(row.createdAt, preferences.timeZone, locale) }}</template><template #actions="{ row }"><RowActions><template v-if="!trash"><PermissionButton :permission="'message:notice:update'" variant="ghost" size="sm" @click="edit(row)">{{ t('common.edit') }}</PermissionButton><PermissionButton :disabled="actions.busy('message.notices.manage.' + row.id)" :permission="'message:notice:delete'" variant="ghost" size="sm" class="text-destructive" @click="erase(row)">{{ t('message.recycle') }}</PermissionButton></template><PermissionButton :disabled="actions.busy('message.notices.manage.' + row.id)" v-else :permission="'message:notice:restore'" variant="ghost" size="sm" @click="restore(row)">{{ t('message.restore') }}</PermissionButton></RowActions></template></DataTable></TableWorkspace>
  <EditorDialog v-model="dialog" :title="t(editingId ? 'message.editNotice' : 'message.publish')" :busy="saving" :submit-disabled="formAction.unresolved.value || uploadBusy" :dirty="dirty" wide @save="save"><ErrorNotice :message="error" /><OperationStatus :slow="formAction.slow.value" :unresolved="formAction.unresolved.value" :querying="formAction.querying.value" queryable @check="checkForm" /><form @submit.prevent="save"><fieldset :disabled="saving || formAction.unresolved.value" class="grid gap-4 sm:grid-cols-2"><div class="space-y-2 sm:col-span-2"><Label for="notice-title">{{ t('message.title') }}</Label><Input id="notice-title" v-model="form.title" maxlength="255" required /></div><div class="space-y-2"><Label for="notice-type">{{ t('message.type') }}</Label><EntitySelect :disabled="saving || formAction.unresolved.value" id="notice-type" :model-value="form.type" :options="dictionaries.sys_notice_type || []" @update:model-value="form.type = String($event || '1')" /></div><div class="space-y-2"><Label for="notice-priority">{{ t('message.priority') }}</Label><EntitySelect :disabled="saving || formAction.unresolved.value" id="notice-priority" :model-value="String(form.priority)" :options="dictionaries.sys_notice_priority_level || []" @update:model-value="form.priority = Number($event || 1)" /></div><div class="space-y-2 sm:col-span-2"><Label for="notice-audience">{{ t('message.audience') }}</Label><EntitySelect :disabled="saving || formAction.unresolved.value" id="notice-audience" :model-value="form.audienceType" :options="audienceOptions" @update:model-value="form.audienceType = String($event || 'ALL'); changeAudience()" /></div><div v-if="form.audienceType !== 'ALL'" class="space-y-2 sm:col-span-2"><Label for="notice-recipients">{{ t('message.recipients') }}</Label><EntitySelect :disabled="saving || formAction.unresolved.value" id="notice-recipients" :model-value="form.audienceIds" :options="audience" multiple remote @search="loadAudience" @update:model-value="form.audienceIds = Array.isArray($event) ? $event : []" /></div><div class="space-y-2 sm:col-span-2"><Label>{{ t('message.content') }}</Label><RichEditor :disabled="saving || formAction.unresolved.value" v-model="form.content" /></div><div class="space-y-2 sm:col-span-2"><Label>{{ t('message.attachments') }}</Label><AttachmentList :key="uploadContext" :upload-scope="uploadContext" @busy="uploadBusy = $event" :disabled="saving || formAction.unresolved.value" v-model="form.fileIds" editable /></div><button type="submit" hidden tabindex="-1" aria-hidden="true">{{ t('common.save') }}</button></fieldset></form></EditorDialog>
  <EditorDialog v-model="detailOpen" :title="detail?.title || t('common.details')" wide><template v-if="detail"><SafeHtml :html="detail.content" /><AttachmentList class="mt-5" :model-value="detail.fileIds" /></template><template #footer><Button variant="outline" @click="detailOpen = false">{{ t('common.cancel') }}</Button></template></EditorDialog>
  <EditorDialog v-model="readersOpen" :title="t('message.readers')" wide><DataTable :rows="readers.map(reader => ({ ...reader, id: reader.userId }))" :columns="readerColumns" :page="readerPage" :total="readerTotal" @update:page="readerPage = $event; loadReaders()"><template #readAt="{ row }">{{ row.readAt ? formatInstant(row.readAt, preferences.timeZone, locale) : t('message.unread') }}</template></DataTable><template #footer><Button variant="outline" @click="readersOpen = false">{{ t('common.cancel') }}</Button></template></EditorDialog>
</template>
