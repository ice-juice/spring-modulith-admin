<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Pin, Ellipsis } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem } from '@/components/ui/dropdown-menu'
import { useShelf } from './shelf'
const props = defineProps<{ path?: string; menu?: boolean }>()
const route = useRoute(), shelf = useShelf(), { t } = useI18n()
const target = computed(() => shelf.target(props.path || route.path))
const pinned = computed(() => target.value && shelf.board.bookmarks.find(b => b.menuId === target.value?.id))
async function toggle() {
  if (!target.value) return
  const ok = pinned.value ? await shelf.remove(pinned.value.id) : await shelf.add(target.value.id)
  if (!ok) shelf.open = true
}
</script>
<template>
  <DropdownMenu v-if="target && menu"><DropdownMenuTrigger as-child><Button variant="ghost" size="icon-sm" :aria-label="t('common.shelf.tabActions')"><Ellipsis class="size-3.5" /></Button></DropdownMenuTrigger><DropdownMenuContent><DropdownMenuItem :disabled="shelf.busy" @select="toggle"><Pin class="size-3.5" />{{ t(pinned ? 'common.shelf.unpinPage' : 'common.shelf.pinPage') }}</DropdownMenuItem></DropdownMenuContent></DropdownMenu>
  <Button v-else-if="target" variant="ghost" size="icon-sm" :disabled="shelf.busy" :aria-pressed="!!pinned" :aria-label="t(pinned ? 'common.shelf.unpinPage' : 'common.shelf.pinPage')" :title="t(pinned ? 'common.shelf.unpinPage' : 'common.shelf.pinPage')" @click="toggle"><Pin class="size-3.5" :class="pinned ? 'fill-primary/20 text-primary' : 'text-muted-foreground'" /></Button>
</template>
