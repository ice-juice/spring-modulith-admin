<script setup lang="ts">
import { computed, inject } from "vue";
import { useI18n } from "vue-i18n";
import { ruleReferencesKey } from "../references";
const value = defineModel<string>({ required: true });
defineProps<{ label: string; readonly?: boolean }>();
const { t } = useI18n(),
  references = inject(
    ruleReferencesKey,
    computed(() => []),
  );
const fields = computed(() =>
  references.value.filter((entry) => entry.kind === "field"),
);
</script>
<template>
  <label class="grid min-w-0 gap-1 text-xs"
    >{{ label }}
    <select
      :value="fields.some((entry) => entry.label === value) ? value : ''"
      :disabled="readonly"
      :aria-label="label + ' · ' + t('rule.selectSource')"
      class="h-9 min-w-0 max-w-full rounded-md border bg-card px-2"
      @change="
        ($event.target as HTMLSelectElement).value &&
        (value = ($event.target as HTMLSelectElement).value)
      "
    >
      <option value="">{{ t("rule.customExpression") }}</option>
      <option v-for="entry in fields" :key="entry.label" :value="entry.label">
        {{ entry.label }}{{ t("rule.separator") }}{{ entry.detail }}
      </option>
    </select>
    <input
      v-model="value"
      :readonly="readonly"
      :aria-label="label"
      class="h-9 min-w-0 rounded-md border bg-card px-2 font-mono text-xs"
      spellcheck="false"
    />
  </label>
</template>
