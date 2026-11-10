<script setup lang="ts">
import { Inbox } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
defineProps<{ title?: string; description?: string }>()
const { t } = useI18n()
</script>
<template><div class="flex min-h-40 flex-col items-center justify-center gap-3 px-4 py-8 text-center"><div class="rounded-xl bg-muted p-3 text-muted-foreground"><Inbox class="size-5" /></div><p class="font-medium">{{ title || t('common.empty') }}</p><p v-if="description" class="max-w-md text-sm text-muted-foreground">{{ description }}</p><slot /></div></template>
