<script setup lang="ts">
import { computed } from 'vue'
import { sanitizeRichHtml } from '@/lib/safe-html'
const props = defineProps<{ html?: string | null }>()
const sanitized = computed(() => sanitizeRichHtml(props.html))
</script>
<template><div class="rich-content break-words" v-html="sanitized" /></template>
