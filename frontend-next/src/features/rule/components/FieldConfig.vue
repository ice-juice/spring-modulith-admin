<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { Button } from "@/components/ui/button";
import JsonField from "./JsonField.vue";
import IdentifierInput from "./IdentifierInput.vue";
import ExactNumberInput from "./ExactNumberInput.vue";
import type { Field, FieldType } from "../types";
const field = defineModel<Field>({ required: true });
withDefaults(
  defineProps<{ label: string; readonly?: boolean; depth?: number }>(),
  { depth: 0 },
);
const { t } = useI18n();
const types: FieldType[] = [
  "STRING",
  "BOOLEAN",
  "INTEGER",
  "DECIMAL",
  "INSTANT",
  "OBJECT",
  "LIST",
];
function changeType() {
  field.value = {
    type: field.value.type,
    required: field.value.required,
    sensitive: field.value.sensitive,
    ...(field.value.type === "OBJECT" ? { properties: {} } : {}),
    ...(field.value.type === "LIST"
      ? { items: { type: "STRING", required: false } }
      : {}),
  };
}
function add() {
  const properties = { ...field.value.properties };
  if (Object.keys(properties).length >= 64) return;
  let index = 1;
  while (properties["field" + index]) index++;
  properties["field" + index] = { type: "STRING", required: false };
  field.value = { ...field.value, properties };
}
function rename(old: string, key: string) {
  if (
    !/^[a-zA-Z][a-zA-Z0-9_]{0,63}$/.test(key) ||
    (old !== key && Object.hasOwn(field.value.properties || {}, key))
  ) {
    return;
  }
  field.value.properties = Object.fromEntries(
    Object.entries(field.value.properties || {}).map(([name, child]) => [
      name === old ? key : name,
      child,
    ]),
  );
}
function remove(key: string) {
  const properties = { ...field.value.properties };
  delete properties[key];
  field.value.properties = properties;
}
</script>
<template>
  <fieldset class="min-w-0 space-y-2 rounded-lg border bg-card p-3">
    <legend class="px-1 text-xs font-medium">{{ label }}</legend>
    <div class="flex flex-wrap items-center gap-2">
      <select
        v-model="field.type"
        :disabled="readonly"
        :aria-label="label + ' · ' + t('rule.fieldType')"
        class="h-9 min-w-0 rounded-md border bg-card px-2 text-xs"
        @change="changeType"
      >
        <option v-for="type in types" :key="type">{{ type }}</option>
      </select>
      <label class="flex items-center gap-1 text-xs"
        ><input
          v-model="field.required"
          type="checkbox"
          :disabled="readonly"
        />{{ t("rule.required") }}</label
      >
    </div>
    <div
      v-if="['INTEGER', 'DECIMAL'].includes(field.type)"
      class="grid grid-cols-2 gap-2"
    >
      <label class="grid gap-1 text-xs"
        >{{ t("rule.minimum")
        }}<ExactNumberInput
          :value="field.minimum"
          :readonly="readonly"
          :label="label + ' · ' + t('rule.minimum')"
          @update="field.minimum = $event"
      /></label>
      <label class="grid gap-1 text-xs"
        >{{ t("rule.maximum")
        }}<ExactNumberInput
          :value="field.maximum"
          :readonly="readonly"
          :label="label + ' · ' + t('rule.maximum')"
          @update="field.maximum = $event"
      /></label>
      <label v-if="field.type === 'DECIMAL'" class="grid gap-1 text-xs"
        >{{ t("rule.scale")
        }}<input
          :value="field.scale ?? ''"
          type="number"
          min="0"
          max="18"
          :readonly="readonly"
          class="h-8 min-w-0 rounded-md border bg-card px-2"
          @input="
            field.scale =
              ($event.target as HTMLInputElement).value === ''
                ? null
                : Number(($event.target as HTMLInputElement).value)
          "
      /></label>
    </div>
    <template v-if="field.type === 'OBJECT' && depth < 8">
      <div
        v-for="(child, key) in field.properties"
        :key="key"
        class="min-w-0 space-y-2 border-l pl-2"
      >
        <div class="flex gap-1">
          <IdentifierInput
            :value="key"
            :readonly="readonly"
            :label="label + ' · ' + key + ' · ' + t('rule.fieldKey')"
            class="flex-1"
            @commit="rename(String(key), $event)"
          /><Button
            v-if="!readonly"
            type="button"
            size="sm"
            variant="ghost"
            @click="remove(String(key))"
            >{{ t("rule.remove") }}</Button
          >
        </div>
        <FieldConfig
          v-model="field.properties![key]!"
          :label="label + '.' + key"
          :readonly="readonly"
          :depth="depth + 1"
        />
      </div>
      <Button
        v-if="!readonly"
        type="button"
        size="sm"
        variant="outline"
        @click="add"
        >{{ t("rule.addField") }}</Button
      >
    </template>
    <FieldConfig
      v-if="field.type === 'LIST' && field.items && depth < 8"
      v-model="field.items"
      :label="label + ' · ' + t('rule.listItem')"
      :readonly="readonly"
      :depth="depth + 1"
    />
    <details>
      <summary class="cursor-pointer text-xs text-muted-foreground">
        {{ t("rule.constraints") }}
      </summary>
      <JsonField
        v-model="field"
        :label="label + ' · ' + t('rule.constraints')"
        :readonly="readonly"
      />
    </details>
  </fieldset>
</template>
