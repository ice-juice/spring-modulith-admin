<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Folder } from '@lucide/vue'
import { resolveIcon } from '@/lib/icons'
const props = withDefaults(defineProps<{ name?: string | null; size?: number; fallback?: string }>(), { size: 20, fallback: 'lucide:folder' })
const failed = ref(false)
const assetBase = import.meta.env.BASE_URL
const entry = computed(() => resolveIcon(props.name))
const backup = computed(() => resolveIcon(props.fallback)?.component || Folder)
watch(() => props.name, () => { failed.value = false })
</script>
<template>
  <span v-if="name !== 'none'" class="app-icon inline-flex shrink-0 items-center justify-center align-middle" :style="{ width: size + 'px', height: size + 'px' }" :data-icon="entry?.id || 'fallback'" aria-hidden="true">
    <img v-if="entry?.file && !failed" :src="`${assetBase}${entry.file}`" alt="" :width="size" :height="size" class="size-full object-contain" loading="lazy" decoding="async" draggable="false" @error="failed = true" />
    <component v-else :is="entry?.component || backup" class="size-full" :size="size" />
  </span>
</template>
