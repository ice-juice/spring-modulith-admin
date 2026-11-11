<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Info } from '@lucide/vue'
import PinPageButton from '@/features/workspace/PinPageButton.vue'
import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
const props = defineProps<{ title: string; description?: string; eyebrow?: string }>()
const route = useRoute(), { t, te } = useI18n()
const section = computed(() => props.eyebrow || (te('common.sections.' + route.meta.module) ? t('common.sections.' + route.meta.module) : ''))
const hint = computed(() => props.description || (te('common.pageHints.' + route.meta.titleKey) ? t('common.pageHints.' + route.meta.titleKey) : ''))
</script>
<template>
  <div class="page-heading mb-3 flex min-h-10 flex-wrap items-center justify-between gap-x-4 gap-y-2">
    <div class="flex min-w-0 items-center gap-1.5"><span v-if="section" class="sr-only">{{ section }}</span><h1 class="text-lg leading-7 font-semibold break-words tracking-tight">{{ title }}</h1><PinPageButton /><Popover v-if="hint"><PopoverTrigger as-child><Button variant="ghost" size="icon-sm" class="shrink-0 text-muted-foreground" :aria-label="t('common.description')"><Info class="size-4" /></Button></PopoverTrigger><PopoverContent align="start" class="max-w-[calc(100vw-2rem)] text-sm leading-6"><p>{{ hint }}</p></PopoverContent></Popover></div>
    <div v-if="$slots.default" class="flex max-w-full flex-wrap items-center gap-2"><slot /></div>
  </div>
</template>
