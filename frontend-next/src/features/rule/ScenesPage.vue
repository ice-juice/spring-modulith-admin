<script setup lang="ts">
import PermissionButton from "@/components/business/PermissionButton.vue";

import TableWorkspace from "@/components/business/TableWorkspace.vue";
import { computed, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { confirmAction } from "@/lib/feedback";
import { useAuth } from "@/stores/auth";
import { Button } from "@/components/ui/button";
import PageHeader from "@/components/business/PageHeader.vue";
import DataTable from "@/components/business/DataTable.vue";
import EditorDialog from "@/components/business/EditorDialog.vue";
import ErrorNotice from "@/components/business/ErrorNotice.vue";
import RuleOperationStatus from './components/RuleOperationStatus.vue'
import SchemaEditor from "./components/SchemaEditor.vue";
import JsonField from "./components/JsonField.vue";
import { ruleGet } from "./api";
import { printExact, cloneExact } from "./exact";
import { useRuleTask } from "./useRuleTask";
import { sampleIds, sampleScene, isSampleId } from "./samples";
import type { Asset, Scene, RuleKind } from "./types";
const { t } = useI18n(),
  auth = useAuth(),
  task = useRuleTask(),
  { busy, error, uncertain } = task;
const rows = ref<Asset<Scene>[]>([]),
  page = ref(1),
  open = ref(false),
  selected = ref<Asset<Scene>>(),
  initial = ref(""),
  form = ref<HTMLFormElement>();
const draft = ref<Scene>({
  code: "",
  name: "",
  inputSchema: { fields: {} },
  parameterPolicy: { fields: {} },
  outputSchema: { type: "BOOLEAN", required: true },
  allowedKinds: ["EXPRESSION"],
});
const kinds: RuleKind[] = ["EXPRESSION", "CONDITION", "FLOW", "DECISION_TABLE"];
const columns = computed(() => [
  { key: "code", title: t("rule.code") },
  { key: "name", title: t("rule.name") },
  { key: "revision", title: t("rule.revision") },
  { key: "actions", title: t("rule.actions") },
]);
async function load() {
  await task.run(
    () => ruleGet<Asset<Scene>[]>("/scenes", { page: page.value, size: 20 }),
    (result) => {
      rows.value = result;
      uncertain.value = false;
    },
  );
}
function edit(row?: Asset<Scene>) {
  if (busy.value) return
  task.bind(row ? 'rule.assets.' + row.id : 'rule.scenes.new')
  example.value = "";
  selected.value = row;
  draft.value = row
    ? cloneExact(row.draft)
    : {
        code: "",
        name: "",
        inputSchema: { fields: {}, additionalProperties: false },
        parameterPolicy: { fields: {}, additionalProperties: false },
        outputSchema: { type: "BOOLEAN", required: true },
        allowedKinds: ["EXPRESSION"],
      };
  initial.value = printExact(draft.value);
  open.value = true;
}
const example = ref("");
async function chooseExample(event: Event) {
  const select = event.target as HTMLSelectElement,
    id = select.value;
  if (id !== "" && !isSampleId(id)) {
    select.value = example.value;
    return;
  }
  if (
    printExact(draft.value) !== initial.value &&
    !(await confirmAction(t("rule.sampleReplace"), t("common.confirm")))
  ) {
    select.value = example.value;
    return;
  }
  draft.value = isSampleId(id)
    ? sampleScene(id, t)
    : {
        code: "",
        name: "",
        inputSchema: { fields: {}, additionalProperties: false },
        parameterPolicy: { fields: {}, additionalProperties: false },
        outputSchema: { type: "BOOLEAN", required: true },
        allowedKinds: ["EXPRESSION"],
      };
  example.value = id;
}
async function recovered(state = 'SUCCEEDED') { if (state === 'SUCCEEDED') { open.value = false; await load() } }
async function save() {
  if (!form.value?.reportValidity()) return
  const selectedRow = selected.value
  if (await task.command({ operation: 'rule.scene-save', resource: selectedRow ? 'rule.assets.' + selectedRow.id : 'rule.scenes.new',
    path: selectedRow ? '/scenes/' + selectedRow.id : '/scenes', method: selectedRow ? 'PUT' : 'POST', body: selectedRow ? { expectedRevision: selectedRow.revision, scene: draft.value } : draft.value,
    completed: () => recovered(),
  }, () => { open.value = false })) await load()
}
async function close() {
  if (
    printExact(draft.value) !== initial.value &&
    !(await confirmAction(t("common.unsavedConfirm"), t("common.confirm")))
  )
    return;
  open.value = false;
}
watch(page, load);
void load();
</script>
<template>
  <TableWorkspace
    ><template #tools
      ><PageHeader :title="t('rule.scenes')" :description="t('rule.hint')"
        ><Button variant="outline" :disabled="busy" @click="load">{{
          uncertain ? t("rule.reconcile") : t("rule.refresh")
        }}</Button
        ><PermissionButton
          :permission="'rule:scene:edit'"
          :disabled="busy || uncertain"
          @click="edit()"
          >{{ t("rule.newScene") }}</PermissionButton
        ></PageHeader
      ><ErrorNotice :message="error" /><RuleOperationStatus :task="task" @resolved="recovered" />
      <p v-if="uncertain" role="alert" class="mb-3 text-sm text-destructive">
        {{ t("rule.uncertain") }}
      </p></template
    ><DataTable :rows="rows" :columns="columns" :loading="busy" :page="page"
      ><template #actions="{ row }"
        ><Button size="sm" variant="ghost" @click="edit(row)">{{
          t("rule.edit")
        }}</Button></template
      ></DataTable
    ><template #footer
      ><div class="mt-3 flex justify-end gap-2">
        <Button
          variant="outline"
          :disabled="page <= 1 || busy"
          @click="page--"
          >{{ t("rule.previous") }}</Button
        ><span class="p-2 text-sm">{{ t("rule.page", { page }) }}</span
        ><Button
          variant="outline"
          :disabled="rows.length < 20 || busy"
          @click="page++"
          >{{ t("rule.nextPage") }}</Button
        >
      </div></template
    ></TableWorkspace
  ><EditorDialog
    v-model="open"
    :title="selected ? t('rule.edit') : t('rule.newScene')"
    :busy="busy"
    :dirty="printExact(draft) !== initial"
    wide
    @save="save"
    ><ErrorNotice :message="error" /><RuleOperationStatus :task="task" @resolved="recovered" />
    <form ref="form" @submit.prevent="save"><fieldset :disabled="busy || uncertain" class="space-y-4">
      <div v-if="!selected" class="space-y-2 rounded-lg border bg-muted/20 p-3">
        <label class="grid gap-1 text-sm"
          >{{ t("rule.sampleScene")
          }}<select
            :value="example"
            :aria-label="t('rule.sampleScene')"
            class="h-10 min-w-0 rounded-lg border bg-card px-3"
            @change="chooseExample"
          >
            <option value="">{{ t("rule.customScene") }}</option>
            <option v-for="id in sampleIds" :key="id" :value="id">
              {{ t("rule.sampleTitles." + id) }}
            </option>
          </select></label
        >
        <p class="text-xs text-muted-foreground">
          {{
            example
              ? t("rule.sampleDescriptions." + example)
              : t("rule.samplesHint")
          }}
        </p>
      </div>
      <div class="grid gap-3 sm:grid-cols-2">
        <label class="grid gap-1 text-sm"
          >{{ t("rule.code")
          }}<input
            v-model="draft.code"
            required
            pattern="[a-z][a-z0-9_-]{0,63}"
            :readonly="busy || uncertain || !!selected"
            class="h-10 rounded-lg border bg-card px-3" /></label
        ><label class="grid gap-1 text-sm"
          >{{ t("rule.name")
          }}<input
            v-model="draft.name"
            required
            maxlength="128"
            class="h-10 rounded-lg border bg-card px-3"
        /></label>
      </div>
      <fieldset class="flex flex-wrap gap-3">
        <legend class="mb-2 text-sm">{{ t("rule.allowedKinds") }}</legend>
        <label v-for="kind in kinds" :key="kind" class="flex gap-2 text-sm"
          ><input
            v-model="draft.allowedKinds"
            type="checkbox"
            :value="kind"
            :disabled="!!selected && selected.draft.allowedKinds.includes(kind)"
          />{{ t("rule.kinds." + kind) }}</label
        >
      </fieldset>
      <SchemaEditor
        v-model="draft.inputSchema"
        :label="t('rule.inputSchema')"
        :readonly="busy || uncertain || !!selected || !auth.hasPermission('rule:scene:edit')"
      /><SchemaEditor
        v-model="draft.parameterPolicy"
        :label="t('rule.parameterPolicy')"
        :readonly="busy || uncertain || !!selected"
      /><JsonField
        v-model="draft.outputSchema"
        :label="t('rule.outputSchema')"
        :readonly="busy || uncertain || !!selected"
      />
    </fieldset></form>
    <template #footer
      ><Button variant="outline" :disabled="busy" @click="close">{{
        t("common.cancel")
      }}</Button
      ><PermissionButton
        :permission="'rule:scene:edit'"
        :disabled="busy || uncertain"
        @click="save"
        >{{ t("common.save") }}</PermissionButton
      ></template
    ></EditorDialog
  >
</template>
