<script setup lang="ts">
import { computed } from 'vue'
import { ChevronRight } from '@lucide/vue'
import { Checkbox } from '@/components/ui/checkbox'
import { checkedState, type TreeNode } from './tree'
const props = defineProps<{ nodes: TreeNode[]; selected: string[]; expanded: Set<string>; searching?: boolean; cascade?: boolean }>()
const emit = defineEmits<{ toggle: [node: TreeNode, checked: boolean]; expand: [id: string] }>()
const chosen = computed(() => new Set(props.selected))
</script>
<template><ul class="space-y-0.5"><li v-for="node in nodes" :key="node.id"><div class="group flex min-h-10 items-center gap-2 rounded-md px-2 hover:bg-muted"><button v-if="node.children?.length" type="button" class="flex size-6 shrink-0 items-center justify-center rounded hover:bg-accent" :aria-label="node.name" :aria-expanded="searching || expanded.has(node.id)" @click="emit('expand', node.id)"><ChevronRight class="size-3.5 transition-transform" :class="{ 'rotate-90': searching || expanded.has(node.id) }" /></button><span v-else class="size-6 shrink-0" /><Checkbox :id="'permission-' + node.id" :model-value="cascade ? checkedState(node, chosen) : chosen.has(node.id)" :disabled="node.disabled" :aria-label="node.name" @update:model-value="emit('toggle', node, !!$event)" /><label :for="'permission-' + node.id" class="min-w-0 flex-1 cursor-pointer break-words text-sm" :class="{ 'text-muted-foreground': node.disabled }">{{ node.name }}</label></div><PermissionBranch v-if="node.children?.length && (searching || expanded.has(node.id))" class="ml-5 border-l pl-2" :nodes="node.children" :selected="selected" :cascade="cascade" :expanded="expanded" :searching="searching" @toggle="(node, checked) => emit('toggle', node, checked)" @expand="emit('expand', $event)" /></li></ul></template>
