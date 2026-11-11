<script setup lang="ts">
import { computed, inject } from "vue";
import { useI18n } from "vue-i18n";
import { Button } from "@/components/ui/button";
import ErrorNotice from "@/components/business/ErrorNotice.vue";
import { ruleReferencesKey } from "../references";
defineProps<{ busy: boolean; error: string }>();
defineEmits<{ reload: [] }>();
const { t } = useI18n(),
  references = inject(
    ruleReferencesKey,
    computed(() => []),
  );
</script>
<template>
  <details class="rounded-xl border bg-card p-3" data-testid="rule-references">
    <summary class="cursor-pointer text-sm font-medium">
      {{ t("rule.references") }}
    </summary>
    <p class="mt-3 text-xs text-muted-foreground">
      {{ t("rule.referencesHint") }}
    </p>
    <div class="mt-3 flex items-center justify-between gap-2">
      <span class="text-xs text-muted-foreground">{{
        t("rule.referencesSource")
      }}</span>
      <Button
        type="button"
        variant="ghost"
        size="sm"
        :disabled="busy"
        @click="$emit('reload')"
        >{{ t("rule.refresh") }}</Button
      >
    </div>
    <ErrorNotice :message="error" />
    <div class="mt-2 grid max-h-64 gap-2 overflow-y-auto sm:grid-cols-2">
      <div
        v-for="item in references"
        :key="item.kind + item.label"
        class="min-w-0 rounded-md bg-muted/30 p-2"
      >
        <code class="block break-all text-xs">{{
          item.kind === "function" ? item.detail : item.label
        }}</code>
        <span class="text-xs text-muted-foreground">{{
          item.kind === "function" ? t("rule.function") : item.detail
        }}</span>
      </div>
    </div>
  </details>
</template>
