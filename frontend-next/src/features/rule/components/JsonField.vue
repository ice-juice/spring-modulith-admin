<script setup lang="ts">
import { ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { parseExact, printExact } from "../exact";
defineProps<{ label: string; readonly?: boolean; rows?: number }>();
const value = defineModel<unknown>({ required: true }),
  emit = defineEmits<{ edit: [] }>();
const { t } = useI18n(),
  text = ref(printExact(value.value, true)),
  invalid = ref(false),
  area = ref<HTMLTextAreaElement>();
watch(
  () => value.value,
  (next) => {
    if (printExact(next) !== safeCurrent()) {
      text.value = printExact(next, true);
      invalid.value = false;
      area.value?.setCustomValidity("");
    }
  },
  { deep: true },
);
function safeCurrent() {
  try {
    return printExact(parseExact(text.value));
  } catch {
    return undefined;
  }
}
function change(event: Event) {
  text.value = (event.target as HTMLTextAreaElement).value;
  emit("edit");
  try {
    const next = parseExact(text.value);
    invalid.value = false;
    area.value?.setCustomValidity("");
    value.value = next;
  } catch {
    invalid.value = true;
    area.value?.setCustomValidity(t("rule.invalidJson"));
  }
}
</script>
<template>
  <label class="grid min-w-0 gap-2 text-sm"
    ><span class="font-medium">{{ label }}</span
    ><textarea
      ref="area"
      :value="text"
      :rows="rows || 5"
      :readonly="readonly"
      :aria-label="label"
      :aria-invalid="invalid"
      spellcheck="false"
      class="w-full min-w-0 rounded-lg border bg-card p-3 font-mono text-xs leading-5 text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring"
      @input="change"
    /><span v-if="invalid" role="alert" class="text-xs text-destructive">{{
      t("rule.invalidJson")
    }}</span></label
  >
</template>
