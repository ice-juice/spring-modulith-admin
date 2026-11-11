<script setup lang="ts">
import { nextTick, ref, watch } from "vue";
const props = defineProps<{
  value: string;
  label: string;
  readonly?: boolean;
  pattern?: string;
}>();
const emit = defineEmits<{ commit: [value: string] }>();
const text = ref(props.value);
watch(
  () => props.value,
  (value) => {
    text.value = value;
  },
);
async function commit(event: Event) {
  if (!(event.target as HTMLInputElement).reportValidity() || props.readonly)
    return;
  emit("commit", text.value);
  await nextTick();
  text.value = props.value;
}
</script>
<template>
  <input
    v-model="text"
    required
    :pattern="pattern || '[a-zA-Z][a-zA-Z0-9_]{0,63}'"
    :readonly="readonly"
    :aria-label="label"
    class="h-9 min-w-0 rounded-md border bg-card px-2 text-xs"
    @change="commit"
  />
</template>
