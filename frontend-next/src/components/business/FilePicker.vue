<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { get, type PageResult } from '@/api/http'
import { useLatestRequest } from '@/composables/useLatestRequest'
import { type Asset, formatBytes } from '@/features/file/files'
import { loadLocaleModule } from '@/locales'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import EditorDialog from './EditorDialog.vue'
import DataTable from './DataTable.vue'
import FilterBar from './FilterBar.vue'
import ErrorNotice from './ErrorNotice.vue'
const open = defineModel<boolean>('open', { required: true })
const ids = defineModel<string[]>({ required: true })
const { t } = useI18n(), request = useLatestRequest(), rows = ref<Asset[]>([]), page = ref(1), total = ref(0), keyword = ref(''), draft = ref<string[]>([])
void loadLocaleModule('file')
const columns = computed(() => [{ key: 'select', title: t('common.actions'), width: 70 }, { key: 'name', title: t('common.name'), width: 240 }, { key: 'sizeBytes', title: t('file.size') }])
async function load() {
  const params = { page: page.value, size: 20, keyword: keyword.value }
  await request.run(signal => get<PageResult<Asset>>('/file/files', params, { signal }), result => { rows.value = result.items; total.value = Number(result.total) }, JSON.stringify(params))
}
function toggle(id: string, selected: boolean) { draft.value = selected ? [...new Set([...draft.value, id])] : draft.value.filter(value => value !== id) }
function save() { ids.value = [...draft.value]; open.value = false }
watch(open, value => { if (value) { draft.value = [...ids.value]; keyword.value = ''; page.value = 1; void load() } })
</script>
<template><EditorDialog v-model="open" :title="t('file.selectExisting')" :dirty="JSON.stringify(draft) !== JSON.stringify(ids)" :busy="request.loading.value" wide @save="save"><FilterBar :busy="request.loading.value" @search="page = 1; load()"><Input v-model="keyword" :placeholder="t('common.search')" :aria-label="t('common.search')" /><template #actions><Button variant="outline" @click="load">{{ t('common.refresh') }}</Button></template></FilterBar><ErrorNotice :message="request.error.value" /><DataTable :rows="rows" :columns="columns" :loading="request.loading.value" :page="page" :total="total" @update:page="page = $event; load()"><template #select="{ row }"><Checkbox :aria-label="t('common.selectRecord', { id: row.name })" :model-value="draft.includes(row.id)" @update:model-value="toggle(row.id, $event === true)" /></template><template #sizeBytes="{ row }">{{ formatBytes(row.sizeBytes) }}</template></DataTable><p class="mt-4 text-xs text-muted-foreground" aria-live="polite">{{ t('common.selectedCount', { count: draft.length }) }}</p></EditorDialog></template>
