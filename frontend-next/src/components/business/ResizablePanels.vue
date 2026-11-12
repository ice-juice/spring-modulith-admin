<script setup lang="ts">
// Interaction reference: LeiBox elastic-panel-resize / Alvish. See docs/frontend-next/leibox-reference.
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { useI18n } from 'vue-i18n'
const props = withDefaults(defineProps<{ label: string; side?: 'left' | 'right'; initial?: number; min?: number; max?: number; mainMin?: number; breakpoint?: number; enabled?: boolean }>(), {
  side: 'left', initial: 280, min: 220, max: 420, mainMin: 400, breakpoint: 760, enabled: true,
})
const { t } = useI18n(), id = useId(), root = ref<HTMLElement>(), handle = ref<HTMLButtonElement>()
const available = ref(0), preferred = ref(props.initial), dragging = ref(false), bend = ref(0)
const stacked = computed(() => !props.enabled || available.value < Math.max(props.breakpoint, props.min + props.mainMin + 24))
const upper = computed(() => Math.max(props.min, Math.min(props.max, available.value - props.mainMin - 24)))
const width = computed(() => Math.round(Math.max(props.min, Math.min(upper.value, preferred.value))))
const columns = computed(() => stacked.value ? 'minmax(0,1fr)' : props.side === 'left' ? `${width.value}px 24px minmax(0,1fr)` : `minmax(0,1fr) 24px ${width.value}px`)
let observer: ResizeObserver | undefined, pointer: number | undefined, startX = 0, startWidth = 0, frame = 0
function resize(value: number) { preferred.value = Math.max(props.min, Math.min(upper.value, value)) }
function finish(cancel = false) {
  if (cancel && dragging.value) preferred.value = startWidth
  const captured = pointer; pointer = undefined; dragging.value = false; bend.value = 0
  if (captured !== undefined && handle.value?.hasPointerCapture(captured)) handle.value.releasePointerCapture(captured)
}
function down(event: PointerEvent) {
  if (event.button !== 0 || stacked.value || pointer !== undefined) return
  event.preventDefault(); handle.value?.focus()
  pointer = event.pointerId; startX = event.clientX; startWidth = width.value; dragging.value = true
  handle.value?.setPointerCapture(pointer)
}
function move(event: PointerEvent) {
  if (event.pointerId !== pointer) return
  const delta = (event.clientX - startX) * (props.side === 'left' ? 1 : -1), desired = startWidth + delta
  resize(desired); bend.value = Math.max(-6, Math.min(6, (desired - width.value) / 6))
}
function keyboard(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End', 'Escape'].includes(event.key)) return
  event.preventDefault(); event.stopPropagation()
  if (event.key === 'Escape') { finish(); preferred.value = props.initial; return }
  const delta = (event.key === 'ArrowRight' ? 1 : -1) * (props.side === 'left' ? 1 : -1) * (event.shiftKey ? 32 : 16)
  resize(event.key === 'Home' ? props.min : event.key === 'End' ? upper.value : width.value + delta)
}
watch(stacked, value => { if (value) finish() })
onMounted(() => {
  observer = new ResizeObserver(entries => {
    const measured = entries[0]?.contentRect.width || 0
    if (measured === available.value) return
    cancelAnimationFrame(frame)
    frame = requestAnimationFrame(() => { available.value = measured })
  })
  if (root.value) observer.observe(root.value)
})
onBeforeUnmount(() => { finish(); observer?.disconnect(); cancelAnimationFrame(frame) })
</script>
<template>
  <div ref="root" class="resizable-panels min-w-0" :class="{ 'is-stacked': stacked, 'panel-right': side === 'right' }" :style="{ gridTemplateColumns: columns }" data-resizable-panels>
    <div :id="id" class="resize-panel min-w-0"><slot name="panel" /></div>
    <button v-show="!stacked" ref="handle" type="button" role="separator" class="resize-handle" :class="{ dragging }" :aria-label="t('common.resizePanels.handle', { panel: label })" aria-orientation="vertical" :aria-controls="id" :aria-valuemin="min" :aria-valuemax="upper" :aria-valuenow="width" :aria-valuetext="t('common.resizePanels.width', { width })" @pointerdown="down" @pointermove="move" @pointerup="finish()" @pointercancel="finish(true)" @lostpointercapture="finish()" @keydown="keyboard" @dblclick="preferred = initial"><span aria-hidden="true" :style="{ transform: `translateX(${bend}px)` }" /></button>
    <div class="resize-main min-w-0"><slot /></div>
  </div>
</template>
<style scoped>
.resizable-panels { display: grid; align-items: start; }
.resize-panel { grid-column: 1; grid-row: 1; }
.resize-main { grid-column: 3; grid-row: 1; }
.panel-right .resize-panel { grid-column: 3; }
.panel-right .resize-main { grid-column: 1; }
.resize-handle { grid-column: 2; grid-row: 1; align-self: stretch; min-height: 100px; display: grid; place-items: center; cursor: col-resize; touch-action: none; border-radius: 8px; }
.resize-handle span { width: 3px; height: 44px; border-radius: 3px; background: var(--border); transition: background 150ms, transform 180ms ease-out; }
.resize-handle:hover span,.resize-handle:focus-visible span,.resize-handle.dragging span { background: var(--primary); }
.resize-handle:focus-visible { outline: 2px solid var(--ring); outline-offset: -3px; }
.resize-handle.dragging span { transition: background 150ms; }
.is-stacked { gap: 16px; }
.is-stacked .resize-panel,.is-stacked .resize-main { grid-column: 1; grid-row: auto; }
.is-stacked.panel-right .resize-main { grid-row: 1; }
.is-stacked.panel-right .resize-panel { grid-row: 2; }
@media (prefers-reduced-motion: reduce) { .resize-handle span { transition: none; transform: none !important; } }
</style>
