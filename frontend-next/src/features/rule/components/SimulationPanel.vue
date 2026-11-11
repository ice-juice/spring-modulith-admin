<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { Button } from "@/components/ui/button";
import ErrorNotice from "@/components/business/ErrorNotice.vue";
import JsonField from "./JsonField.vue";
import ExecutionView from "./ExecutionView.vue";
import { ruleWrite } from "../api";
import { valuesFor } from "../model";
import { useRuleTask } from "../useRuleTask";
import { cloneExact, printExact } from "../exact";
import type { RuleDocument, Execution, Problem } from "../types";
const props = defineProps<{ document: RuleDocument; editVersion?: number }>(),
  emit = defineEmits<{
    locate: [path: string, line?: number | null, column?: number | null];
    result: [result: Execution];
    reset: [];
  }>(),
  { t } = useI18n();
const task = useRuleTask(),
  { busy, error } = task,
  input = ref<unknown>(valuesFor(props.document.inputSchema)),
  params = ref<unknown>(valuesFor(props.document.parameterSchema)),
  result = ref<Execution>(),
  form = ref<HTMLFormElement>(),
  valid = ref(false),
  stale = ref(false),
  problems = ref<Problem[]>([]);
const selectedExample = ref("");
function loadExample() {
  const example = props.document.examples[Number(selectedExample.value)];
  if (selectedExample.value === "" || !example || busy.value) return;
  input.value = cloneExact(example.input);
  params.value = cloneExact(example.params);
  invalidate();
}
let generation = 0;
function invalidate() {
  generation++;
  stale.value ||=
    !!result.value || valid.value || !!problems.value.length || busy.value;
  result.value = undefined;
  valid.value = false;
  problems.value = [];
  error.value = "";
  emit("reset");
}
const source = computed(() =>
  printExact({
    document: props.document,
    input: input.value,
    params: params.value,
    editVersion: props.editVersion,
  }),
);
watch(source, invalidate, { flush: "sync" });
async function run(validate = false) {
  if (busy.value || !form.value?.reportValidity()) return;
  invalidate();
  stale.value = false;
  const requestGeneration = generation;
  const payload = cloneExact({
    document: props.document,
    input: input.value,
    params: params.value,
  });
  if (validate)
    await task.run(
      () =>
        ruleWrite<{ valid: boolean; problems: Problem[] }>(
          "/validate",
          payload,
        ),
      (value) => {
        if (generation !== requestGeneration) return;
        valid.value = value.valid;
        problems.value = value.problems;
      },
      false,
      () => generation === requestGeneration,
    );
  else
    await task.run(
      () => ruleWrite<Execution>("/simulations", payload),
      (value) => {
        if (generation !== requestGeneration) return;
        result.value = value;
        emit("result", value);
      },
      false,
      () => generation === requestGeneration,
    );
}
</script>
<template>
  <aside class="min-w-0 space-y-4">
    <form
      ref="form"
      class="space-y-4 rounded-xl border bg-card p-4"
      @submit.prevent="run()"
    >
      <h2 class="text-sm font-semibold">{{ t("rule.simulation") }}</h2>
      <div v-if="document.examples.length" class="grid gap-2">
        <label class="grid gap-1 text-xs"
          >{{ t("rule.sampleInput")
          }}<select
            v-model="selectedExample"
            :aria-label="t('rule.sampleInput')"
            :disabled="busy"
            class="h-9 min-w-0 rounded-md border bg-card px-2"
          >
            <option value="">{{ t("rule.choose") }}</option>
            <option
              v-for="(example, index) in document.examples"
              :key="index"
              :value="String(index)"
            >
              {{ example.name }}
            </option>
          </select></label
        >
        <Button
          type="button"
          size="sm"
          variant="outline"
          :disabled="busy || selectedExample === ''"
          @click="loadExample"
          >{{ t("rule.loadExample") }}</Button
        >
      </div>
      <JsonField
        v-model="input"
        :label="t('rule.input')"
        :rows="5"
        @edit="invalidate"
      /><JsonField
        v-model="params"
        :label="t('rule.params')"
        :rows="5"
        @edit="invalidate"
      />
      <div class="flex flex-wrap gap-2">
        <Button
          type="button"
          variant="outline"
          :disabled="busy"
          @click="run(true)"
          >{{ t("rule.validate") }}</Button
        ><Button :disabled="busy">{{ t("rule.simulate") }}</Button>
      </div>
      <ErrorNotice :message="error" />
      <p v-if="stale" role="status" class="text-sm text-muted-foreground">
        {{ t("rule.simulationStale") }}
      </p>
      <p v-if="valid" role="status" class="text-sm text-success">
        {{ t("rule.valid") }}
      </p>
      <button
        v-for="problem in problems"
        :key="problem.path + problem.code"
        type="button"
        class="block break-all text-left text-xs text-destructive"
        @click="emit('locate', problem.path, problem.line, problem.column)"
      >
        {{ problem.path }}{{ t("rule.separator") }}{{ problem.code }}
      </button>
    </form>
    <ExecutionView
      v-if="result"
      :result="result"
      @locate="(path, line, column) => emit('locate', path, line, column)"
    />
  </aside>
</template>
