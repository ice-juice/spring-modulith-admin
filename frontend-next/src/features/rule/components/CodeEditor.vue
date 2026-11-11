<script setup lang="ts">
import { defineAsyncComponent } from "vue";
const editor = defineAsyncComponent(() => import("./MonacoEditor.vue"));
defineProps<{
  label: string;
  language?: "rule" | "json";
  readonly?: boolean;
  line?: number | null;
  column?: number | null;
}>();
const value = defineModel<string>({ required: true });
</script>
<template>
  <div class="min-w-0">
    <component
      :is="editor"
      v-model="value"
      :label="label"
      :language="language"
      :readonly="readonly"
      :line="line"
      :column="column"
    />
  </div>
</template>
