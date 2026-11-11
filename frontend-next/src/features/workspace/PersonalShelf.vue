<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { X } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from '@/components/ui/sheet'
import { useAuth } from '@/stores/auth'
import { notify } from '@/lib/feedback'
import { useShelf, type ShelfBoard } from './shelf'
import { useShelfNotes } from './notes'
import ShelfContent from './ShelfContent.vue'
import NoteEditor from './NoteEditor.vue'
import FloatingWindow from './FloatingWindow.vue'
const emit = defineEmits<{ dock: [width: number] }>()
const auth = useAuth(), shelf = useShelf(), notes = useShelfNotes(), { t } = useI18n()
const width = ref(innerWidth), floatingKeys = ref<string[]>([]), restoredScope = ref('')
const visible = computed(() => !!auth.user && !auth.user.locked)
const actualMode = computed(() => width.value < 768 || shelf.board.mode === 'docked' && width.value < 1280 ? 'drawer' : shelf.board.mode)
const floating = computed(() => floatingKeys.value.filter(key => notes.entries[key] && (!notes.entries[key].server || notes.entries[key].server.status === 'active')))
let restoringScope = ''
const storage = computed(() => 'sma.shelf.layout.' + shelf.board.scope)
function beforeUnload(event: BeforeUnloadEvent) { if (notes.dirty) { event.preventDefault(); event.returnValue = '' } }
function resize() { width.value = innerWidth }
async function close(value: boolean) { if (!value && !await notes.flushAll()) return; shelf.open = value }
async function mode(value: ShelfBoard['mode']) { if (await notes.flushAll() && await shelf.mode(value)) shelf.open = true }
async function float(key: string) {
  if (floatingKeys.value.includes(key)) return
  if (floating.value.length >= 3) { notify(t('common.shelf.floatLimit'), 'info'); return }
  if (!await notes.save(key, true)) return
  floatingKeys.value = [...floating.value, key]
  if (width.value >= 768) shelf.open = false
  persist()
}
async function closeNote(key: string) { if (await notes.save(key, true)) { floatingKeys.value = floatingKeys.value.filter(k => k !== key); persist() } else { notes.selected = key; shelf.section = 'notes'; shelf.open = true } }
function persist() {
  if (!shelf.board.scope || restoringScope) return
  try { localStorage.setItem(storage.value, JSON.stringify({ floating: floatingKeys.value.slice(0, 3), section: shelf.section, open: shelf.open })) } catch { /* Optional layout storage. */ }
}
async function restore(scope: string) {
  if (!scope || scope === restoredScope.value) return
  restoredScope.value = scope; restoringScope = scope
  try {
    const saved = JSON.parse(localStorage.getItem('sma.shelf.layout.' + scope) || '{}') as { floating?: unknown; section?: string; open?: boolean }
    if (saved.section === 'notes') shelf.section = 'notes'
    if (saved.open === true && shelf.board.mode !== 'drawer') shelf.open = true
    if (Array.isArray(saved.floating)) {
      const keys = saved.floating.filter((key): key is string => typeof key === 'string' && /^[a-f0-9-]{36}$/.test(key)).slice(0, 3)
      if (keys.length && await notes.load() && scope === shelf.board.scope) floatingKeys.value = keys
    }
  } catch { /* Ignore invalid stored layout. */ } finally { if (restoringScope === scope) { restoringScope = ''; if (scope === shelf.board.scope) persist() } }
}
watch([() => shelf.identity, () => auth.user?.locked], () => {
  if (!auth.user) { floatingKeys.value = []; restoredScope.value = ''; return }
  if (!auth.user.locked) void shelf.load().then(() => restore(shelf.board.scope))
}, { immediate: true })
watch(() => shelf.identity, () => { floatingKeys.value = []; restoredScope.value = '' }, { flush: 'sync' })
watch([() => shelf.open, () => shelf.section], persist)
watch([actualMode, () => shelf.open, visible], () => emit('dock', visible.value && shelf.open && actualMode.value === 'docked' ? 380 : 0), { immediate: true })
onMounted(() => { window.addEventListener('beforeunload', beforeUnload); window.addEventListener('resize', resize) })
onUnmounted(() => { window.removeEventListener('beforeunload', beforeUnload); window.removeEventListener('resize', resize); emit('dock', 0) })
</script>
<template>
  <Teleport to="body">
    <template v-if="visible">
      <Sheet :open="shelf.open && actualMode === 'drawer'" @update:open="close"><SheetContent class="w-[min(400px,100vw)] max-w-full sm:max-w-[400px]"><SheetHeader class="border-b p-4 pr-12"><SheetTitle>{{ t('common.shelf.title') }}</SheetTitle><SheetDescription>{{ t('common.shelf.notes') }}</SheetDescription></SheetHeader><div class="min-h-0 flex-1 overflow-y-auto px-4 pb-4"><ShelfContent @mode="mode" @float="float" /></div></SheetContent></Sheet>
      <FloatingWindow v-if="shelf.open && actualMode === 'floating'" :title="t('common.shelf.title')" :storage-key="storage + '.panel'" @close="close(false)"><ShelfContent @mode="mode" @float="float" /></FloatingWindow>
      <section v-if="shelf.open && actualMode === 'docked'" role="region" :aria-label="t('common.shelf.title')" class="fixed inset-y-0 right-0 z-30 flex w-[380px] flex-col border-l bg-card shadow-lg" data-shelf-docked><header class="flex items-center justify-between border-b p-4"><h2 class="text-sm font-semibold">{{ t('common.shelf.title') }}</h2><Button variant="ghost" size="icon-sm" :aria-label="t('common.shelf.closeFloat')" @click="close(false)"><X class="size-4" /></Button></header><div class="min-h-0 flex-1 overflow-auto p-4"><ShelfContent @mode="mode" @float="float" /></div></section>
      <template v-if="width >= 768"><FloatingWindow v-for="(key, index) in floating" :key="key" :title="notes.entries[key]!.input.title" :index="index + 1" :color="notes.entries[key]!.input.color" :storage-key="storage + '.note.' + key" @close="closeNote(key)"><NoteEditor :note-key="key" /></FloatingWindow></template>
    </template>
  </Teleport>
</template>
