<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { UserRound } from '@lucide/vue'
import { content } from '@/features/file/files'
const props = defineProps<{ fileId?: string | null; size?: number; shape?: 'circle' | 'square'; fit?: 'contain' | 'cover'; alt?: string }>()
const url = ref(''), { t } = useI18n()
watch(() => props.fileId, async (id, _old, cleanup) => { let active = true, objectUrl = ''; url.value = ''; cleanup(() => { active = false; if (objectUrl) URL.revokeObjectURL(objectUrl) }); if (!id) return; try { const blob = await content(id, true); if (active) { objectUrl = URL.createObjectURL(blob); url.value = objectUrl } } catch { /* Unavailable files use the account fallback. */ } }, { immediate: true })
</script>
<template><span class="inline-flex shrink-0 items-center justify-center overflow-hidden bg-accent text-accent-foreground" :class="shape === 'square' ? 'rounded-lg' : 'rounded-full'" :style="{ width: (size || 36) + 'px', height: (size || 36) + 'px' }"><img v-if="url" :src="url" :alt="alt || t('common.profile')" class="h-full w-full" :style="{ objectFit: fit || 'cover' }" @error="url = ''" /><UserRound v-else class="h-1/2 w-1/2" :aria-label="alt || t('common.profile')" /></span></template>
