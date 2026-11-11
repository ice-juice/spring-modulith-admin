<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ChevronRight, ExternalLink } from '@lucide/vue'
import AppIcon from '@/components/business/AppIcon.vue'
import type { MenuItem } from '@/stores/auth'
import { menuRoute } from '@/lib/menus'
import { safeHttpLink } from '@/lib/web-addresses'
const props = defineProps<{ items: MenuItem[]; collapsed?: boolean; depth?: number }>()
const emit = defineEmits<{ navigate: [] }>()
const route = useRoute(), closed = ref(new Set<string>())
function toggle(id: string) { const next = new Set(closed.value); if (next.has(id)) next.delete(id); else next.add(id); closed.value = next }
function external(item: MenuItem) { const url = safeHttpLink(item.path); if (url) window.open(url, '_blank', 'noopener,noreferrer'); emit('navigate') }
</script>
<template><ul class="space-y-1"><li v-for="item in props.items" :key="item.id"><template v-if="item.children?.length"><button type="button" class="flex min-h-11 w-full items-center gap-2.5 rounded-xl px-3 text-left text-sm text-muted-foreground hover:bg-muted hover:text-foreground" :title="collapsed ? item.name : undefined" :aria-expanded="!closed.has(item.id)" :aria-label="item.name" @click="toggle(item.id)"><AppIcon :name="item.icon" :size="item.icon?.startsWith('3d:') ? 24 : 18" /><span v-if="!collapsed" class="min-w-0 flex-1 truncate">{{ item.name }}</span><ChevronRight v-if="!collapsed" class="size-3.5 shrink-0 transition-transform" :class="{ 'rotate-90': !closed.has(item.id) }" /></button><NavigationTree v-if="!closed.has(item.id)" class="mt-1" :class="collapsed ? '' : 'ml-5 border-l pl-2'" :items="item.children" :collapsed="collapsed" :depth="(depth || 0) + 1" @navigate="emit('navigate')" /></template><button v-else-if="item.openMode === 'EXTERNAL'" type="button" class="flex min-h-11 w-full items-center gap-2.5 rounded-xl px-3 text-left text-sm text-muted-foreground hover:bg-muted" :title="collapsed ? item.name : undefined" :aria-label="item.name" @click="external(item)"><AppIcon :name="item.icon" :size="item.icon?.startsWith('3d:') ? 24 : 18" /><span v-if="!collapsed" class="min-w-0 flex-1 truncate">{{ item.name }}</span><ExternalLink v-if="!collapsed" class="size-3.5" /></button><RouterLink v-else :to="menuRoute(item)" class="flex min-h-11 items-center gap-2.5 rounded-xl px-3 text-sm transition-colors" :class="route.path === menuRoute(item) ? 'bg-accent font-medium text-accent-foreground navigation-current' : 'text-muted-foreground hover:bg-muted hover:text-foreground'" :title="collapsed ? item.name : undefined" :aria-label="item.name" :aria-current="route.path === menuRoute(item) ? 'page' : undefined" @click="emit('navigate')"><AppIcon :name="item.icon" :size="item.icon?.startsWith('3d:') ? 24 : 18" /><span v-if="!collapsed" class="truncate">{{ item.name }}</span></RouterLink></li></ul></template>
