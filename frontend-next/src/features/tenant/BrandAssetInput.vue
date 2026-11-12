<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuth } from '@/stores/auth'
import UploadButton from '@/components/business/UploadButton.vue'
import { validBrandUpload } from './brands'
const props = defineProps<{ scope: string; label: string; disabled?: boolean }>()
const emit = defineEmits<{ uploaded: [id: string]; busy: [value: boolean]; error: [message: string] }>()
const busy = ref(false), auth = useAuth(), { t } = useI18n()
function validate(file: File) { if (!validBrandUpload(file)) throw new Error(t('tenant.uploadInvalid')) }
function pending(value: boolean) { busy.value = value; emit('busy', value) }
</script>
<template><UploadButton :scope="scope" :label="label" image-only :validate="validate" :disabled="(props.disabled && !busy) || !auth.hasPermission('file:file:upload') || !auth.hasPermission('tenant:brand:edit') || !!auth.user?.locked" @busy="pending" @uploaded="emit('uploaded', $event.id)" @error="emit('error', $event)" /></template>
