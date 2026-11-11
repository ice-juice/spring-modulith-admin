<script setup lang="ts">
import { useI18n } from "vue-i18n";
import RuleSourceInput from "./RuleSourceInput.vue";
import type { Schema } from "../types";
defineProps<{ schema: Schema; label: string; readonly?: boolean }>();
const bindings = defineModel<Record<string, string>>({ required: true });
const { t } = useI18n();
function set(key: string, value: string) {
  const next = { ...bindings.value };
  if (value.trim()) next[key] = value;
  else delete next[key];
  bindings.value = next;
}
</script>
<template>
  <section class="min-w-0 space-y-3 rounded-lg border p-3">
    <h4 class="text-xs font-semibold">{{ label }}</h4>
    <p
      v-if="!Object.keys(schema.fields).length"
      class="text-xs text-muted-foreground"
    >
      {{ t("rule.noMappings") }}
    </p>
    <div
      v-for="(field, key) in schema.fields"
      :key="key"
      class="min-w-0 space-y-1"
    >
      <RuleSourceInput
        :model-value="bindings[key] || ''"
        :label="label + ' · ' + key"
        :readonly="readonly"
        @update:model-value="set(String(key), $event)"
      />
      <p class="text-xs text-muted-foreground">
        {{ field.type }}{{ t("rule.separator")
        }}{{
          t(
            field.required && field.defaultValue == null
              ? "rule.bindingRequired"
              : "rule.bindingOptional",
          )
        }}
      </p>
    </div>
  </section>
</template>
