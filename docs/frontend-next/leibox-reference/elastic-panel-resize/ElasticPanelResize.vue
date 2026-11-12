<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from "vue";
import { mountElasticPanelResize, type ElasticPanelResizeOptions } from "./ElasticPanelResizeEngine.js";
import type { MotionController } from "./motion-runtime";
const props = defineProps<{ options?: ElasticPanelResizeOptions }>();
const host = ref<HTMLDivElement | null>(null);
let controller: MotionController | undefined;
onMounted(() => { controller = mountElasticPanelResize(host.value!, props.options); });
watch(() => props.options, value => controller?.update(value ?? {}), { deep: true });
onBeforeUnmount(() => controller?.destroy());
</script>

<template><div ref="host" data-motion-component="elastic-panel-resize" /></template>
