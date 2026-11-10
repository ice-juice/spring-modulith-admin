<script setup lang="ts">
import { computed } from 'vue'
import { useBranding } from '@/stores/branding'
const branding = useBranding()
const company = computed(() => branding.values['system.copyrightCompany']), record = computed(() => branding.values['system.recordInfo'])
function link(value?: string) { return value && /^https?:\/\//i.test(value) ? value : undefined }
</script>
<template><div class="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground"><span>{{ branding.name }}</span><a v-if="company && link(branding.values['system.copyrightUrl'])" :href="link(branding.values['system.copyrightUrl'])" target="_blank" rel="noopener noreferrer" class="hover:text-foreground">{{ company }}</a><span v-else-if="company">{{ company }}</span><a v-if="record && link(branding.values['system.recordUrl'])" :href="link(branding.values['system.recordUrl'])" target="_blank" rel="noopener noreferrer" class="hover:text-foreground">{{ record }}</a><span v-else-if="record">{{ record }}</span></div></template>
