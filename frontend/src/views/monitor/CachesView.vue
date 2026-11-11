<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'
import { get, remove } from '@/api/http'
interface Cache {
  id: string
  name: string
  size: string
  hits: string
  misses: string
}
const { t, locale } = useI18n(),
  rows = ref<Cache[]>([]),
  busy = ref(false),
  error = ref('')
async function load() {
  busy.value = true
  try {
    rows.value = await get('/monitor/caches')
    error.value = ''
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
async function clear(row?: Cache) {
  try {
    await ElMessageBox.confirm(t('monitor.clearConfirm'), t('common.confirm'), { type: 'warning' })
    await remove('/monitor/caches' + (row ? '/' + encodeURIComponent(row.id) : ''))
    await load()
  } catch (e) {
    if (e instanceof Error) error.value = e.message
  }
}
watch(locale, load, { immediate: true })
</script>
<template>
  <el-card shadow="never"
    ><div class="toolbar">
      <el-button @click="load">{{ t('common.refresh') }}</el-button
      ><el-button v-permission="'monitor:cache:clear'" @click="clear()">{{
        t('monitor.clearAll')
      }}</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" /><el-table
      v-loading="busy"
      :data="rows"
      row-key="id"
      ><el-table-column prop="name" :label="t('common.name')" /><el-table-column
        prop="id"
        :label="t('monitor.cacheId')"
      /><el-table-column prop="size" :label="t('monitor.entries')"><template #default="{ row }">{{ row.size === '-1' ? t('common.unavailable') : row.size }}</template></el-table-column><el-table-column
        prop="hits"
        :label="t('monitor.hits')"
      /><el-table-column prop="misses" :label="t('monitor.misses')" /><el-table-column
        :label="t('common.actions')"
        ><template #default="{ row }"
          ><el-button v-permission="'monitor:cache:clear'" link @click="clear(row)">{{
            t('monitor.clear')
          }}</el-button></template
        ></el-table-column
      ></el-table
    ></el-card
  >
</template>
