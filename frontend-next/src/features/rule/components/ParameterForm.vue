<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { exactNumber, parseExact, printExact } from "../exact";
import type { RuleDocument, Field } from "../types";
import JsonField from "./JsonField.vue";
const props = defineProps<{ document: RuleDocument; readonly?: boolean }>(),
  values = defineModel<Record<string, unknown>>({ required: true }),
  { t } = useI18n();
function change(key: string, field: Field, event: Event) {
  const element = event.target as HTMLInputElement;
  try {
    const text = element.value;
    const next = !text
      ? null
      : field.enumValues?.length || field.type === "BOOLEAN"
        ? parseExact(text)
        : ["DECIMAL", "INTEGER"].includes(field.type)
          ? exactNumber(text)
          : text;
    if (field.type === "INTEGER" && next !== null && !/^-?\d+$/.test(text))
      throw new Error("INTEGER_REQUIRED");
    if (
      field.type === "INSTANT" &&
      text &&
      (!/(Z|[+-]\d\d:\d\d)$/.test(text) || Number.isNaN(Date.parse(text)))
    )
      throw new Error("INSTANT_REQUIRED");
    element.setCustomValidity("");
    values.value = { ...values.value, [key]: next };
  } catch {
    element.setCustomValidity(t("rule.invalidJson"));
    element.reportValidity();
  }
}
function label(key: string) {
  return props.document.parameterUi?.[key]?.label || key;
}
</script>
<template>
  <section class="space-y-4">
    <div
      v-for="(field, key) in document.parameterSchema.fields"
      :key="key"
      class="space-y-1"
    >
      <template v-if="['OBJECT', 'LIST'].includes(field.type)"
        ><JsonField
          :model-value="values[key]"
          :label="label(key)"
          :readonly="readonly"
          @update:model-value="
            values = { ...values, [key]: $event }
          " /></template
      ><label v-else class="grid gap-2 text-sm"
        ><span
          >{{ label(key)
          }}<span v-if="field.required" class="ml-1 text-destructive">{{
            t("rule.required")
          }}</span></span
        ><select
          v-if="field.enumValues?.length || field.type === 'BOOLEAN'"
          :value="
            values[key] === null || values[key] === undefined
              ? ''
              : printExact(values[key])
          "
          :required="field.required"
          :disabled="readonly"
          class="h-10 w-full rounded-lg border bg-card px-3"
          @change="change(key, field, $event)"
        >
          <option value="">{{ t("rule.choose") }}</option>
          <option
            v-for="option in field.enumValues?.length
              ? field.enumValues
              : [true, false]"
            :key="printExact(option)"
            :value="printExact(option)"
          >
            {{ String(option) }}
          </option></select
        ><input
          v-else
          :value="
            values[key] === null || values[key] === undefined
              ? ''
              : String(values[key])
          "
          :required="field.required"
          :readonly="readonly"
          :maxlength="field.maxLength || undefined"
          :type="field.sensitive ? 'password' : 'text'"
          :inputmode="
            ['INTEGER', 'DECIMAL'].includes(field.type) ? 'decimal' : undefined
          "
          autocomplete="off"
          class="h-10 min-w-0 rounded-lg border bg-card px-3"
          @input="change(key, field, $event)"
      /></label>
      <p
        v-if="document.parameterUi?.[key]?.help"
        class="text-xs text-muted-foreground"
      >
        {{ document.parameterUi[key]?.help }}
      </p>
      <p
        v-if="field.minimum !== undefined || field.maximum !== undefined"
        class="text-xs text-muted-foreground"
      >
        {{ key }}{{ t("rule.separator") }}{{ printExact(field.minimum)
        }}{{ t("rule.separator") }}{{ printExact(field.maximum) }}
      </p>
    </div>
  </section>
</template>
