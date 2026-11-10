<script setup lang="ts">
import { ref } from 'vue'
import { Upload, LoaderCircle } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { upload, type Asset } from '@/features/file/files'
import { notify } from '@/lib/feedback'
const props = defineProps<{ label?: string; imageOnly?: boolean; disabled?: boolean }>(), emit = defineEmits<{ uploaded: [asset: Asset]; error: [message: string] }>()
const input = ref<HTMLInputElement>(), busy = ref(false), { t } = useI18n()
async function send(event: Event) { const element = event.target as HTMLInputElement, file = element.files?.[0]; if (!file || busy.value) return; busy.value = true; try { if (file.size > 20 * 1024 * 1024) throw new Error(t('file.tooLarge')); const asset = await upload(file, !!props.imageOnly); emit('uploaded', asset) } catch (cause) { const message = (cause as Error).message; emit('error', message); notify(message, 'error') } finally { busy.value = false; element.value = '' } }
</script>
<template><span class="inline-flex"><input ref="input" class="hidden" type="file" :accept="imageOnly ? 'image/*' : undefined" :disabled="disabled || busy" :aria-label="label || t('file.upload')" @change="send" /><Button type="button" variant="outline" :disabled="disabled || busy" @click="input?.click()"><LoaderCircle v-if="busy" class="size-4 animate-spin" /><Upload v-else class="size-4" />{{ label || t('file.upload') }}</Button></span></template>
