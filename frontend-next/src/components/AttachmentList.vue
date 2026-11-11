<script setup lang="ts">
import PermissionButton from '@/components/business/PermissionButton.vue'

import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Paperclip, X } from '@lucide/vue'
import { loadLocaleModule } from '@/locales'
import { get } from '@/api/http'
import { download, formatBytes, type Asset } from '@/features/file/files'
import { Button } from '@/components/ui/button'
import FilePicker from '@/components/business/FilePicker.vue'
import UploadButton from '@/components/business/UploadButton.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
const pickerOpen = ref(false)
const props = defineProps<{ editable?: boolean; disabled?: boolean }>(), ids = defineModel<string[]>({ required: true }), assets = ref<Asset[]>([]), error = ref(''), { t } = useI18n()
watch(() => props.disabled, disabled => { if (disabled) pickerOpen.value = false }, { flush: 'sync' })
function setIds(value: string[]) { if (props.editable && !props.disabled) ids.value = [...new Set(value)] }
void loadLocaleModule('file')
watch(ids, async (value, _old, cleanup) => { let active = true; cleanup(() => { active = false }); try { const result = await Promise.all(value.map(id => get<Asset>('/file/files/' + encodeURIComponent(id)))); if (active) { assets.value = result; error.value = '' } } catch (cause) { if (active) error.value = (cause as Error).message } }, { immediate: true, deep: true })
async function save(asset: Asset) { try { await download(asset.id, asset.name) } catch (cause) { error.value = (cause as Error).message } }
</script>
<template><div class="space-y-2"><ErrorNotice :message="error" /><div v-for="asset in assets" :key="asset.id" class="flex items-center gap-3 rounded-md border px-3 py-2"><Paperclip class="size-4 shrink-0 text-muted-foreground" /><button class="min-w-0 flex-1 truncate text-left text-sm text-primary hover:underline" @click="save(asset)">{{ asset.name }}</button><span class="shrink-0 text-xs text-muted-foreground">{{ formatBytes(asset.sizeBytes) }}</span><Button v-if="props.editable" :disabled="disabled" type="button" variant="ghost" size="icon-sm" :aria-label="t('common.remove')" @click="setIds(ids.filter(id => id !== asset.id))"><X class="size-3.5" /></Button></div><PermissionButton v-if="editable" :disabled="disabled" :permission="'file:file:view'" type="button" variant="outline" @click="pickerOpen = true">{{ t('file.selectExisting') }}</PermissionButton><FilePicker v-if="editable && !disabled" v-model:open="pickerOpen" :model-value="ids" @update:model-value="setIds" /><UploadButton v-if="editable" :disabled="disabled" v-permission="'file:file:upload'" @uploaded="setIds([...ids, $event.id])" @error="error = $event" /></div></template>
