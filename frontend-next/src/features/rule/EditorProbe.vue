<script setup lang="ts">
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import CodeEditor from "./components/CodeEditor.vue";
import FlowCanvas from "./components/FlowCanvas.vue";
import { newFlow } from "./flow";
const { t } = useI18n(),
  source = ref("round(input.amount * params.ratio, 2)"),
  flow = ref(newFlow());
</script>
<template>
  <main class="space-y-4 p-6">
    <h1>{{ t("rule.editorTools") }}</h1>
    <CodeEditor v-model="source" :label="t('rule.expression')" /><FlowCanvas
      v-model="flow"
    />
  </main>
</template>
