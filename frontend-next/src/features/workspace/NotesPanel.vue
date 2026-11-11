<script setup lang="ts">
import { computed, ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Pin } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import { useShelfNotes, type PersonalNote } from './notes'
import { useShelf } from './shelf'
import NoteEditor from './NoteEditor.vue'
import { formatInstant } from '@/lib/time'
import { usePreferences } from '@/stores/preferences'
const emit = defineEmits<{ float: [key: string] }>()
const notes = useShelfNotes(), shelf = useShelf(), route = useRoute(), { t } = useI18n()
const preferences = usePreferences()
const query = ref(''), filter = ref<PersonalNote['status']>('active')
const activeCount = computed(() => notes.list.filter(([, d]) => !d.server || d.server.status === 'active').length)
const rows = computed(() => notes.list.filter(([, d]) => (d.server?.status || 'active') === filter.value && (d.input.title + '\n' + d.input.content + '\n' + d.input.items.map(i => i.text).join('\n')).toLocaleLowerCase().includes(query.value.toLocaleLowerCase())))
const selected = computed(() => notes.entries[notes.selected])
onMounted(() => { void notes.load() })
watch(() => shelf.identity, () => { query.value = ''; filter.value = 'active' })
function create(kind: 'text' | 'checklist') { notes.create(kind, shelf.target(route.path)?.id || null); filter.value = 'active' }
async function back() { if (!selected.value || await notes.save(notes.selected, true)) notes.selected = '' }
async function state(key: string, status: PersonalNote['status']) { if (await notes.transition(key, status)) notes.selected = '' }
</script>
<template>
  <div class="space-y-3">
    <ErrorNotice :message="notes.error" /><Button v-if="notes.error" variant="outline" @click="notes.load()">{{ t('common.retry') }}</Button>
    <template v-if="selected">
      <div class="flex flex-wrap items-center justify-between gap-2"><Button variant="ghost" size="sm" @click="back">{{ t('common.shelf.backNotes') }}</Button><Button v-if="!selected.server || selected.server.status === 'active'" variant="outline" size="sm" class="hidden md:inline-flex" @click="emit('float', notes.selected)">{{ t('common.shelf.floatNote') }}</Button></div>
      <NoteEditor :note-key="notes.selected" />
      <div v-if="selected.server" class="flex flex-wrap gap-2 border-t pt-3">
        <Button v-if="selected.server.status === 'active'" variant="outline" size="sm" :disabled="selected.saving" @click="state(notes.selected, 'archived')">{{ t('common.shelf.archive') }}</Button>
        <Button v-if="selected.server.status !== 'active'" variant="outline" size="sm" :disabled="selected.saving" @click="state(notes.selected, 'active')">{{ t('common.shelf.restore') }}</Button>
        <Button v-if="selected.server.status !== 'trash'" variant="ghost" size="sm" :disabled="selected.saving" @click="state(notes.selected, 'trash')">{{ t('common.shelf.trashNote') }}</Button>
      </div>
    </template>
    <template v-else>
      <div class="flex flex-wrap gap-2"><Button size="sm" :disabled="activeCount >= 200 || notes.loading" @click="create('text')">{{ t('common.shelf.newText') }}</Button><Button size="sm" variant="outline" :disabled="activeCount >= 200 || notes.loading" @click="create('checklist')">{{ t('common.shelf.newChecklist') }}</Button><Button size="sm" variant="ghost" @click="notes.load()">{{ t('common.refresh') }}</Button></div>
      <div class="flex gap-1" :aria-label="t('common.shelf.noteStatus')"><Button v-for="status in (['active', 'archived', 'trash'] as const)" :key="status" size="sm" :variant="filter === status ? 'secondary' : 'ghost'" :aria-pressed="filter === status" @click="filter = status">{{ t('common.shelf.status.' + status) }}</Button></div>
      <Input v-model="query" :placeholder="t('common.shelf.searchNotes')" :aria-label="t('common.shelf.searchNotes')" />
      <p v-if="filter === 'trash'" class="text-xs text-muted-foreground">{{ t('common.shelf.trashHint') }}</p>
      <p v-if="notes.loading && !notes.loaded" class="py-5 text-sm">{{ t('common.loading') }}</p>
      <p v-else-if="!rows.length" class="py-5 text-center text-sm text-muted-foreground">{{ t('common.shelf.emptyNotes') }}</p>
      <button v-for="[key, draft] in rows" :key="key" class="block w-full rounded-xl border bg-card p-3 text-left hover:bg-accent" data-note-row :data-note-color="draft.input.color" @click="notes.selected = key"><span class="block truncate text-sm font-medium"><Pin v-if="draft.input.pinned" class="mr-1 inline size-3" />{{ draft.input.title }}</span><span class="mt-1 line-clamp-2 whitespace-pre-wrap break-words text-xs text-muted-foreground">{{ draft.input.content || draft.input.items.map(i => (i.done ? '☑ ' : '☐ ') + i.text).join('\n') }}</span><span v-if="draft.input.kind === 'checklist'" class="mt-1 block text-xs text-muted-foreground">{{ t('common.shelf.completedCount', { done: draft.input.items.filter(i => i.done).length, total: draft.input.items.length }) }}</span><span v-if="draft.server" class="mt-1 block text-xs text-muted-foreground">{{ formatInstant(draft.server.updatedAt, preferences.timeZone, preferences.locale) }}</span></button>
    </template>
  </div>
</template>
