<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { type UploadRequestOptions } from 'element-plus'
import { loadLocaleModule } from '@/locales'
import { get } from '@/api/http'
import { upload, download, formatBytes, type Asset } from '@/features/file/files'
import { useAuth } from '@/stores/auth'
const props = defineProps<{ modelValue: string[]; editable?: boolean }>(),
  emit = defineEmits<{ 'update:modelValue': [ids: string[]] }>()
const { t } = useI18n(),
  auth = useAuth(),
  assets = ref<Asset[]>([]),
  error = ref(''),
  busy = ref(false)
void loadLocaleModule('file')
watch(
  () => props.modelValue,
  async (ids, _old, cleanup) => {
    let active = true
    cleanup(() => {
      active = false
    })
    try {
      const result = await Promise.all(ids.map((id) => get<Asset>('/file/files/' + id)))
      if (active) {
        assets.value = result
        error.value = ''
      }
    } catch (e) {
      if (active) error.value = (e as Error).message
    }
  },
  { immediate: true, deep: true },
)
async function send(options: UploadRequestOptions) {
  busy.value = true
  error.value = ''
  try {
    const asset = await upload(options.file)
    emit('update:modelValue', [...props.modelValue, asset.id])
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
async function save(asset: Asset) {
  try {
    await download(asset.id, asset.name)
  } catch (e) {
    error.value = (e as Error).message
  }
}
</script>
<template>
  <div class="attachment-list">
    <el-alert v-if="error" type="error" :title="error" :closable="false" />
    <div v-for="asset in assets" :key="asset.id" class="attachment-row">
      <el-button link @click="save(asset)">{{ asset.name }}</el-button
      ><small>{{ formatBytes(asset.sizeBytes) }}</small
      ><el-button
        v-if="editable"
        text
        type="danger"
        @click="
          emit(
            'update:modelValue',
            modelValue.filter((id) => id !== asset.id),
          )
        "
        >{{ t('file.remove') }}</el-button
      >
    </div>
    <el-upload
      v-if="editable && auth.hasPermission('file:file:upload')"
      :http-request="send"
      :show-file-list="false"
      ><el-button :loading="busy" :disabled="busy || modelValue.length >= 20">{{
        t('file.upload')
      }}</el-button
      ><template #tip
        ><small>{{ t('file.uploadHint') }}</small></template
      ></el-upload
    >
  </div>
</template>
