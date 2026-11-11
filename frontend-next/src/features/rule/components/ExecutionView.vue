<script setup lang="ts">
import { useI18n } from "vue-i18n";
import type { Execution } from "../types";
import { printExact } from "../exact";
defineProps<{ result: Execution }>();
const emit = defineEmits<{
    locate: [path: string, line?: number | null, column?: number | null];
  }>(),
  { t } = useI18n();
</script>
<template>
  <section class="space-y-3 rounded-xl border bg-card p-4" aria-live="polite">
    <div class="flex flex-wrap justify-between gap-2">
      <h3 class="font-semibold">{{ t("rule.result") }}</h3>
      <span
        class="rounded-md bg-accent px-2 py-1 text-xs text-accent-foreground"
        >{{ result.status }}</span
      >
    </div>
    <pre
      class="max-h-48 overflow-auto whitespace-pre-wrap break-words rounded-lg bg-muted/40 p-3 text-xs"
      >{{ printExact(result.output, true) }}</pre>
    <div v-if="result.problems?.length" class="space-y-2">
      <h4 class="text-sm font-medium">{{ t("rule.problems") }}</h4>
      <button
        v-for="(problem, index) in result.problems"
        :key="index"
        type="button"
        class="block text-left text-xs text-destructive underline"
        @click="emit('locate', problem.path, problem.line, problem.column)"
      >
        {{ problem.path }}{{ t("rule.separator") }}{{ problem.code
        }}<span v-if="problem.line"
          >{{ t("rule.separator") }}{{ t("rule.line") }}
          {{ problem.line }}</span
        >
      </button>
    </div>
    <details v-if="result.trace?.length">
      <summary class="cursor-pointer text-sm">{{ t("rule.trace") }}</summary>
      <ol class="mt-2 max-h-64 space-y-1 overflow-auto">
        <li v-for="(item, index) in result.trace" :key="index">
          <button
            type="button"
            class="text-left font-mono text-xs break-all"
            :class="
              item.evaluated ? 'text-foreground' : 'text-muted-foreground'
            "
            @click="emit('locate', item.id, item.line, item.column)"
          >
            {{ item.id }}{{ t("rule.separator") }}{{ item.outcome }}
          </button>
        </li>
      </ol>
    </details>
  </section>
</template>
