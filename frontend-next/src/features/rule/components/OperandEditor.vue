<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { computed, inject } from "vue";
import { ruleReferencesKey } from "../references";
import JsonField from "./JsonField.vue";
import type { Operand } from "../types";
const operand = defineModel<Operand>({ required: true });
defineProps<{ label: string; readonly?: boolean }>();
const { t } = useI18n();
const references = inject(
  ruleReferencesKey,
  computed(() => []),
);
const fields = computed(() =>
  references.value.filter(
    (entry) =>
      entry.kind === "field" &&
      entry.label.startsWith(operand.value.source + "."),
  ),
);
</script>
<template>
  <fieldset class="min-w-0 space-y-2">
    <legend class="mb-1 text-xs font-medium">{{ label }}</legend>
    <select
      v-if="operand.source !== 'literal' && fields.length"
      :disabled="readonly"
      :value="operand.source + '.' + operand.path"
      :aria-label="label + ' · ' + t('rule.selectSource')"
      class="h-9 w-full min-w-0 max-w-full rounded-md border bg-card px-2 text-xs"
      @change="
        operand.path = ($event.target as HTMLSelectElement).value.slice(
          operand.source.length + 1,
        )
      "
    >
      <option value="">{{ t("rule.choose") }}</option>
      <option v-for="field in fields" :key="field.label" :value="field.label">
        {{ field.label }}{{ t("rule.separator") }}{{ field.detail }}
      </option>
    </select>
    <div class="grid grid-cols-[96px_minmax(0,1fr)] gap-2">
      <select
        v-model="operand.source"
        :aria-label="label + ' · ' + t('rule.source')"
        :disabled="readonly"
        class="h-9 min-w-0 w-full rounded-md border bg-card px-2"
      >
        <option value="input">{{ t("rule.input") }}</option>
        <option value="params">{{ t("rule.params") }}</option>
        <option value="context">{{ t("rule.path") }}</option>
        <option value="literal">{{ t("rule.literal") }}</option></select
      ><input
        v-if="operand.source !== 'literal'"
        v-model="operand.path"
        :readonly="readonly"
        :aria-label="label + ' · ' + t('rule.path')"
        class="h-9 min-w-0 flex-1 rounded-md border bg-card px-2"
      />
    </div>
    <JsonField
      v-if="operand.source === 'literal'"
      v-model="operand.value"
      :readonly="readonly"
      :label="label + ' · ' + t('rule.value')"
      :rows="2"
    />
  </fieldset>
</template>
