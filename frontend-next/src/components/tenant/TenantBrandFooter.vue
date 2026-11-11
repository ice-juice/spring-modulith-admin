<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { TenantEntryDto } from '@/lib/tenant-branding'
import { copyrightCaption } from '@/lib/tenant-branding'
import { safeHttpLink as link } from '@/lib/web-addresses'
const props = defineProps<{ name: string; legal?: TenantEntryDto['branding']['legal']; links?: TenantEntryDto['branding']['links'] }>()
const { t } = useI18n()
const legal = computed(() => props.legal)
const captions = computed(() => [
  { id: 'copyright', text: copyrightCaption(legal.value?.copyrightOwner, legal.value?.copyrightStartYear), url: link(legal.value?.copyrightUrl) },
  { id: 'icp', text: legal.value?.icpRecord, url: link(legal.value?.icpUrl) },
  { id: 'police', text: legal.value?.policeRecord, url: link(legal.value?.policeUrl) },
].filter(item => item.text))
const destinations = computed(() => (['website', 'help', 'support', 'privacy', 'agreement'] as const)
  .map(id => ({ id, url: link(props.links?.[id]) })).filter(item => item.url))
</script>
<template>
  <div class="branding-footer flex flex-wrap items-center gap-x-5 gap-y-2 text-xs text-muted-foreground">
    <span class="font-semibold tracking-tight text-foreground">{{ name }}</span>
    <template v-for="item in captions" :key="item.id"><a v-if="item.url" :href="item.url" target="_blank" rel="noopener noreferrer">{{ item.text }}</a><span v-else>{{ item.text }}</span></template>
    <a v-for="item in destinations" :key="item.id" :href="item.url" target="_blank" rel="noopener noreferrer">{{ t('common.brandLinks.' + item.id) }}</a>
  </div>
</template>
