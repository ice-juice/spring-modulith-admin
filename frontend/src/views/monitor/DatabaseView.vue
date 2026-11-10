<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { get, http } from '@/api/http'
import { saveBlob } from '@/features/file/files'
interface Table {
  name: string
  remarks: string
  columns: {
    name: string
    type: string
    nullable: boolean
    defaultValue: string | null
    remarks: string
    primaryKey: boolean
  }[]
  indexes: { name: string; unique: boolean; columns: string[] }[]
}
interface Schema {
  database: string
  product: string
  version: string
  tables: Table[]
}
const { t } = useI18n(),
  schema = ref<Schema>(),
  keyword = ref(''),
  selected = ref(''),
  busy = ref(false),
  exporting = ref(false),
  error = ref('')
const filtered = computed(
    () =>
      schema.value?.tables.filter((table) =>
        (table.name + ' ' + table.remarks).toLowerCase().includes(keyword.value.toLowerCase()),
      ) || [],
  ),
  table = computed(() => filtered.value.find((table) => table.name === selected.value))
watch(filtered, (rows) => {
  if (!rows.some((table) => table.name === selected.value)) selected.value = rows[0]?.name || ''
})
async function load() {
  busy.value = true
  try {
    schema.value = await get('/monitor/database')
    error.value = ''
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
async function download() {
  exporting.value = true
  try {
    saveBlob(
      (await http.get<Blob>('/monitor/database/markdown', { responseType: 'blob' })).data,
      'database-schema.md',
    )
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    exporting.value = false
  }
}
onMounted(load)
</script>
<template>
  <el-card shadow="never"
    ><div class="toolbar">
      <el-input v-model="keyword" :placeholder="t('monitor.searchTables')" clearable /><el-button
        :loading="busy"
        @click="load"
        >{{ t('common.refresh') }}</el-button
      ><el-button :loading="exporting" @click="download">{{ t('monitor.export') }}</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <p v-if="schema">{{ schema.database + ' · ' + schema.product + ' ' + schema.version }}</p>
    <div v-loading="busy" class="schema-layout">
      <aside class="schema-tables">
        <el-button
          v-for="item in filtered"
          :key="item.name"
          text
          :type="selected === item.name ? 'primary' : 'default'"
          @click="selected = item.name"
          >{{ item.name }}</el-button
        >
      </aside>
      <section v-if="table" class="schema-details">
        <h3>{{ table.name }}</h3>
        <p>{{ table.remarks }}</p>
        <el-table :data="table.columns"
          ><el-table-column
            prop="name"
            :label="t('monitor.columns.name')"
            min-width="170" /><el-table-column
            prop="type"
            :label="t('monitor.columns.type')"
            min-width="130" /><el-table-column :label="t('monitor.columns.nullable')" width="90"
            ><template #default="{ row }">{{
              t(row.nullable ? 'common.yes' : 'common.no')
            }}</template></el-table-column
          ><el-table-column
            prop="defaultValue"
            :label="t('monitor.columns.default')"
            min-width="120" /><el-table-column :label="t('monitor.columns.primary')" width="90"
            ><template #default="{ row }">{{
              t(row.primaryKey ? 'common.yes' : 'common.no')
            }}</template></el-table-column
          ><el-table-column prop="remarks" :label="t('monitor.columns.remarks')" min-width="180"
        /></el-table>
        <h4>{{ t('monitor.indexes') }}</h4>
        <el-table :data="table.indexes"
          ><el-table-column prop="name" :label="t('common.name')" /><el-table-column
            :label="t('monitor.columns.unique')"
            width="100"
            ><template #default="{ row }">{{
              t(row.unique ? 'common.yes' : 'common.no')
            }}</template></el-table-column
          ><el-table-column :label="t('monitor.columns.fields')"
            ><template #default="{ row }">{{ row.columns.join(', ') }}</template></el-table-column
          ></el-table
        >
      </section>
    </div></el-card
  >
</template>
