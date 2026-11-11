<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { Button } from "@/components/ui/button";
import JsonField from "./JsonField.vue";
import type { Schema, Field, FieldType } from "../types";
const schema = defineModel<Schema>({ required: true }),
  props = defineProps<{ label: string; readonly?: boolean }>(),
  { t } = useI18n();
const types: FieldType[] = [
  "STRING",
  "BOOLEAN",
  "INTEGER",
  "DECIMAL",
  "INSTANT",
  "OBJECT",
  "LIST",
];
function add() {
  let key = "field1";
  for (let i = 1; schema.value.fields[key]; i++) key = "field" + (i + 1);
  schema.value = {
    ...schema.value,
    fields: {
      ...schema.value.fields,
      [key]: { type: "STRING", required: false },
    },
  };
}
function rename(old: string, event: Event) {
  const input = event.target as HTMLInputElement,
    key = input.value;
  if (
    !/^[a-zA-Z][a-zA-Z0-9_]{0,63}$/.test(key) ||
    (key !== old && schema.value.fields[key])
  ) {
    input.value = old;
    input.setCustomValidity(t("rule.invalidJson"));
    input.reportValidity();
    input.setCustomValidity("");
    return;
  }
  schema.value = {
    ...schema.value,
    fields: Object.fromEntries(
      Object.entries(schema.value.fields).map(([name, field]) => [
        name === old ? key : name,
        field,
      ]),
    ),
  };
}
function remove(key: string) {
  const fields = { ...schema.value.fields };
  delete fields[key];
  schema.value = { ...schema.value, fields };
}
function update(key: string, field: unknown) {
  if (field && typeof field === "object" && "type" in field)
    schema.value = {
      ...schema.value,
      fields: { ...schema.value.fields, [key]: field as Field },
    };
}
</script>
<template>
  <section class="space-y-3 rounded-xl border bg-card p-4">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <h3 class="text-sm font-semibold">{{ props.label }}</h3>
      <Button
        v-if="!readonly"
        type="button"
        size="sm"
        variant="outline"
        @click="add"
        >{{ t("rule.addField") }}</Button
      >
    </div>
    <div
      v-for="(field, key) in schema.fields"
      :key="key"
      class="rounded-lg border bg-muted/15 p-3"
    >
      <div class="flex flex-wrap items-end gap-3">
        <label class="grid flex-1 gap-1 text-xs"
          >{{ t("rule.fieldKey")
          }}<input
            :value="key"
            :disabled="readonly"
            class="h-9 w-full rounded-md border bg-card px-2"
            @change="rename(key, $event)" /></label
        ><label class="grid gap-1 text-xs"
          >{{ t("rule.fieldType")
          }}<select
            v-model="field.type"
            :disabled="readonly"
            class="h-9 rounded-md border bg-card px-2"
          >
            <option v-for="type in types" :key="type">{{ type }}</option>
          </select></label
        ><label class="flex h-9 items-center gap-2 text-xs"
          ><input
            v-model="field.required"
            type="checkbox"
            :disabled="readonly"
          />{{ t("rule.required") }}</label
        ><Button
          v-if="!readonly"
          type="button"
          size="sm"
          variant="ghost"
          @click="remove(key)"
          >{{ t("rule.remove") }}</Button
        >
      </div>
      <details class="mt-2">
        <summary class="cursor-pointer text-xs text-muted-foreground">
          {{ t("rule.constraints") }}
        </summary>
        <JsonField
          :model-value="field"
          :readonly="readonly"
          :label="key + ' · ' + t('rule.constraints')"
          @update:model-value="update(key, $event)"
        />
      </details>
    </div>
  </section>
</template>
