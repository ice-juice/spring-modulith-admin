<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { Button } from "@/components/ui/button";
import JsonField from "./JsonField.vue";
import type { Schema } from "../types";
interface Interval {
  id: string;
  parameter: string;
  lowerField: string;
  upperField: string;
  contiguous: boolean;
  firstLower?: unknown;
}
const intervals = defineModel<Interval[] | null | undefined>({
  required: true,
});
defineProps<{ schema: Schema; readonly?: boolean }>();
const { t } = useI18n();
function add() {
  intervals.value = [
    ...(intervals.value || []),
    {
      id: "interval_" + crypto.randomUUID().slice(0, 8),
      parameter: "",
      lowerField: "lower",
      upperField: "upper",
      contiguous: true,
    },
  ];
}
</script>
<template>
  <section class="space-y-3 rounded-xl border bg-card p-4">
    <div class="flex items-center justify-between gap-3">
      <h3 class="text-sm font-semibold">{{ t("rule.intervals") }}</h3>
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
      v-for="(item, index) in intervals"
      :key="item.id"
      class="grid gap-3 rounded-lg border bg-muted/20 p-3 sm:grid-cols-2"
    >
      <label class="grid gap-1 text-xs"
        >{{ t("rule.parameters")
        }}<select
          v-model="item.parameter"
          :disabled="readonly"
          class="h-9 rounded-md border bg-card px-2"
        >
          <option value="">{{ t("rule.choose") }}</option>
          <option
            v-for="(field, key) in schema.fields"
            v-show="field.type === 'LIST'"
            :key="key"
          >
            {{ key }}
          </option>
        </select></label
      ><label
        v-for="key in ['lowerField', 'upperField'] as const"
        :key="key"
        class="grid gap-1 text-xs"
        >{{ t("rule." + key)
        }}<input
          v-model="item[key]"
          :readonly="readonly"
          class="h-9 rounded-md border bg-card px-2" /></label
      ><label class="flex items-center gap-2 text-xs"
        ><input
          v-model="item.contiguous"
          type="checkbox"
          :disabled="readonly"
        />{{ t("rule.contiguous") }}</label
      ><JsonField
        v-model="item.firstLower"
        :label="t('rule.firstLower')"
        :readonly="readonly"
        :rows="2"
      /><Button
        v-if="!readonly"
        type="button"
        size="sm"
        variant="ghost"
        @click="intervals?.splice(index, 1)"
        >{{ t("rule.remove") }}</Button
      >
    </div>
  </section>
</template>
