<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessageBox, type UploadRequestOptions } from 'element-plus'
import { get, remove, type PageResult } from '@/api/http'
import { usePreferences } from '@/stores/preferences'
import { formatInstant } from '@/lib/time'
import { upload, download, formatBytes, type Asset } from '@/features/file/files'
import AvatarImage from '@/components/AvatarImage.vue'
const { t, locale } = useI18n(),
  preferences = usePreferences()
const rows = ref<Asset[]>([]),
  keyword = ref(''),
  kind = ref(''),
  page = ref(1),
  total = ref(0),
  busy = ref(false),
  error = ref(''),
  detail = ref<Asset>()
async function load() {
  busy.value = true
  error.value = ''
  try {
    const result = await get<PageResult<Asset>>('/file/files', {
      page: page.value,
      size: 20,
      keyword: keyword.value,
      kind: kind.value || undefined,
    })
    rows.value = result.items
    total.value = Number(result.total)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
async function send(options: UploadRequestOptions) {
  try {
    await upload(options.file)
    await load()
  } catch (e) {
    error.value = (e as Error).message
  }
}
async function erase(row: Asset) {
  try {
    await ElMessageBox.confirm(t('common.deleteConfirm'), t('common.confirm'), { type: 'warning' })
    await remove('/file/files/' + row.id)
    await load()
  } catch (e) {
    if (e instanceof Error) error.value = e.message
  }
}
async function save(row: Asset) {
  try {
    await download(row.id, row.name)
  } catch (e) {
    error.value = (e as Error).message
  }
}
function search() {
  page.value = 1
  void load()
}
onMounted(load)
</script>
<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input
        v-model="keyword"
        :placeholder="t('common.search')"
        clearable
        @keyup.enter="search"
      /><el-select v-model="kind" clearable :placeholder="t('file.kind')"
        ><el-option
          v-for="value in ['IMAGE', 'VIDEO', 'DOCUMENT']"
          :key="value"
          :value="value"
          :label="t('file.kinds.' + value)" /></el-select
      ><el-button @click="search">{{ t('common.search') }}</el-button
      ><el-upload
        v-permission="'file:file:upload'"
        :show-file-list="false"
        :http-request="send"
        multiple
        ><el-button type="primary">{{ t('file.upload') }}</el-button></el-upload
      >
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table v-loading="busy" :data="rows" row-key="id"
      ><el-table-column :label="t('file.name')" min-width="240"
        ><template #default="{ row }"
          ><el-button link @click="detail = row">{{ row.name }}</el-button></template
        ></el-table-column
      ><el-table-column :label="t('file.kind')" width="130"
        ><template #default="{ row }">{{ t('file.kinds.' + row.kind) }}</template></el-table-column
      ><el-table-column :label="t('file.size')" width="130"
        ><template #default="{ row }">{{ formatBytes(row.sizeBytes) }}</template></el-table-column
      ><el-table-column prop="uploader" :label="t('file.uploader')" width="150" /><el-table-column
        :label="t('common.createdAt')"
        width="200"
        ><template #default="{ row }">{{
          formatInstant(row.createdAt, preferences.timeZone, locale)
        }}</template></el-table-column
      ><el-table-column :label="t('common.actions')" width="210"
        ><template #default="{ row }"
          ><el-button link @click="save(row)">{{ t('file.download') }}</el-button
          ><el-button v-permission="'file:file:delete'" link type="danger" @click="erase(row)">{{
            t('common.remove')
          }}</el-button></template
        ></el-table-column
      ></el-table
    >
    <el-pagination
      v-model:current-page="page"
      :total="total"
      :page-size="20"
      layout="total, prev, pager, next"
      @current-change="load"
    />
  </el-card>
  <el-dialog :model-value="!!detail" :title="detail?.name" width="680px" @close="detail = undefined"
    ><template v-if="detail"
      ><div v-if="detail.previewable" class="file-preview">
        <AvatarImage
          :file-id="detail.id"
          :size="280"
          shape="square"
          fit="contain"
          :alt="detail.name"
        />
      </div>
      <el-descriptions :column="1" border
        ><el-descriptions-item :label="t('file.size')">{{
          formatBytes(detail.sizeBytes)
        }}</el-descriptions-item
        ><el-descriptions-item :label="t('file.contentType')">{{
          detail.contentType
        }}</el-descriptions-item
        ><el-descriptions-item :label="t('file.checksum')"
          ><code class="break-all">{{ detail.checksum }}</code></el-descriptions-item
        ></el-descriptions
      ></template
    ></el-dialog
  >
</template>
