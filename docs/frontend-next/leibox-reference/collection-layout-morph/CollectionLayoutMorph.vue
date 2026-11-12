<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from "vue";
import { mountCollectionLayoutMorph, type CollectionLayoutMorphOptions } from "./CollectionLayoutMorphEngine.js";
import type { MotionController } from "./motion-runtime";
const props = defineProps<{ options?: CollectionLayoutMorphOptions }>();
const host = ref<HTMLDivElement | null>(null);
let controller: MotionController | undefined;
onMounted(() => { controller = mountCollectionLayoutMorph(host.value!, props.options); });
watch(() => props.options, value => controller?.update(value ?? {}), { deep: true });
onBeforeUnmount(() => controller?.destroy());
</script>

<template><div ref="host" data-motion-component="collection-layout-morph" /></template>
