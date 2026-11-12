<script setup lang="ts">
import { computed, inject, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowUp, ArrowDown, GripVertical, Pencil, X, LayoutGrid, List } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import AppIcon from '@/components/business/AppIcon.vue'
import { safeLocalPath } from '@/lib/web-addresses'
import { useShelf, type Bookmark } from './shelf'
import { shelfNavigate } from './navigation'
import { useBookmarkLayout, useCollectionMotion, type BookmarkLayout } from './bookmark-layout'
const shelf = useShelf(), { t } = useI18n(), router = useRouter(), route = useRoute()
const navigatePage = inject(shelfNavigate, path => router.push(path))
const props = defineProps<{ compact?: boolean }>()
const more = ref(false)
const collection = ref<HTMLElement>(), { layout, select } = useBookmarkLayout(computed(() => shelf.board.scope))
const motion = useCollectionMotion(collection)
let layoutGeneration = 0
async function changeLayout(value: BookmarkLayout) {
  if (layout.value === value) return
  const generation = ++layoutGeneration, before = motion.capture(), identity = shelf.identity
  select(value); await nextTick()
  if (generation === layoutGeneration && identity === shelf.identity) motion.play(before)
}
const query = ref(''), adding = ref(false), editId = ref(''), alias = ref(''), dragging = ref('')
const undo = ref<{ menuId: string; alias: string }>(), undoUntil = ref(0)
const rows = computed(() => shelf.board.bookmarks.filter(b => shelf.label(b).toLocaleLowerCase().includes(query.value.toLocaleLowerCase())))
const shownRows = computed(() => props.compact && !more.value && !query.value ? rows.value.slice(0, 6) : rows.value)
const choices = computed(() => shelf.board.targets.filter(t => !shelf.board.bookmarks.some(b => b.menuId === t.id) && shelf.targetLabel(t).toLocaleLowerCase().includes(query.value.toLocaleLowerCase())))
watch(() => shelf.identity, () => { query.value = ''; editId.value = ''; alias.value = ''; undo.value = undefined; adding.value = false })
async function navigate(row: Bookmark) {
  if (!await shelf.load()) return
  const latest = shelf.board.bookmarks.find(b => b.id === row.id)
  const path = safeLocalPath(latest?.target?.path)
  if (!path) return
  const failure = await navigatePage(path)
  if (!failure && shelf.board.mode === 'drawer') shelf.open = false
}
async function remove(row: Bookmark) {
  if (await shelf.remove(row.id)) { undo.value = { menuId: row.menuId, alias: row.alias }; undoUntil.value = Date.now() + 8000; setTimeout(() => { if (Date.now() >= undoUntil.value) undo.value = undefined }, 8100) }
}
async function restore() { if (undo.value && Date.now() < undoUntil.value && await shelf.add(undo.value.menuId, undo.value.alias)) undo.value = undefined }
async function move(id: string, to: number) {
  const ids = shelf.board.bookmarks.map(b => b.id), from = ids.indexOf(id)
  if (from < 0 || to < 0 || to >= ids.length) return
  ids.splice(from, 1); ids.splice(to, 0, id); await shelf.reorder(ids)
}
async function saveAlias() { if (await shelf.rename(editId.value, alias.value)) editId.value = '' }
</script>
<template>
  <div class="space-y-3">
    <div class="flex gap-2"><Input v-model="query" :placeholder="t('common.shelf.searchTags')" :aria-label="t('common.shelf.searchTags')" /><Button variant="outline" :disabled="shelf.busy || shelf.board.bookmarks.length >= 30" @click="adding = !adding">{{ t(adding ? 'common.cancel' : 'common.shelf.add') }}</Button></div>
    <div class="flex flex-wrap items-center justify-between gap-2"><p class="text-xs text-muted-foreground">{{ t('common.shelf.tagCount', { count: shelf.board.bookmarks.length }) }}</p><div class="flex gap-1" role="group" :aria-label="t('common.shelf.bookmarkLayout')"><Button type="button" :variant="layout === 'list' ? 'secondary' : 'ghost'" size="icon-sm" :aria-label="t('common.shelf.listView')" :aria-pressed="layout === 'list'" @click="changeLayout('list')"><List class="size-4" /></Button><Button type="button" :variant="layout === 'cards' ? 'secondary' : 'ghost'" size="icon-sm" :aria-label="t('common.shelf.cardView')" :aria-pressed="layout === 'cards'" @click="changeLayout('cards')"><LayoutGrid class="size-4" /></Button></div></div>
    <div v-if="adding" class="space-y-1 border-b pb-3" :aria-label="t('common.shelf.choosePage')">
      <Button v-for="item in choices" :key="item.id" variant="ghost" class="h-auto w-full justify-start whitespace-normal text-left" :disabled="shelf.busy" @click="shelf.add(item.id).then(ok => { if (ok) adding = false })"><AppIcon :name="item.icon" :size="16" />{{ shelf.targetLabel(item) }}</Button>
      <p v-if="!choices.length" class="py-3 text-sm text-muted-foreground">{{ t('common.noMatch') }}</p>
    </div>
    <div v-if="undo" class="flex items-center justify-between rounded-lg bg-muted p-2 text-sm"><span>{{ t('common.shelf.removed') }}</span><Button variant="ghost" size="sm" :disabled="shelf.busy" @click="restore">{{ t('common.shelf.undo') }}</Button></div>
    <p v-if="!rows.length" class="py-8 text-center text-sm text-muted-foreground">{{ t(query ? 'common.noMatch' : 'common.shelf.emptyTags') }}</p>
    <Button v-if="compact && rows.length > 6 && !query" variant="ghost" size="sm" @click="more = !more">{{ t(more ? 'common.shelf.lessTags' : 'common.shelf.moreTags') }}</Button>
    <div ref="collection" class="bookmark-collection" :data-layout="layout">
    <div v-for="row in shownRows" :key="row.id" class="min-w-0 rounded-lg border p-2" :class="row.target?.path === route.path ? 'border-primary/40 bg-accent' : 'bg-card'" data-shelf-bookmark @dragover.prevent @drop.prevent="move(dragging, shelf.board.bookmarks.findIndex(b => b.id === row.id)); dragging = ''">
      <div class="flex min-w-0 items-center gap-1">
        <span :draggable="!shelf.busy && !query" class="cursor-grab p-1 text-muted-foreground" :title="t('common.shelf.drag')" @dragstart="dragging = row.id" @dragend="dragging = ''"><GripVertical class="size-3" /></span>
        <Button variant="ghost" class="h-auto min-w-0 flex-1 justify-start px-1 text-left" :disabled="!row.target || shelf.loading" :title="shelf.label(row)" @click="navigate(row)"><AppIcon :name="row.target?.icon" :size="16" /><span class="truncate">{{ shelf.label(row) }}</span></Button>
        <Button variant="ghost" size="icon-sm" :aria-label="t('common.shelf.rename')" @click="editId = row.id; alias = row.alias"><Pencil class="size-3" /></Button>
        <Button variant="ghost" size="icon-sm" :disabled="shelf.busy" :aria-label="t('common.shelf.removeTag')" @click="remove(row)"><X class="size-3" /></Button>
      </div>
      <p v-if="!row.target" class="px-2 text-xs text-muted-foreground">{{ t('common.shelf.unavailable') }}</p>
      <div v-if="editId === row.id" class="mt-2 flex gap-1"><Input v-model="alias" maxlength="50" :aria-label="t('common.shelf.alias')" @keyup.enter="saveAlias" /><Button size="sm" :disabled="shelf.busy" @click="saveAlias">{{ t('common.save') }}</Button><Button size="sm" variant="ghost" @click="editId = ''">{{ t('common.cancel') }}</Button></div>
      <div class="flex justify-end gap-1"><Button variant="ghost" size="icon-sm" :disabled="shelf.busy || shelf.board.bookmarks[0]?.id === row.id" :aria-label="t('common.shelf.moveUp')" @click="move(row.id, shelf.board.bookmarks.findIndex(b => b.id === row.id) - 1)"><ArrowUp class="size-3" /></Button><Button variant="ghost" size="icon-sm" :disabled="shelf.busy || shelf.board.bookmarks.at(-1)?.id === row.id" :aria-label="t('common.shelf.moveDown')" @click="move(row.id, shelf.board.bookmarks.findIndex(b => b.id === row.id) + 1)"><ArrowDown class="size-3" /></Button></div>
    </div>
    </div>
  </div>
</template>
<style scoped>
.bookmark-collection { display: grid; grid-template-columns: minmax(0,1fr); gap: 10px; }
.bookmark-collection[data-layout='cards'] { grid-template-columns: repeat(auto-fit,minmax(min(100%,150px),1fr)); }
.bookmark-collection[data-layout='cards'] [data-shelf-bookmark] > div:first-child { flex-wrap: wrap; }
.bookmark-collection[data-layout='cards'] [data-shelf-bookmark] > div:first-child > button:first-of-type { order: -1; flex-basis: 100%; flex-direction: column; align-items: flex-start; padding: 12px 8px; gap: 12px; }
.bookmark-collection[data-layout='cards'] [data-shelf-bookmark] > div:first-child > button:first-of-type > span { max-width: 100%; white-space: normal; overflow-wrap: anywhere; }
.bookmark-collection[data-layout='cards'] [data-shelf-bookmark] > div:nth-child(2) { flex-wrap: wrap; }
.bookmark-collection[data-layout='cards'] input { min-width: 0; flex-basis: 100%; }
</style>
