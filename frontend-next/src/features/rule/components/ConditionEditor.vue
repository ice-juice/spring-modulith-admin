<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { Button } from "@/components/ui/button";
import OperandEditor from "./OperandEditor.vue";
import type { Condition } from "../types";
const condition = defineModel<Condition>({ required: true });
withDefaults(defineProps<{ readonly?: boolean; depth?: number }>(), {
  depth: 0,
});
const { t } = useI18n();
const group = computed(() =>
  ["AND", "OR", "NOT"].includes(condition.value.operator),
);
function change() {
  if (group.value) {
    condition.value.children ||= [];
    condition.value.left = null;
    condition.value.right = null;
  } else {
    condition.value.children = null;
    condition.value.left ||= { source: "input", path: "" };
    if (["IS_NULL", "IS_NOT_NULL"].includes(condition.value.operator))
      condition.value.right = null;
    else condition.value.right ||= { source: "literal", value: null };
  }
}
function add(isGroup = false) {
  condition.value.children ||= [];
  condition.value.children.push(
    isGroup
      ? {
          id: "group_" + crypto.randomUUID().slice(0, 8),
          operator: "AND",
          children: [],
        }
      : {
          id: "condition_" + crypto.randomUUID().slice(0, 8),
          operator: "EQ",
          left: { source: "input", path: "" },
          right: { source: "literal", value: null },
        },
  );
}
</script>
<template>
  <fieldset class="@container min-w-0 space-y-3 rounded-xl border border-primary/20 bg-card p-4">
    <div class="flex flex-wrap items-center gap-2">
      <input
        v-model="condition.id"
        :aria-label="t('rule.nodeId')"
        :readonly="readonly"
        class="h-9 min-w-0 flex-1 rounded-md border bg-card px-2 text-xs"
      /><select
        v-model="condition.operator"
        :disabled="readonly"
        :aria-label="t('rule.operator')"
        class="h-9 rounded-md border bg-card px-2"
        @change="change"
      >
        <option
          v-for="operator in [
            'AND',
            'OR',
            'NOT',
            'EQ',
            'NE',
            'GT',
            'GE',
            'LT',
            'LE',
            'IN',
            'CONTAINS',
            'IS_NULL',
            'IS_NOT_NULL',
          ]"
          :key="operator"
        >
          {{ operator }}
        </option>
      </select>
    </div>
    <template v-if="group"
      ><div
        v-for="(child, index) in condition.children"
        :key="child.id"
        class="space-y-1"
      >
        <ConditionEditor
          v-if="depth < 8"
          v-model="condition.children![index]!"
          :depth="depth + 1"
          :readonly="readonly"
        /><Button
          v-if="!readonly"
          type="button"
          size="sm"
          variant="ghost"
          @click="condition.children!.splice(index, 1)"
          >{{ t("rule.remove") }}</Button
        >
      </div>
      <div v-if="!readonly && depth < 8" class="flex flex-wrap gap-2">
        <Button type="button" size="sm" variant="outline" @click="add()">{{
          t("rule.addCondition")
        }}</Button
        ><Button type="button" size="sm" variant="ghost" @click="add(true)">{{
          t("rule.addGroup")
        }}</Button>
      </div></template
    >
    <div v-else class="grid min-w-0 gap-3 @md:grid-cols-2">
      <OperandEditor
        v-if="condition.left"
        v-model="condition.left"
        :label="t('rule.left')"
        :readonly="readonly"
      /><OperandEditor
        v-if="
          condition.right &&
          !['IS_NULL', 'IS_NOT_NULL'].includes(condition.operator)
        "
        v-model="condition.right"
        :label="t('rule.right')"
        :readonly="readonly"
      />
    </div>
  </fieldset>
</template>
