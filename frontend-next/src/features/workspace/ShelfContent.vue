<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import OperationStatus from '@/components/business/OperationStatus.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import BookmarkList from './BookmarkList.vue'
import NotesPanel from './NotesPanel.vue'
import { useShelf, type ShelfBoard } from './shelf'
const emit = defineEmits<{ mode: [mode: ShelfBoard['mode']]; float: [key: string] }>()
const shelf = useShelf(), { t } = useI18n()
</script>
<template>
  <div class="flex min-h-0 flex-1 flex-col gap-3">
    <label class="text-xs text-muted-foreground">{{ t('common.shelf.displayMode') }}<select class="mt-1 h-9 w-full rounded-md border bg-background px-2 text-sm text-foreground" :aria-label="t('common.shelf.displayMode')" :value="shelf.board.mode" :disabled="shelf.busy" @change="emit('mode', ($event.target as HTMLSelectElement).value as ShelfBoard['mode'])"><option value="drawer">{{ t('common.shelf.drawer') }}</option><option value="floating">{{ t('common.shelf.floating') }}</option><option value="docked">{{ t('common.shelf.docked') }}</option></select></label>
    <div class="flex gap-1"><Button :variant="shelf.section === 'bookmarks' ? 'secondary' : 'ghost'" :aria-pressed="shelf.section === 'bookmarks'" @click="shelf.section = 'bookmarks'">{{ t('common.shelf.tags') }}</Button><Button :variant="shelf.section === 'notes' ? 'secondary' : 'ghost'" :aria-pressed="shelf.section === 'notes'" @click="shelf.section = 'notes'">{{ t('common.shelf.notes') }}</Button></div>
    <ErrorNotice :message="shelf.error" /><OperationStatus :slow="shelf.slow" :unresolved="shelf.unresolved" :querying="shelf.querying" queryable @check="shelf.check" /><Button v-if="shelf.error && !shelf.unresolved" variant="outline" @click="shelf.load()">{{ t('common.retry') }}</Button>
    <p v-if="shelf.loading && !shelf.ready" class="py-6 text-sm">{{ t('common.loading') }}</p>
    <BookmarkList v-if="shelf.ready && shelf.section === 'bookmarks'" :compact="shelf.board.mode === 'floating'" />
    <NotesPanel v-if="shelf.ready && shelf.section === 'notes'" @float="emit('float', $event)" />
  </div>
</template>
