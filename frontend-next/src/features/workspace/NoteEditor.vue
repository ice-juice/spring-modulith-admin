<script setup lang="ts">
import { computed, inject, ref, useId } from 'vue'
import { X } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import OperationStatus from '@/components/business/OperationStatus.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
import { safeLocalPath } from '@/lib/web-addresses'
import { notify } from '@/lib/feedback'
import { useShelf } from './shelf'
import { noteInput, useShelfNotes, type NoteInput } from './notes'
import { shelfNavigate } from './navigation'
const props = defineProps<{ noteKey: string }>()
const { t } = useI18n(), notes = useShelfNotes(), shelf = useShelf(), router = useRouter()
const navigatePage = inject(shelfNavigate, path => router.push(path))
const editorId = useId()
const hideDone = ref(false)
const d = computed(() => notes.entries[props.noteKey])
const operation = computed(() => notes.operation(props.noteKey))
const readonly = computed(() => d.value?.transitioning || d.value?.server && d.value.server.status !== 'active')
const changed = computed(() => d.value && JSON.stringify(noteInput(d.value.input)) !== d.value.baseline)
function edit(values: Partial<NoteInput>) { if (d.value) notes.edit(props.noteKey, { ...noteInput(d.value.input), ...values }) }
function item(index: number, values: Partial<NoteInput['items'][number]>) { if (d.value) edit({ items: d.value.input.items.map((it, i) => i === index ? { ...it, ...values } : { ...it }) }) }
function addItem() { if (d.value && d.value.input.items.length < 100) edit({ items: [...d.value.input.items, { id: crypto.randomUUID(), text: '', done: false }] }) }
async function copy() {
  if (!d.value) return
  try { await navigator.clipboard.writeText(d.value.input.title + '\n' + d.value.input.content + '\n' + d.value.input.items.map(it => (it.done ? '[x] ' : '[ ] ') + it.text).join('\n')); notify(t('common.shelf.copied')) }
  catch { notify(t('common.shelf.copyFailed'), 'error') }
}
async function related() {
  await shelf.load()
  const target = shelf.board.targets.find(v => v.id === d.value?.input.relatedMenuId), path = safeLocalPath(target?.path)
  if (path) { const failed = await navigatePage(path); if (!failed && shelf.board.mode === 'drawer') shelf.open = false }
}
</script>
<template>
  <div v-if="d" class="space-y-3" data-note-editor>
    <div class="flex flex-wrap items-center justify-between gap-2">
      <span class="text-xs text-muted-foreground" role="status" aria-live="polite">{{ t(d.saving ? 'common.shelf.saving' : d.error || d.conflict || d.uncertain ? 'common.shelf.notSaved' : changed ? 'common.shelf.unsaved' : 'common.shelf.saved') }}</span>
      <Button variant="ghost" size="sm" @click="copy">{{ t('common.shelf.copy') }}</Button>
    </div>
    <ErrorNotice :message="d.error" /><OperationStatus :slow="operation.slow.value" :unresolved="operation.unresolved.value || d.uncertain" :querying="operation.querying.value || d.saving" queryable @check="notes.save(noteKey, true)" />
    <div v-if="d.conflict" class="space-y-2 rounded-lg border border-[var(--warning)] p-3 text-sm" role="alert"><p>{{ t('common.shelf.conflict') }}</p><details><summary>{{ t('common.shelf.remotePreview') }}</summary><pre class="mt-2 max-h-40 overflow-auto whitespace-pre-wrap break-words">{{ d.conflict.title + '\n' + d.conflict.content + '\n' + d.conflict.items.map(i => (i.done ? '[x] ' : '[ ] ') + i.text).join('\n') }}</pre></details><div class="flex flex-wrap gap-2"><Button size="sm" variant="outline" @click="notes.useRemote(noteKey)">{{ t('common.shelf.useRemote') }}</Button><Button size="sm" @click="notes.copyDraft(noteKey)">{{ t('common.shelf.saveCopy') }}</Button></div></div>
    <div class="space-y-1"><Label :for="'note-title-' + editorId">{{ t('common.shelf.noteTitle') }}</Label><Input :id="'note-title-' + editorId" :model-value="d.input.title" maxlength="50" :disabled="!!readonly" @update:model-value="edit({ title: String($event) })" /></div>
    <div class="flex flex-wrap gap-2">
      <label class="min-w-0 flex-1 space-y-1 text-xs">{{ t('common.shelf.noteType') }}<select class="h-9 w-full rounded-md border bg-background px-2 text-sm" :aria-label="t('common.shelf.noteType')" :value="d.input.kind" :disabled="!!readonly" @change="edit({ kind: ($event.target as HTMLSelectElement).value as NoteInput['kind'] })"><option value="text">{{ t('common.shelf.textNote') }}</option><option value="checklist">{{ t('common.shelf.checklist') }}</option></select></label>
      <label class="min-w-0 flex-1 space-y-1 text-xs">{{ t('common.shelf.color') }}<select class="h-9 w-full rounded-md border bg-background px-2 text-sm" :aria-label="t('common.shelf.color')" :value="d.input.color" :disabled="!!readonly" @change="edit({ color: ($event.target as HTMLSelectElement).value as NoteInput['color'] })"><option v-for="color in ['neutral', 'purple', 'green', 'amber']" :key="color" :value="color">{{ t('common.shelf.colors.' + color) }}</option></select></label>
    </div>
    <div class="space-y-1"><Label :for="'note-body-' + editorId">{{ t('common.shelf.noteBody') }}</Label><Textarea :id="'note-body-' + editorId" :model-value="d.input.content" :rows="6" maxlength="10000" :disabled="!!readonly" @update:model-value="edit({ content: String($event) })" /></div>
    <div v-if="d.input.kind === 'checklist'" class="space-y-2">
      <Button variant="ghost" size="sm" :aria-pressed="hideDone" @click="hideDone = !hideDone">{{ t(hideDone ? 'common.shelf.showDone' : 'common.shelf.hideDone') }}</Button><div v-for="(it, index) in d.input.items" v-show="!hideDone || !it.done" :key="it.id" class="flex items-start gap-2">
        <input type="checkbox" class="mt-3 size-4 shrink-0" :checked="it.done" :disabled="!!readonly" :aria-label="t('common.shelf.completeItem', { index: index + 1 })" @change="item(index, { done: ($event.target as HTMLInputElement).checked })" />
        <Input :model-value="it.text" maxlength="500" :disabled="!!readonly" :aria-label="t('common.shelf.itemText', { index: index + 1 })" :class="it.done ? 'line-through text-muted-foreground' : ''" @update:model-value="item(index, { text: String($event) })" />
        <Button variant="ghost" size="sm" :disabled="!!readonly" :aria-label="t('common.shelf.removeItem', { index: index + 1 })" @click="edit({ items: d.input.items.filter((_, i) => i !== index) })"><X class="size-3" /></Button>
      </div>
      <Button variant="outline" size="sm" :disabled="!!readonly || d.input.items.length >= 100" @click="addItem">{{ t('common.shelf.addItem') }}</Button>
    </div>
    <label class="flex items-center gap-2 text-sm"><input type="checkbox" :checked="d.input.pinned" :disabled="!!readonly" @change="edit({ pinned: ($event.target as HTMLInputElement).checked })" />{{ t('common.shelf.pinList') }}</label>
    <label class="block space-y-1 text-xs">{{ t('common.shelf.relatedPage') }}<select class="h-9 w-full min-w-0 rounded-md border bg-background px-2 text-sm" :aria-label="t('common.shelf.relatedPage')" :value="d.input.relatedMenuId || ''" :disabled="!!readonly" @change="edit({ relatedMenuId: ($event.target as HTMLSelectElement).value || null })"><option value="">{{ t('common.shelf.noRelated') }}</option><option v-if="d.input.relatedMenuId && !shelf.board.targets.some(v => v.id === d?.input.relatedMenuId)" :value="d.input.relatedMenuId">{{ t('common.shelf.unavailable') }}</option><option v-for="target in shelf.board.targets" :key="target.id" :value="target.id">{{ shelf.targetLabel(target) }}</option></select></label>
    <Button v-if="d.input.relatedMenuId" variant="ghost" size="sm" :disabled="!shelf.board.targets.some(v => v.id === d?.input.relatedMenuId)" @click="related">{{ t('common.shelf.openRelated') }}</Button>
    <div class="flex flex-wrap gap-2"><Button v-if="!readonly" size="sm" :disabled="d.saving || !!d.conflict" @click="notes.save(noteKey, true)">{{ t('common.shelf.saveNow') }}</Button><Button v-if="d.error && !d.conflict && !d.uncertain && !operation.unresolved.value" variant="outline" size="sm" :disabled="d.saving" @click="notes.save(noteKey, true)">{{ t('common.retry') }}</Button></div>
  </div>
</template>
