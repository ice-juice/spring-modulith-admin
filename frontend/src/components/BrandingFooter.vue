<script setup lang="ts">
import { computed } from 'vue'
import { useBranding } from '@/stores/branding'
const branding = useBranding()
const company = computed(() => branding.values['system.copyrightCompany'])
const record = computed(() => branding.values['system.recordInfo'])
function link(value?: string) {
  return value && /^https?:\/\//.test(value) ? value : undefined
}
</script>
<template>
  <div v-if="company || record" class="branding-footer">
    <a
      v-if="company && link(branding.values['system.copyrightUrl'])"
      :href="link(branding.values['system.copyrightUrl'])"
      target="_blank"
      rel="noopener noreferrer"
      >{{ company }}</a
    ><span v-else-if="company">{{ company }}</span>
    <a
      v-if="record && link(branding.values['system.recordUrl'])"
      :href="link(branding.values['system.recordUrl'])"
      target="_blank"
      rel="noopener noreferrer"
      >{{ record }}</a
    ><span v-else-if="record">{{ record }}</span>
  </div>
</template>
