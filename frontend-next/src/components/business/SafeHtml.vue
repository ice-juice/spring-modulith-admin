<script setup lang="ts">
import { computed } from 'vue'
import DOMPurify from 'dompurify'
const props = defineProps<{ html?: string | null }>()
const sanitized = computed(() => DOMPurify.sanitize(props.html || '', { USE_PROFILES: { html: true }, FORBID_TAGS: ['form', 'input', 'button', 'iframe', 'style'], FORBID_ATTR: ['style'] }))
</script>
<template><div class="rich-content break-words" v-html="sanitized" /></template>
