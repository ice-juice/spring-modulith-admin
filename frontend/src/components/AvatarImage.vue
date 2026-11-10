<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { User } from '@element-plus/icons-vue'
import { content } from '@/features/file/files'
const props = defineProps<{
  fileId?: string | null
  size?: number
  shape?: 'circle' | 'square'
  fit?: 'contain' | 'cover'
  alt?: string
}>()
const { t } = useI18n(),
  url = ref('')
watch(
  () => props.fileId,
  async (id, _old, cleanup) => {
    let active = true,
      objectUrl = ''
    url.value = ''
    cleanup(() => {
      active = false
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    })
    if (!id) return
    try {
      const blob = await content(id, true)
      if (active) {
        objectUrl = URL.createObjectURL(blob)
        url.value = objectUrl
      }
    } catch {
      /* An unavailable avatar falls back to the account icon. */
    }
  },
  { immediate: true },
)
</script>
<template>
  <el-avatar
    :size="size || 36"
    :shape="shape || 'circle'"
    :fit="fit || 'cover'"
    :src="url"
    :alt="alt || t('common.profile')"
    ><el-icon><User /></el-icon
  ></el-avatar>
</template>
