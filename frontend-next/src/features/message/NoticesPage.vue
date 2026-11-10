<script setup lang="ts">
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
import { notify, confirmOrCancel } from '@/lib/feedback'
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
  Object.assign(form, noticeInput(row))
  editingId.value = row?.id || ''
  error.value = ''
  dialog.value = true
  await loadAudience()
}
async function save() {
  if (saving.value) return
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
    notify(t('common.saved'))
    await load()
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    saving.value = false
  }
}
async function erase(row: Notice) {
  try {
    await confirmOrCancel(t('message.recycleConfirm'), t('common.confirm'), {
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
    await confirmOrCancel(
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
</script>
<template>
  <PageHeader :title="t('message.notices')"><div class="mr-3 flex items-center gap-2"><Switch id="notice-trash" v-model="trash" /><Label for="notice-trash">{{ t('message.recycleBin') }}</Label></div><Button v-if="!trash" v-permission="'message:notice:create'" @click="edit()">{{ t('message.publish') }}</Button><Button v-if="auth.hasPermission(trash ? 'message:notice:restore' : 'message:notice:delete')" variant="outline" :disabled="!selected.length" @click="batch">{{ t(trash ? 'message.restoreSelected' : 'message.recycleSelected') }}</Button></PageHeader>
  <FilterBar :busy="busy" @search="search"><Input v-model="keyword" :placeholder="t('common.search')" :aria-label="t('common.search')" /><EntitySelect :model-value="type" :options="dictionaries.sys_notice_type || []" :placeholder="t('message.type')" class="w-full sm:w-40" @update:model-value="type = String($event || '')" /><EntitySelect :model-value="priority == null ? null : String(priority)" :options="dictionaries.sys_notice_priority_level || []" :placeholder="t('message.priority')" class="w-full sm:w-40" @update:model-value="priority = $event == null ? undefined : Number($event)" /><EntitySelect :model-value="audienceType" :options="audienceOptions" :placeholder="t('message.audience')" class="w-full sm:w-48" @update:model-value="audienceType = String($event || '')" /></FilterBar><ErrorNotice :message="error || dictionaryError" />
  <DataTable :rows="rows" :columns="columns" :loading="busy" :page="page" :total="total" selectable @select="selected = $event" @update:page="page = $event; load()"><template #title="{ row }"><button v-if="!trash" class="max-w-full truncate text-left text-primary hover:underline" @click="detail = row">{{ row.title }}</button><span v-else>{{ row.title }}</span></template><template #type="{ row }">{{ label('sys_notice_type', row.type) }}</template><template #priority="{ row }"><StatusBadge :label="label('sys_notice_priority_level', row.priority)" :tone="row.priority === 3 ? 'danger' : 'neutral'" /></template><template #audienceType="{ row }">{{ t('message.audiences.' + row.audienceType) }}</template><template #readStats="{ row }"><Button v-if="!trash" variant="link" size="sm" class="p-0" @click="openReaders(row)">{{ t('message.readRatio', { read: row.readCount, total: row.recipientCount }) }}</Button><span v-else>{{ t('message.readRatio', { read: row.readCount, total: row.recipientCount }) }}</span></template><template #createdAt="{ row }">{{ formatInstant(row.createdAt, preferences.timeZone, locale) }}</template><template #actions="{ row }"><div class="flex gap-1"><template v-if="!trash"><Button v-permission="'message:notice:update'" variant="ghost" size="sm" @click="edit(row)">{{ t('common.edit') }}</Button><Button v-permission="'message:notice:delete'" variant="ghost" size="sm" class="text-destructive" @click="erase(row)">{{ t('message.recycle') }}</Button></template><Button v-else v-permission="'message:notice:restore'" variant="ghost" size="sm" @click="restore(row)">{{ t('message.restore') }}</Button></div></template></DataTable>
  <EditorDialog v-model="dialog" :title="t(editingId ? 'message.editNotice' : 'message.publish')" :busy="saving" :dirty="dirty" wide @save="save"><ErrorNotice :message="error" /><form class="grid gap-4 sm:grid-cols-2" @submit.prevent="save"><div class="space-y-2 sm:col-span-2"><Label for="notice-title">{{ t('message.title') }}</Label><Input id="notice-title" v-model="form.title" maxlength="255" required /></div><div class="space-y-2"><Label for="notice-type">{{ t('message.type') }}</Label><EntitySelect id="notice-type" :model-value="form.type" :options="dictionaries.sys_notice_type || []" @update:model-value="form.type = String($event || '1')" /></div><div class="space-y-2"><Label for="notice-priority">{{ t('message.priority') }}</Label><EntitySelect id="notice-priority" :model-value="String(form.priority)" :options="dictionaries.sys_notice_priority_level || []" @update:model-value="form.priority = Number($event || 1)" /></div><div class="space-y-2 sm:col-span-2"><Label for="notice-audience">{{ t('message.audience') }}</Label><EntitySelect id="notice-audience" :model-value="form.audienceType" :options="audienceOptions" @update:model-value="form.audienceType = String($event || 'ALL'); changeAudience()" /></div><div v-if="form.audienceType !== 'ALL'" class="space-y-2 sm:col-span-2"><Label for="notice-recipients">{{ t('message.recipients') }}</Label><EntitySelect id="notice-recipients" :model-value="form.audienceIds" :options="audience" multiple remote @search="loadAudience" @update:model-value="form.audienceIds = Array.isArray($event) ? $event : []" /></div><div class="space-y-2 sm:col-span-2"><Label>{{ t('message.content') }}</Label><RichEditor v-model="form.content" /></div><div class="space-y-2 sm:col-span-2"><Label>{{ t('message.attachments') }}</Label><AttachmentList v-model="form.fileIds" editable /></div><button type="submit" hidden tabindex="-1" aria-hidden="true">{{ t('common.save') }}</button></form></EditorDialog>
  <EditorDialog v-model="detailOpen" :title="detail?.title || t('common.details')" wide><template v-if="detail"><SafeHtml :html="detail.content" /><AttachmentList class="mt-5" :model-value="detail.fileIds" /></template><template #footer><Button variant="outline" @click="detailOpen = false">{{ t('common.cancel') }}</Button></template></EditorDialog>
  <EditorDialog v-model="readersOpen" :title="t('message.readers')" wide><DataTable :rows="readers.map(reader => ({ ...reader, id: reader.userId }))" :columns="readerColumns" :page="readerPage" :total="readerTotal" @update:page="readerPage = $event; loadReaders()"><template #readAt="{ row }">{{ row.readAt ? formatInstant(row.readAt, preferences.timeZone, locale) : t('message.unread') }}</template></DataTable><template #footer><Button variant="outline" @click="readersOpen = false">{{ t('common.cancel') }}</Button></template></EditorDialog>
</template>
