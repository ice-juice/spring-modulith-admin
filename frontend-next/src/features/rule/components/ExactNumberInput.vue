<script setup lang="ts">
import { ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { exactNumber, printExact } from "../exact";
const props = defineProps<{
  value: unknown;
  label: string;
  readonly?: boolean;
}>();
const emit = defineEmits<{ update: [value: unknown] }>();
const { t } = useI18n(),
  text = ref(props.value == null ? "" : printExact(props.value));
watch(
  () => props.value,
  (value) => {
    text.value = value == null ? "" : printExact(value);
  },
);
function input(event: Event) {
  const element = event.target as HTMLInputElement;
  text.value = element.value;
  try {
    const value = text.value.trim() ? exactNumber(text.value.trim()) : null;
    element.setCustomValidity("");
    emit("update", value);
  } catch {
    element.setCustomValidity(t("rule.invalidNumber"));
  }
}
</script>
<template>
  <input
    :value="text"
    :readonly="readonly"
    :aria-label="label"
    class="h-8 min-w-0 rounded-md border bg-card px-2"
    @input="input"
  />
</template>
