<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useBranding } from '@/stores/branding'
import { copyrightCaption } from '@/lib/tenant-branding'
import { legacyBrandImage } from '@/lib/tenant-branding'
const link = (value?: string | null) => value && /^https?:\/\//i.test(value) ? legacyBrandImage(value) : undefined
const branding = useBranding(), { t } = useI18n()
const legal = computed(() => branding.entry?.branding.legal)
const captions = computed(() => [
  { id: 'copyright', text: copyrightCaption(legal.value?.copyrightOwner, legal.value?.copyrightStartYear), url: link(legal.value?.copyrightUrl) },
  { id: 'icp', text: legal.value?.icpRecord, url: link(legal.value?.icpUrl) },
  { id: 'police', text: legal.value?.policeRecord, url: link(legal.value?.policeUrl) },
].filter(item => item.text))
const destinations = computed(() => (['website', 'help', 'support', 'privacy', 'agreement'] as const)
  .map(id => ({ id, url: link(branding.entry?.branding.links?.[id]) })).filter(item => item.url))
</script>
<template>
  <div class="branding-footer">
    <template v-for="item in captions" :key="item.id"><a v-if="item.url" :href="item.url" target="_blank" rel="noopener noreferrer">{{ item.text }}</a><span v-else>{{ item.text }}</span></template>
    <a v-for="item in destinations" :key="item.id" :href="item.url" target="_blank" rel="noopener noreferrer">{{ t('common.brandLinks.' + item.id) }}</a>
  </div>
</template>
