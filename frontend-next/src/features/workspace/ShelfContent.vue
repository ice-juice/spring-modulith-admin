<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { computed, inject, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/business/AppIcon.vue'
import QuickTabMenu from '@/components/business/QuickTabMenu.vue'
import { safeLocalPath } from '@/lib/web-addresses'
import { shelfNavigate } from './navigation'
import { Button } from '@/components/ui/button'
import OperationStatus from '@/components/business/OperationStatus.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import BookmarkList from './BookmarkList.vue'
import NotesPanel from './NotesPanel.vue'
import { useShelf, type ShelfBoard } from './shelf'
const emit = defineEmits<{ mode: [mode: ShelfBoard['mode']]; float: [key: string] }>()
const shelf = useShelf(), { t } = useI18n()
const router = useRouter(), navigatePage = inject(shelfNavigate, path => router.push(path)), quickMenu = ref<InstanceType<typeof QuickTabMenu>>()
const navigating = ref(false)
const targets = computed(() => shelf.board.targets.filter(target => safeLocalPath(target.path)))
async function navigateTarget(id: string) {
  if (navigating.value) return
  const identity = shelf.identity
  navigating.value = true
  try {
    if (!await shelf.load() || shelf.identity !== identity) return
    const path = safeLocalPath(shelf.board.targets.find(target => target.id === id)?.path)
    if (!path) return
    const failure = await navigatePage(path)
    if (shelf.identity !== identity || failure) return
    quickMenu.value?.close()
    if (shelf.board.mode === 'drawer') shelf.open = false
  } finally { navigating.value = false }
}
</script>
<template>
  <div class="flex min-h-0 flex-1 flex-col gap-3">
    <label class="text-xs text-muted-foreground">{{ t('common.shelf.displayMode') }}<select class="mt-1 h-9 w-full rounded-md border bg-background px-2 text-sm text-foreground" :aria-label="t('common.shelf.displayMode')" :value="shelf.board.mode" :disabled="shelf.busy" @change="emit('mode', ($event.target as HTMLSelectElement).value as ShelfBoard['mode'])"><option value="drawer">{{ t('common.shelf.drawer') }}</option><option value="floating">{{ t('common.shelf.floating') }}</option><option value="docked">{{ t('common.shelf.docked') }}</option></select></label>
    <QuickTabMenu ref="quickMenu" :model-value="shelf.section" :tabs="[{ value: 'bookmarks', label: t('common.shelf.tags') }, { value: 'notes', label: t('common.shelf.notes') }]" :label="t('common.shelf.title')" :expand-label="t('common.shelf.quickOpen')" :collapse-label="t('common.shelf.quickClose')" :disabled="!shelf.ready" @update:model-value="shelf.section = $event === 'notes' ? 'notes' : 'bookmarks'" @expand="shelf.load()">
      <template #actions><div class="grid grid-cols-2 gap-1" data-shelf-shortcuts><Button v-for="target in targets" :key="target.id" type="button" variant="ghost" class="h-auto min-w-0 justify-start whitespace-normal break-words px-2 py-3 text-left" :disabled="shelf.loading || navigating" @click="navigateTarget(target.id)"><AppIcon :name="target.icon" :size="16" /><span class="min-w-0">{{ shelf.targetLabel(target) }}</span></Button><p v-if="!targets.length" class="col-span-2 p-3 text-sm text-muted-foreground">{{ t('common.noMatch') }}</p></div></template>
    </QuickTabMenu>
    <ErrorNotice :message="shelf.error" /><OperationStatus :slow="shelf.slow" :unresolved="shelf.unresolved" :querying="shelf.querying" queryable @check="shelf.check" /><Button v-if="shelf.error && !shelf.unresolved" variant="outline" @click="shelf.load()">{{ t('common.retry') }}</Button>
    <p v-if="shelf.loading && !shelf.ready" class="py-6 text-sm">{{ t('common.loading') }}</p>
    <BookmarkList v-if="shelf.ready" v-show="shelf.section === 'bookmarks'" :compact="shelf.board.mode === 'floating'" />
    <NotesPanel v-if="shelf.ready && shelf.section === 'notes'" @float="emit('float', $event)" />
  </div>
</template>
