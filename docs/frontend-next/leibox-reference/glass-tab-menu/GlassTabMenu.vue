<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from "vue";
import { mountGlassTabMenu, type GlassTabMenuOptions } from "./GlassTabMenuEngine.js";
import type { MotionController } from "./motion-runtime";
const props = defineProps<{ options?: GlassTabMenuOptions }>();
const host = ref<HTMLDivElement | null>(null);
let controller: MotionController | undefined;
onMounted(() => { controller = mountGlassTabMenu(host.value!, props.options); });
watch(() => props.options, value => controller?.update(value ?? {}), { deep: true });
onBeforeUnmount(() => controller?.destroy());
</script>

<template><div ref="host" data-motion-component="glass-tab-menu" /></template>
