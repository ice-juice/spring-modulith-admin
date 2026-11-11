<script setup lang="ts">
import { onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuth } from '@/stores/auth'
import { upload } from '@/features/file/files'
import { Button } from '@/components/ui/button'
import { validBrandUpload } from './brands'
const props = defineProps<{ label: string; disabled?: boolean }>()
const emit = defineEmits<{ uploaded: [id: string]; busy: [value: boolean]; error: [message: string] }>()
const input = ref<HTMLInputElement>(), busy = ref(false), auth = useAuth(), { t } = useI18n()
let active = true
onUnmounted(() => { active = false })
async function send(event: Event) {
  const element = event.target as HTMLInputElement, file = element.files?.[0]
  if (!file || props.disabled || busy.value || !auth.hasPermission('file:file:upload')) return
  if (!validBrandUpload(file)) { emit('error', t('tenant.uploadInvalid')); element.value = ''; return }
  const owner = auth.user?.id, portal = auth.user?.portal
  busy.value = true; emit('busy', true)
  try {
    const result = await upload(file, true)
    if (active && owner === auth.user?.id && portal === auth.user?.portal && !auth.user?.locked && auth.hasPermission('tenant:brand:edit')) emit('uploaded', result.id)
  } catch (cause) { if (active && owner === auth.user?.id && portal === auth.user?.portal && !auth.user?.locked) emit('error', (cause as Error).message) }
  finally { if (active) { busy.value = false; emit('busy', false); element.value = '' } }
}
</script>
<template><span class="inline-flex"><input ref="input" type="file" class="hidden" accept="image/png,image/jpeg,image/gif" :aria-label="label" :disabled="disabled || busy" @change="send" /><Button type="button" variant="outline" :disabled="disabled || busy || !auth.hasPermission('file:file:upload')" @click="input?.click()">{{ busy ? t('common.loading') : label }}</Button></span></template>
