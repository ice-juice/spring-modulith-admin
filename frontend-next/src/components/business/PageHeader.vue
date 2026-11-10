<script setup lang="ts">
defineProps<{ title: string; description?: string; eyebrow?: string }>()
</script>
<template>
  <div class="mb-6 flex flex-wrap items-end justify-between gap-4">
    <div class="min-w-0 space-y-1.5"><p v-if="eyebrow" class="text-xs font-medium tracking-wider text-primary">{{ eyebrow }}</p><h1 class="text-2xl font-semibold tracking-tight">{{ title }}</h1><p v-if="description" class="max-w-2xl text-sm text-muted-foreground">{{ description }}</p></div>
    <div v-if="$slots.default" class="flex flex-wrap items-center gap-2"><slot /></div>
  </div>
</template>
