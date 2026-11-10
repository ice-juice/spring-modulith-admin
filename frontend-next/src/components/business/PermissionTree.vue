<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import PermissionBranch from './PermissionBranch.vue'
import EmptyState from './EmptyState.vue'
import { descendants, filterTree, toggleTree, type TreeNode } from './tree'
const props = defineProps<{ nodes: TreeNode[]; loading?: boolean; cascade?: boolean }>()
const selected = defineModel<string[]>({ default: () => [] })
const query = ref(''), expanded = ref(new Set<string>()), { t } = useI18n()
const visible = computed(() => filterTree(props.nodes, query.value))
function expand(id: string) { const next = new Set(expanded.value); if (next.has(id)) next.delete(id); else next.add(id); expanded.value = next }
function toggle(node: TreeNode, checked: boolean) { selected.value = toggleTree(props.nodes, node, selected.value, checked, !!props.cascade) }
</script>
<template><div class="rounded-lg border bg-card" :aria-busy="loading"><div class="flex flex-wrap items-center gap-2 border-b p-3"><Input v-model="query" :placeholder="t('common.searchTree')" :aria-label="t('common.searchTree')" class="min-w-36 flex-1" /><Button size="sm" variant="ghost" @click="expanded = new Set(nodes.flatMap(descendants).map(node => node.id))">{{ t('common.expandAll') }}</Button><Button size="sm" variant="ghost" @click="expanded = new Set()">{{ t('common.collapseAll') }}</Button></div><div class="max-h-[50dvh] overflow-y-auto p-2"><p v-if="loading" class="p-6 text-center text-muted-foreground">{{ t('common.loading') }}</p><PermissionBranch v-else-if="visible.length" :nodes="visible" :selected="selected" :cascade="cascade" :expanded="expanded" :searching="!!query.trim()" @toggle="toggle" @expand="expand" /><EmptyState v-else :title="t(query ? 'common.noMatch' : 'common.empty')" /></div><p class="border-t px-4 py-2 text-xs text-muted-foreground" aria-live="polite">{{ t('common.selectedCount', { count: selected.length }) }}</p></div></template>
