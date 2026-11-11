<script setup lang="ts">
import { Ellipsis } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
const { t } = useI18n()
</script>
<template><div class="hidden flex-wrap items-center gap-1 sm:flex"><slot /></div><Popover><PopoverTrigger as-child><Button class="sm:hidden" variant="ghost" size="icon-sm" :aria-label="t('common.actions')"><Ellipsis class="size-4" /></Button></PopoverTrigger><PopoverContent class="w-52 p-2" align="end"><div class="flex flex-col items-stretch gap-1"><slot /></div></PopoverContent></Popover></template>
