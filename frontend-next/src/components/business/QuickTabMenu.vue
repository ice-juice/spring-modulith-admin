<script setup lang="ts">
// Interaction reference: LeiBox glass-tab-menu / solutionb. See docs/frontend-next/leibox-reference.
import { nextTick, ref, useId } from 'vue'
import { Plus } from '@lucide/vue'
import { Button } from '@/components/ui/button'
const props = defineProps<{ modelValue: string; tabs: { value: string; label: string }[]; label: string; expandLabel: string; collapseLabel: string; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: string]; expand: [] }>()
const open = ref(false), root = ref<HTMLElement>(), menuId = useId()
async function toggle() {
  open.value = !open.value
  if (open.value) { emit('expand'); await nextTick() }
}
function close() { open.value = false; root.value?.querySelector<HTMLButtonElement>('[data-quick-toggle]')?.focus() }
function escape(event: KeyboardEvent) { if (event.key === 'Escape' && open.value) { event.stopPropagation(); event.preventDefault(); close() } }
function keys(event: KeyboardEvent, index: number) {
  const length = props.tabs.length
  if (!length || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? length - 1 : (index + (event.key === 'ArrowRight' ? 1 : -1) + length) % length
  emit('update:modelValue', props.tabs[next]!.value)
  root.value?.querySelectorAll<HTMLButtonElement>('[data-quick-tab]')[next]?.focus()
}
defineExpose({ close })
</script>
<template>
  <div ref="root" class="quick-tab-menu" @keydown="escape">
    <div class="flex min-w-0 items-center gap-1 rounded-xl border bg-muted/60 p-1" role="group" :aria-label="label">
      <Button v-for="(tab, index) in tabs" :key="tab.value" data-quick-tab type="button" :variant="modelValue === tab.value ? 'secondary' : 'ghost'" class="min-w-0 flex-1 whitespace-normal text-center" :class="modelValue === tab.value ? 'shadow-sm' : ''" :aria-pressed="modelValue === tab.value" @click="emit('update:modelValue', tab.value)" @keydown="keys($event, index)">{{ tab.label }}</Button>
      <Button type="button" variant="ghost" size="icon-sm" class="shrink-0" data-quick-toggle :disabled="disabled" :aria-label="open ? collapseLabel : expandLabel" :aria-expanded="open" :aria-controls="menuId" @click="toggle"><Plus class="size-4 transition-transform motion-reduce:transition-none" :class="open ? 'rotate-45' : ''" /></Button>
    </div>
    <Transition name="quick-menu">
      <div v-if="open" :id="menuId" class="mt-2 max-h-64 overflow-y-auto rounded-xl border bg-card p-2" :aria-label="expandLabel"><slot name="actions" :close="close" /></div>
    </Transition>
  </div>
</template>
<style scoped>
.quick-menu-enter-active,.quick-menu-leave-active { transition: transform 180ms ease, opacity 180ms ease; transform-origin: top; }
.quick-menu-enter-from,.quick-menu-leave-to { transform: translateY(-6px); opacity: 0; }
@media (prefers-reduced-motion: reduce) { .quick-menu-enter-active,.quick-menu-leave-active { transition: none; } }
</style>
