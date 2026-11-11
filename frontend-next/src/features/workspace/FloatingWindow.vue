<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Grip, Minimize2, Maximize2, X, RotateCcw, Move } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { clampRect, type FloatRect } from './geometry'
const props = withDefaults(defineProps<{ storageKey: string; title: string; index?: number; color?: string }>(), { index: 0, color: 'neutral' })
const emit = defineEmits<{ close: [] }>()
const { t } = useI18n()
const rect = ref<FloatRect>(clampRect({}, window.innerWidth, window.innerHeight))
let drag: { x: number; y: number; rect: FloatRect; resize: boolean } | undefined
const style = computed(() => ({ left: rect.value.x + 'px', top: rect.value.y + 'px', width: rect.value.width + 'px', height: (rect.value.collapsed ? 48 : rect.value.height) + 'px' }))
function reset() { rect.value = clampRect({ x: window.innerWidth - 360 - props.index * 30, y: 96 + props.index * 48 }, innerWidth, innerHeight); persist() }
function persist() { if (!props.storageKey) return; try { localStorage.setItem(props.storageKey, JSON.stringify(rect.value)) } catch { /* Layout storage is optional. */ } }
function restore() {
  let saved: Partial<FloatRect> = { x: innerWidth - 360 - props.index * 30, y: 96 + props.index * 48 }
  try { const value: unknown = JSON.parse(localStorage.getItem(props.storageKey) || 'null'); if (value && typeof value === 'object' && !Array.isArray(value)) saved = value } catch { /* Ignore corrupt geometry. */ }
  rect.value = clampRect(saved, innerWidth, innerHeight)
}
function down(event: PointerEvent, resize = false) { if (event.button !== 0) return; event.preventDefault(); (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId); drag = { x: event.clientX, y: event.clientY, rect: { ...rect.value }, resize } }
function move(event: PointerEvent) { if (!drag) return; const x = event.clientX - drag.x, y = event.clientY - drag.y; rect.value = clampRect(drag.resize ? { ...drag.rect, width: drag.rect.width + x, height: drag.rect.height + y } : { ...drag.rect, x: drag.rect.x + x, y: drag.rect.y + y }, innerWidth, innerHeight) }
function end() { if (!drag) return; if (!drag.resize) rect.value.x = rect.value.x + rect.value.width / 2 < innerWidth / 2 ? 8 : innerWidth - rect.value.width - 8; drag = undefined; persist() }
function key(event: KeyboardEvent, resize = false) {
  const axis = { ArrowLeft: [-16, 0], ArrowRight: [16, 0], ArrowUp: [0, -16], ArrowDown: [0, 16] }[event.key]
  if (!axis) return; event.preventDefault()
  rect.value = clampRect(resize ? { ...rect.value, width: rect.value.width + axis[0]!, height: rect.value.height + axis[1]! } : { ...rect.value, x: rect.value.x + axis[0]!, y: rect.value.y + axis[1]! }, innerWidth, innerHeight); persist()
}
function collapse() { rect.value = clampRect({ ...rect.value, collapsed: !rect.value.collapsed }, innerWidth, innerHeight); persist() }
function fit() { rect.value = clampRect(rect.value, innerWidth, innerHeight) }
watch(() => props.storageKey, restore, { immediate: true })
onMounted(() => { window.addEventListener('pointermove', move); window.addEventListener('pointerup', end); window.addEventListener('pointercancel', end); window.addEventListener('resize', fit) })
onUnmounted(() => { window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', end); window.removeEventListener('pointercancel', end); window.removeEventListener('resize', fit) })
</script>
<template>
  <section role="region" :aria-label="title" class="shelf-float fixed z-30 flex flex-col overflow-hidden rounded-xl border bg-card text-card-foreground shadow-[var(--shadow-floating)]" :style="style" :data-note-color="color" data-shelf-floating>
    <header class="flex h-12 shrink-0 items-center gap-1 border-b px-2">
      <Button variant="ghost" size="icon-sm" class="touch-none cursor-move" :aria-label="t('common.shelf.moveWindow')" @pointerdown="down($event)" @keydown="key($event)"><Move class="size-4" /></Button>
      <span class="min-w-0 flex-1 truncate text-sm font-medium" :title="title">{{ title }}</span>
      <Button variant="ghost" size="icon-sm" :aria-label="t('common.shelf.resetPosition')" @click="reset"><RotateCcw class="size-3.5" /></Button>
      <Button variant="ghost" size="icon-sm" :aria-label="t(rect.collapsed ? 'common.shelf.expandWindow' : 'common.shelf.collapseWindow')" @click="collapse"><Maximize2 v-if="rect.collapsed" class="size-3.5" /><Minimize2 v-else class="size-3.5" /></Button>
      <Button variant="ghost" size="icon-sm" :aria-label="t('common.shelf.closeFloat')" @click="emit('close')"><X class="size-4" /></Button>
    </header>
    <div v-show="!rect.collapsed" class="min-h-0 flex-1 overflow-y-auto p-3"><slot /></div>
    <Button v-if="!rect.collapsed" variant="ghost" size="icon-sm" class="absolute right-0 bottom-0 touch-none cursor-se-resize" :aria-label="t('common.shelf.resizeWindow')" @pointerdown="down($event, true)" @keydown="key($event, true)"><Grip class="size-3" /></Button>
  </section>
</template>
<style>
[data-note-color="purple"] { border-inline-start: 3px solid var(--primary); }
[data-note-color="green"] { border-inline-start: 3px solid var(--success); }
[data-note-color="amber"] { border-inline-start: 3px solid var(--warning); }
</style>
