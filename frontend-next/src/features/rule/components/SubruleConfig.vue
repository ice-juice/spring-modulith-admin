<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useAuth } from "@/stores/auth";
import { Button } from "@/components/ui/button";
import ErrorNotice from "@/components/business/ErrorNotice.vue";
import { confirmAction } from "@/lib/feedback";
import BindingEditor from "./BindingEditor.vue";
import { ruleGet } from "../api";
import { cloneExact } from "../exact";
import { useRuleTask } from "../useRuleTask";
import type { Asset, FlowNode, RuleDocument, Version } from "../types";
const node = defineModel<FlowNode>({ required: true });
const props = defineProps<{ readonly?: boolean }>();
const { t } = useI18n(),
  auth = useAuth(),
  task = useRuleTask(),
  { busy, error } = task;
const templates = ref<Asset<RuleDocument>[]>([]),
  versions = ref<Version<RuleDocument>[]>([]),
  templateId = ref(""),
  versionId = ref(""),
  page = ref(1);
const selected = computed(() =>
  versions.value.find((version) => version.id === versionId.value),
);
const attached = computed(() =>
  versions.value.find((version) => version.id === node.value.subRuleVersionId),
);
let epoch = 0;
watch(templateId, () => {
  epoch++;
  versions.value = [];
  versionId.value = "";
});
async function loadTemplates() {
  const current = epoch;
  await task.run(
    () =>
      ruleGet<Asset<RuleDocument>[]>("/templates", {
        page: page.value,
        size: 100,
      }),
    (rows) => {
      templates.value = page.value === 1 ? rows : [...templates.value, ...rows];
      if (rows.length === 100) page.value++;
    },
    false,
    () => current === epoch,
  );
}
async function loadVersions() {
  if (!templateId.value) return;
  const current = ++epoch;
  await task.run(
    () =>
      ruleGet<Version<RuleDocument>[]>(
        "/assets/" + templateId.value + "/versions",
      ),
    (rows) => {
      versions.value = rows.filter(
        (version) =>
          node.value.kind !== "TABLE" ||
          version.document.definition.kind === "DECISION_TABLE",
      );
    },
    false,
    () => current === epoch,
  );
}
async function apply() {
  const version = selected.value;
  if (props.readonly || !version || busy.value) return;
  if (
    node.value.subRuleVersionId &&
    node.value.subRuleVersionId !== version.id &&
    !(await confirmAction(t("rule.replaceSubrule"), t("common.confirm")))
  )
    return;
  // Preserve compatible bindings; new/unmapped required inputs remain visible for explicit configuration.
  node.value = {
    ...node.value,
    subRuleVersionId: version.id,
    outputSchema: cloneExact(version.document.outputSchema),
    inputBindings: Object.fromEntries(
      Object.entries(node.value.inputBindings || {}).filter(([key]) =>
        Object.hasOwn(version.document.inputSchema.fields, key),
      ),
    ),
    parameterBindings: Object.fromEntries(
      Object.entries(node.value.parameterBindings || {}).filter(([key]) =>
        Object.hasOwn(version.document.parameterSchema.fields, key),
      ),
    ),
  };
}
</script>
<template>
  <section class="min-w-0 space-y-3">
    <p class="break-all text-xs text-muted-foreground">
      {{ t("rule.pinnedVersion") }}{{ t("rule.separator")
      }}{{ node.subRuleVersionId || t("rule.choose") }}
    </p>
    <p class="text-xs text-muted-foreground">{{ t("rule.subruleHint") }}</p>
    <ErrorNotice :message="error" />
    <template v-if="auth.hasPermission('rule:template:view')">
      <Button
        type="button"
        size="sm"
        variant="outline"
        :disabled="busy"
        @click="loadTemplates"
        >{{ t("rule.browseTemplates") }}</Button
      >
      <label class="grid gap-1 text-xs"
        >{{ t("rule.templates")
        }}<select
          v-model="templateId"
          :disabled="busy || readonly"
          :aria-label="t('rule.childTemplate')"
          class="h-9 min-w-0 rounded-md border bg-card px-2"
        >
          <option value="">{{ t("rule.choose") }}</option>
          <option v-for="asset in templates" :key="asset.id" :value="asset.id">
            {{ asset.name }}{{ t("rule.separator") }}{{ asset.code }}
          </option>
        </select></label
      >
      <Button
        type="button"
        size="sm"
        variant="outline"
        :disabled="busy || !templateId"
        @click="loadVersions"
        >{{ t("rule.loadVersions") }}</Button
      >
      <label class="grid gap-1 text-xs"
        >{{ t("rule.version")
        }}<select
          v-model="versionId"
          :disabled="busy || readonly"
          :aria-label="t('rule.childVersion')"
          class="h-9 min-w-0 rounded-md border bg-card px-2"
        >
          <option value="">{{ t("rule.choose") }}</option>
          <option
            v-for="version in versions"
            :key="version.id"
            :value="version.id"
          >
            {{ version.revision }}{{ t("rule.separator") }}{{ version.id }}
          </option>
        </select></label
      >
      <Button
        v-if="!readonly"
        type="button"
        size="sm"
        :disabled="busy || !selected"
        @click="apply"
        >{{ t("rule.applyVersion") }}</Button
      >
    </template>
    <BindingEditor
      v-if="attached"
      :model-value="node.inputBindings || {}"
      :schema="attached.document.inputSchema"
      :label="t('rule.inputBindings')"
      :readonly="readonly"
      @update:model-value="node.inputBindings = $event"
    />
    <BindingEditor
      v-if="attached"
      :model-value="node.parameterBindings || {}"
      :schema="attached.document.parameterSchema"
      :label="t('rule.parameterBindings')"
      :readonly="readonly"
      @update:model-value="node.parameterBindings = $event"
    />
    <p v-if="!attached" class="text-xs text-muted-foreground">
      {{ t("rule.loadMappingHint") }}
    </p>
    <details>
      <summary class="cursor-pointer text-xs">
        {{ t("rule.existingMappings") }}
      </summary>
      <pre class="overflow-auto whitespace-pre-wrap break-all text-xs"
        >{{ node.inputBindings }}{{ node.parameterBindings }}</pre>
    </details>
  </section>
</template>
