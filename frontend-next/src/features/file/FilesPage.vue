<script setup lang="ts">
import PermissionButton from '@/components/business/PermissionButton.vue'

import TableWorkspace from '@/components/business/TableWorkspace.vue'
import RowActions from '@/components/business/RowActions.vue'
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { get, remove, type PageResult } from '@/api/http'
import { usePreferences } from '@/stores/preferences'
import { formatInstant } from '@/lib/time'
import { useLatestRequest } from '@/composables/useLatestRequest'
import { useSubmissionPool } from '@/composables/useSubmissionPool'
import OperationStatus from '@/components/business/OperationStatus.vue'
import { confirmAction } from '@/lib/feedback'
import { download, formatBytes, type Asset } from './files'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import PageHeader from '@/components/business/PageHeader.vue'
import FilterBar from '@/components/business/FilterBar.vue'
import DataTable from '@/components/business/DataTable.vue'
import EntitySelect from '@/components/business/EntitySelect.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import EditorDialog from '@/components/business/EditorDialog.vue'
import UploadButton from '@/components/business/UploadButton.vue'
import AvatarImage from '@/components/AvatarImage.vue'
const { t, locale } = useI18n(), preferences = usePreferences(), request = useLatestRequest(), rows = ref<Asset[]>([]), keyword = ref(''), kind = ref(''), page = ref(1), total = ref(0), error = ref(''), detail = ref<Asset>(), detailOpen = ref(false)
const columns = computed(() => [{ key: 'name', title: t('common.name'), width: 260 }, { key: 'kind', title: t('file.kind') }, { key: 'sizeBytes', title: t('file.size') }, { key: 'uploader', title: t('file.uploader') }, { key: 'createdAt', title: t('common.createdAt'), width: 190 }, { key: 'actions', title: t('common.actions'), width: 220 }])
const kinds = computed(() => ['IMAGE', 'VIDEO', 'DOCUMENT'].map(value => ({ value, label: t('file.kinds.' + value) })))
const route = useRoute()
const deletions = useSubmissionPool(), deletion = deletions.last
watch(() => route.query.focus, async (id, _old, cleanup) => {
  let active = true; cleanup(() => { active = false })
  if (typeof id !== 'string') return
  try { const result = await get<Asset>('/file/files/' + encodeURIComponent(id)); if (active) { detail.value = result; detailOpen.value = true } }
  catch (cause) { if (active) error.value = (cause as Error).message }
}, { immediate: true })
async function load() { const params = { page: page.value, size: 20, keyword: keyword.value, kind: kind.value || undefined }; await request.run(signal => get<PageResult<Asset>>('/file/files', params, { signal }), result => { rows.value = result.items; total.value = Number(result.total) }, JSON.stringify([params, locale.value])) }
function refresh() { request.cancel(); void load() }
function search() { page.value = 1; void load() }
function show(row: Asset) { detail.value = row; detailOpen.value = true }
async function save(row: Asset) { try { await download(row.id, row.name) } catch (cause) { error.value = (cause as Error).message } }
async function erase(row: Asset) {
  const id = row.id, resource = 'file.files.' + id, action = deletions.get(resource)
  const result = await action.run({ operation: 'file.delete', resource, protocol: 'LOCAL' }, async () => {
    if (!await confirmAction(t('common.deleteConfirm'), t('common.confirm'), true)) return
    return options => remove('/file/files/' + id, options)
  })
  if (result.kind === 'success' || result.kind === 'completed') { request.cancel(); await load() }
  else error.value = action.error.value
}
async function checkDeletion() { const state = await deletion.value?.check(); error.value = deletion.value?.error.value || ''; if (state === 'SUCCEEDED') { request.cancel(); await load() } }
watch(locale, search, { immediate: true })

</script>
<template><TableWorkspace><template #tools><PageHeader :title="t('file.files')"><UploadButton v-permission="'file:file:upload'" @uploaded="refresh" @error="error = $event" /></PageHeader><FilterBar :busy="request.loading.value" @search="search"><Input v-model="keyword" :placeholder="t('common.search')" :aria-label="t('common.search')" /><EntitySelect :model-value="kind" :options="kinds" :placeholder="t('file.kind')" class="w-full sm:w-44" @update:model-value="kind = String($event || '')" /></FilterBar><ErrorNotice :message="request.error.value || error" /><OperationStatus v-if="deletion" :slow="deletion.slow.value" :unresolved="deletion.unresolved.value" :querying="deletion.querying.value" queryable @check="checkDeletion" /></template><DataTable :rows="rows" :columns="columns" :loading="request.loading.value" :page="page" :total="total" @update:page="page = $event; load()"><template #name="{ row }"><button class="flex max-w-full items-center gap-3 text-left text-primary hover:underline" @click="show(row)"><AvatarImage v-if="row.previewable" :file-id="row.id" :size="36" shape="square" :alt="row.name" aria-hidden="true" /><span class="truncate">{{ row.name }}</span></button></template><template #kind="{ row }">{{ t('file.kinds.' + row.kind) }}</template><template #sizeBytes="{ row }">{{ formatBytes(row.sizeBytes) }}</template><template #createdAt="{ row }">{{ formatInstant(row.createdAt, preferences.timeZone, locale) }}</template><template #actions="{ row }"><RowActions><Button variant="ghost" size="sm" @click="show(row)">{{ t('common.details') }}</Button><Button variant="ghost" size="sm" @click="save(row)">{{ t('file.download') }}</Button><PermissionButton :disabled="deletions.busy('file.files.' + row.id)" :permission="'file:file:delete'" variant="ghost" size="sm" class="text-destructive" @click="erase(row)">{{ t('common.remove') }}</PermissionButton></RowActions></template></DataTable></TableWorkspace><EditorDialog v-model="detailOpen" :title="detail?.name || t('common.details')"><div v-if="detail" class="space-y-5"><AvatarImage v-if="detail.previewable" :file-id="detail.id" :size="180" shape="square" fit="contain" :alt="detail.name" /><dl class="space-y-3"><div v-for="entry in [{ label: t('file.contentType'), value: detail.contentType }, { label: t('file.size'), value: formatBytes(detail.sizeBytes) }, { label: t('file.checksum'), value: detail.checksum }, { label: t('common.createdAt'), value: formatInstant(detail.createdAt, preferences.timeZone, locale) }]" :key="entry.label"><dt class="text-xs text-muted-foreground">{{ entry.label }}</dt><dd class="mt-1 break-all text-sm">{{ entry.value }}</dd></div></dl></div><template #footer><Button variant="outline" @click="detailOpen = false">{{ t('common.cancel') }}</Button><Button v-if="detail" @click="save(detail)">{{ t('file.download') }}</Button></template></EditorDialog></template>
