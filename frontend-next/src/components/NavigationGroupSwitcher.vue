<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronsUpDown } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuLabel, DropdownMenuRadioGroup, DropdownMenuRadioItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import AppIcon from '@/components/business/AppIcon.vue'
import type { CurrentUser } from '@/stores/auth'
const props = defineProps<{ groups: CurrentUser['navigation']; collapsed?: boolean }>()
const value = defineModel<string>({ required: true }), { t } = useI18n()
const name = computed(() => props.groups.find(group => group.id === value.value)?.name || t('common.allNavigation'))
</script>
<template>
  <DropdownMenu>
    <DropdownMenuTrigger as-child>
      <Button variant="outline" data-navigation-switcher class="navigation-switcher" :class="{ 'is-collapsed': collapsed }" :aria-label="t('common.switchNavigation')" :title="name">
        <span class="navigation-symbol"><AppIcon :name="groups.find(group => group.id === value)?.icon" fallback="lucide:layers" :size="24" /></span>
        <span v-if="!collapsed" class="min-w-0 flex-1 text-left"><span class="block truncate text-sm font-medium">{{ name }}</span><span class="block truncate text-xs font-normal text-muted-foreground">{{ t('common.businessNavigation') }}</span></span>
        <ChevronsUpDown v-if="!collapsed" class="size-3.5 text-muted-foreground" />
      </Button>
    </DropdownMenuTrigger>
    <DropdownMenuContent align="start" :side="collapsed ? 'right' : 'bottom'" class="max-h-[min(60dvh,24rem)] w-64 max-w-[calc(100vw-2rem)] overflow-y-auto" data-scroll-region="business-navigation">
      <DropdownMenuLabel>{{ t('common.businessNavigation') }}</DropdownMenuLabel><DropdownMenuSeparator />
      <DropdownMenuRadioGroup v-model="value"><DropdownMenuRadioItem value="">{{ t('common.allNavigation') }}</DropdownMenuRadioItem><DropdownMenuRadioItem v-for="group in groups" :key="group.id" :value="group.id"><AppIcon :name="group.icon" fallback="lucide:layers" :size="20" /><span class="break-words">{{ group.name }}</span></DropdownMenuRadioItem></DropdownMenuRadioGroup>
    </DropdownMenuContent>
  </DropdownMenu>
</template>
