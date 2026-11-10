<script setup lang="ts">
import { Badge } from '@/components/ui/badge'
defineProps<{ label: string; tone?: 'success' | 'warning' | 'danger' | 'neutral' }>()
</script>
<template><Badge variant="outline" :class="{ 'border-[var(--success)]/20 bg-[var(--success)]/10 text-[var(--success)]': tone === 'success', 'border-[var(--warning)]/20 bg-[var(--warning)]/10 text-[var(--warning)]': tone === 'warning', 'border-destructive/20 bg-destructive/10 text-destructive': tone === 'danger', 'bg-muted text-muted-foreground': !tone || tone === 'neutral' }"><span class="size-1.5 rounded-full bg-current" />{{ label }}</Badge></template>
