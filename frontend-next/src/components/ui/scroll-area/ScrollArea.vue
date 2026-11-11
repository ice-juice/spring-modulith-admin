<script setup lang="ts">
import { ref, type HTMLAttributes } from 'vue'
import { ScrollAreaRoot, ScrollAreaViewport, ScrollAreaScrollbar, ScrollAreaThumb, ScrollAreaCorner } from 'reka-ui'
import { cn } from '@/lib/utils'

defineOptions({ inheritAttrs: false })
const props = withDefaults(defineProps<{
  class?: HTMLAttributes['class']
  viewportClass?: HTMLAttributes['class']
  orientation?: 'vertical' | 'horizontal' | 'both'
  label: string
}>(), { orientation: 'vertical' })
const focusWithin = ref(false)
function blur(event: FocusEvent) {
  if (!(event.currentTarget as HTMLElement).contains(event.relatedTarget as Node | null)) focusWithin.value = false
}
</script>

<template>
  <ScrollAreaRoot v-bind="$attrs" data-slot="scroll-area" :type="focusWithin ? 'auto' : 'hover'" :scroll-hide-delay="450" :class="cn('relative min-h-0 min-w-0 overflow-hidden', props.class)" @focusin="focusWithin = true" @focusout="blur">
    <ScrollAreaViewport data-slot="scroll-area-viewport" role="region" :aria-label="label" :class="cn('size-full rounded-[inherit] outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring/50', viewportClass)">
      <slot />
    </ScrollAreaViewport>
    <ScrollAreaScrollbar v-if="orientation !== 'horizontal'" orientation="vertical" data-slot="scroll-area-scrollbar" class="product-scrollbar">
      <ScrollAreaThumb data-slot="scroll-area-thumb" class="product-scrollbar-thumb" />
    </ScrollAreaScrollbar>
    <ScrollAreaScrollbar v-if="orientation !== 'vertical'" orientation="horizontal" data-slot="scroll-area-scrollbar" class="product-scrollbar">
      <ScrollAreaThumb data-slot="scroll-area-thumb" class="product-scrollbar-thumb" />
    </ScrollAreaScrollbar>
    <ScrollAreaCorner class="bg-transparent" />
  </ScrollAreaRoot>
</template>
