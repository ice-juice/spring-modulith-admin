<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { Button } from "@/components/ui/button";
import JsonField from "./JsonField.vue";
import OperandEditor from "./OperandEditor.vue";
import type { DecisionTable, Field, Schema } from "../types";
const table = defineModel<DecisionTable>({ required: true }),
  props = defineProps<{
    inputSchema: Schema;
    outputSchema: Field;
    readonly?: boolean;
    locate?: string;
  }>(),
  { t } = useI18n(),
  selected = ref("");
const row = computed(() =>
    table.value.rows.find((item) => item.id === selected.value),
  ),
  outputs = computed(() => Object.keys(props.outputSchema.properties || {}));
const ops = [
    "ANY",
    "EQ",
    "NE",
    "GT",
    "GE",
    "LT",
    "LE",
    "IN",
    "CONTAINS",
    "IS_NULL",
    "IS_NOT_NULL",
  ],
  unary = ["ANY", "IS_NULL", "IS_NOT_NULL"];
watch(
  () => props.locate,
  (value) => {
    const id = value?.replace(/^table\./, "").split("/")[0];
    if (id && table.value.rows.some((item) => item.id === id))
      selected.value = id;
  },
);
function next(prefix: string, items: { id: string }[]) {
  let n = 1;
  while (items.some((item) => item.id === prefix + n)) n++;
  return prefix + n;
}
function addColumn() {
  if (table.value.inputs.length >= 16) return;
  const id = next("column", table.value.inputs),
    first = Object.entries(props.inputSchema.fields)[0];
  table.value.inputs.push({
    id,
    source: first
      ? { source: "input", path: first[0] }
      : { source: "literal", value: "" },
    schema: first ? { ...first[1] } : { type: "STRING" },
  });
  table.value.rows.forEach((item) => {
    item.cells[id] = { operator: "ANY" };
  });
}
function removeColumn(id: string) {
  table.value.inputs = table.value.inputs.filter((item) => item.id !== id);
  table.value.rows.forEach((item) => delete item.cells[id]);
}
function addRow() {
  if (table.value.rows.length >= 200) return;
  const id = next("row", table.value.rows);
  table.value.rows.push({
    id,
    cells: Object.fromEntries(
      table.value.inputs.map((column) => [column.id, { operator: "ANY" }]),
    ),
    outputs: Object.fromEntries(outputs.value.map((key) => [key, "null"])),
  });
  selected.value = id;
}
function move(index: number, offset: number) {
  const to = index + offset,
    entry = table.value.rows[index];
  if (!entry || to < 0 || to >= table.value.rows.length) return;
  table.value.rows.splice(index, 1);
  table.value.rows.splice(to, 0, entry);
}
function operator(key: string, op: string) {
  if (!row.value) return;
  const old = row.value.cells[key];
  row.value.cells[key] = {
    operator: op,
    operand: unary.includes(op)
      ? null
      : old?.operand || { source: "literal", value: null },
  };
}
</script>
<template>
  <section class="space-y-4">
    <div class="flex flex-wrap items-end gap-3">
      <label class="grid gap-1 text-sm"
        >{{ t("rule.hitPolicy")
        }}<select
          v-model="table.hitPolicy"
          :aria-label="t('rule.hitPolicy')"
          :disabled="readonly"
          class="h-10 rounded-lg border bg-card px-3"
        >
          <option v-for="policy in ['UNIQUE', 'FIRST']" :key="policy">
            {{ policy }}
          </option>
        </select></label
      ><Button
        v-if="!readonly"
        type="button"
        variant="outline"
        :disabled="table.inputs.length >= 16"
        @click="addColumn"
        >{{ t("rule.addColumn") }}</Button
      ><Button
        v-if="!readonly"
        type="button"
        variant="outline"
        :disabled="!table.inputs.length || table.rows.length >= 200"
        @click="addRow"
        >{{ t("rule.addRow") }}</Button
      >
    </div>
    <p v-if="table.hitPolicy === 'FIRST'" class="text-sm text-muted-foreground">
      {{ t("rule.firstOrder") }}
    </p>
    <details class="rounded-xl border bg-muted/20 p-3">
      <summary class="cursor-pointer text-sm font-medium">
        {{ t("rule.columns") }}
      </summary>
      <div class="mt-3 grid gap-3 md:grid-cols-2">
        <section
          v-for="column in table.inputs"
          :key="column.id"
          class="space-y-2 rounded-lg border bg-card p-3"
        >
          <div class="flex items-center justify-between">
            <span class="font-mono text-sm">{{ column.id }}</span
            ><Button
              v-if="!readonly"
              type="button"
              size="sm"
              variant="ghost"
              @click="removeColumn(column.id)"
              >{{ t("rule.remove") }}</Button
            >
          </div>
          <OperandEditor
            v-model="column.source"
            :label="t('rule.source')"
            :readonly="readonly"
          /><JsonField
            v-model="column.schema"
            :label="t('rule.fieldType')"
            :readonly="readonly"
            :rows="3"
          />
        </section>
      </div>
    </details>
    <div
      class="overflow-auto rounded-xl border"
      tabindex="0"
      role="region"
      :aria-label="t('rule.rows')"
    >
      <table class="w-full text-left text-xs">
        <thead class="bg-muted">
          <tr>
            <th class="p-3">{{ t("rule.rowId") }}</th>
            <th
              v-for="column in table.inputs"
              :key="column.id"
              class="min-w-24 p-3"
            >
              {{ column.id }}
            </th>
            <th class="min-w-56 p-3">{{ t("rule.actions") }}</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(entry, index) in table.rows"
            :key="entry.id"
            class="border-t"
            :class="selected === entry.id ? 'bg-accent/50' : ''"
          >
            <td class="p-3 font-mono">{{ entry.id }}</td>
            <td
              v-for="column in table.inputs"
              :key="column.id"
              class="p-3 font-mono"
            >
              {{ entry.cells[column.id]?.operator || "—" }}
            </td>
            <td class="whitespace-nowrap p-2">
              <Button
                type="button"
                size="sm"
                variant="ghost"
                :aria-label="entry.id + ' · ' + t('rule.edit')"
                @click="selected = entry.id"
                >{{ t(readonly ? "rule.details" : "rule.edit") }}</Button
              ><template v-if="!readonly"
                ><Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  :aria-label="entry.id + ' · ' + t('rule.moveUp')"
                  :disabled="!index"
                  @click="move(index, -1)"
                  >{{ t("rule.moveUp") }}</Button
                ><Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  :aria-label="entry.id + ' · ' + t('rule.moveDown')"
                  :disabled="index === table.rows.length - 1"
                  @click="move(index, 1)"
                  >{{ t("rule.moveDown") }}</Button
                ><Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  :aria-label="entry.id + ' · ' + t('rule.remove')"
                  @click="table.rows.splice(index, 1)"
                  >{{ t("rule.remove") }}</Button
                ></template
              >
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <section
      v-if="row"
      :key="row.id"
      class="space-y-3 rounded-xl border bg-card p-4"
    >
      <h3 class="text-sm font-semibold">{{ row.id }}</h3>
      <div class="grid gap-3 md:grid-cols-2">
        <div
          v-for="column in table.inputs"
          :key="column.id"
          class="space-y-2 rounded-lg border bg-muted/20 p-3"
        >
          <label class="grid gap-1 text-xs"
            >{{ column.id
            }}<select
              :value="row.cells[column.id]?.operator"
              :aria-label="column.id + ' · ' + t('rule.operator')"
              :disabled="readonly"
              class="h-9 rounded-md border bg-card px-2"
              @change="
                operator(column.id, ($event.target as HTMLSelectElement).value)
              "
            >
              <option v-for="op in ops" :key="op">{{ op }}</option>
            </select></label
          ><OperandEditor
            v-if="row.cells[column.id]?.operand"
            v-model="row.cells[column.id]!.operand!"
            :label="column.id + ' · ' + t('rule.right')"
            :readonly="readonly"
          />
        </div>
      </div>
      <h4 class="text-sm font-medium">{{ t("rule.outputs") }}</h4>
      <label v-for="key in outputs" :key="key" class="grid gap-1 text-xs"
        >{{ key
        }}<input
          v-model="row.outputs[key]"
          :readonly="readonly"
          class="h-9 min-w-0 rounded-md border bg-card px-2 font-mono"
      /></label>
    </section>
    <JsonField
      v-model="table.defaultOutput"
      :label="t('rule.defaultOutput')"
      :readonly="readonly"
    />
  </section>
</template>
