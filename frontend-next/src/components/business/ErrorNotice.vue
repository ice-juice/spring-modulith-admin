<script setup lang="ts">
import { AlertCircle } from '@lucide/vue'
import { Alert, AlertDescription } from '@/components/ui/alert'
defineProps<{ message?: string }>()
</script>
<template><Alert v-if="message" variant="destructive" role="alert" class="mb-4"><AlertCircle class="size-4" /><AlertDescription>{{ message }}</AlertDescription></Alert></template>
