<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { get, type PageResult } from '@/api/http'

/** Read-only effective view. Overrides and platform publication are managed in the next console. */
interface Revision { platform: string; tenant: string }
interface Parameter { key: string; name: string; value: string | null; origin: string; revision: Revision; overridePolicy: string }
interface Summary { key: string; name: string; mode: string }
interface Entry { code: string; parentCode: string | null; label: string; selectable: boolean; origin: string; sort: number }
interface Dictionary { key: string; name: string; mode: string; enabled: boolean; revision: Revision; entries: Entry[] }
const props = defineProps<{ kind: 'parameters' | 'dictionaries' }>()
const { t, locale } = useI18n()
const parameters = ref<Parameter[]>([]), summaries = ref<Summary[]>([]), selected = ref(''), dictionary = ref<Dictionary>(), loading = ref(false), error = ref(''), page = ref(1), total = ref(0)
let attempt = 0, detailAttempt = 0
async function load(next = 1) {
  const current = ++attempt
  detailAttempt++
  loading.value = true
  error.value = ''
  try {
    if (props.kind === 'parameters') {
      const result = await get<PageResult<Parameter>>('/config/effective-parameters', { page: next, size: 50 })
      if (current === attempt) { parameters.value = result.items; page.value = next; total.value = Number(result.total) }
    } else {
      const result = await get<PageResult<Summary>>('/config/effective-dictionaries', { page: next, size: 50 })
      if (current !== attempt) return
      summaries.value = result.items; page.value = next; total.value = Number(result.total)
      if (!result.items.some((item) => item.key === selected.value)) selected.value = result.items[0]?.key || ''
      await open()
    }
  } catch (cause) {
    if (current === attempt) error.value = (cause as Error).message
  } finally {
    if (current === attempt) loading.value = false
  }
}
async function open() {
  if (!selected.value) {
    dictionary.value = undefined
    return
  }
  const key = selected.value, current = ++detailAttempt
  const result = await get<Dictionary>('/config/effective-dictionaries/' + encodeURIComponent(key))
  if (current === detailAttempt && selected.value === key) dictionary.value = result
}
function value(row: Parameter) {
  return row.value === null ? t('config.effective.nullValue') : row.value === '' ? t('config.effective.emptyValue') : row.value
}
function origin(row: Parameter | Entry) {
  return row.origin && ['DEFAULT', 'PLATFORM', 'TENANT_OVERRIDE', 'TENANT_ONLY'].includes(row.origin)
    ? t('config.effective.origins.' + row.origin) : '—'
}
watch(locale, () => { void load(1) }, { immediate: true })
watch(selected, () => {
  if (props.kind === 'dictionaries') void open().catch((cause) => (error.value = (cause as Error).message))
})
</script>
<template>
  <el-alert :title="t('config.effective.readOnly')" type="info" :closable="false" show-icon />
  <el-alert v-if="error" :title="error" type="error" :closable="false" class="effective-error" />
  <template v-if="kind === 'parameters'"><el-table v-loading="loading" :data="parameters" row-key="key">
    <el-table-column prop="name" :label="t('config.effective.name')" min-width="160" />
    <el-table-column prop="key" :label="t('config.effective.key')" min-width="200" />
    <el-table-column :label="t('config.effective.value')" min-width="200"><template #default="{ row }">{{ value(row) }}</template></el-table-column>
    <el-table-column :label="t('config.effective.origin')" min-width="120"><template #default="{ row }">{{ origin(row) }}</template></el-table-column>
    <el-table-column :label="t('config.effective.revision')" min-width="140"><template #default="{ row }">{{ t('config.effective.revisionValue', row.revision) }}</template></el-table-column>
  </el-table>
  <el-pagination v-if="total > 50" :current-page="page" :page-size="50" :total="total" layout="prev, pager, next" @current-change="load" /></template>
  <template v-else>
    <el-select v-model="selected" :placeholder="t('config.selectType')" class="effective-select" filterable>
      <el-option v-for="item in summaries" :key="item.key" :label="item.name" :value="item.key" />
    </el-select>
    <el-pagination v-if="total > 50" :current-page="page" :page-size="50" :total="total" layout="prev, pager, next" @current-change="load" />
    <el-table v-loading="loading" :data="dictionary?.entries || []" row-key="code">
      <el-table-column prop="label" :label="t('config.effective.label')" min-width="160" />
      <el-table-column prop="code" :label="t('config.effective.code')" min-width="120" />
      <el-table-column prop="parentCode" :label="t('config.effective.parent')" min-width="120" />
      <el-table-column :label="t('config.effective.origin')" min-width="120"><template #default="{ row }">{{ origin(row) }}</template></el-table-column>
      <el-table-column :label="t('config.effective.selectable')" min-width="100"><template #default="{ row }">{{ row.selectable ? t('config.effective.yes') : t('config.effective.no') }}</template></el-table-column>
    </el-table>
  </template>
</template>
<style scoped>
.effective-error,
.effective-select {
  margin: 12px 0;
}
.effective-select {
  width: 300px;
}
</style>

