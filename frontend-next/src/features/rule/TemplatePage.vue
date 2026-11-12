<script setup lang="ts">
import PermissionButton from "@/components/business/PermissionButton.vue";

import { computed, defineAsyncComponent, provide, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { useAuth } from "@/stores/auth";
import { useUnsavedChanges } from "@/composables/useUnsavedChanges";
import { confirmAction } from "@/lib/feedback";
import { Button } from "@/components/ui/button";
import PageHeader from "@/components/business/PageHeader.vue";
import ErrorNotice from "@/components/business/ErrorNotice.vue";
import RuleOperationStatus from './components/RuleOperationStatus.vue'
import SchemaEditor from "./components/SchemaEditor.vue";
import ConditionEditor from "./components/ConditionEditor.vue";
import JsonField from "./components/JsonField.vue";
import IntervalEditor from "./components/IntervalEditor.vue";
import TemplateGovernance from "./components/TemplateGovernance.vue";
import ReleasePanel from "./components/ReleasePanel.vue";
import ReferencePanel from "./components/ReferencePanel.vue";
import ResizablePanels from "@/components/business/ResizablePanels.vue";
import { referencesFor, ruleReferencesKey } from "./references";
import { isSampleId, sampleDocument } from "./samples";
import { ruleGet } from "./api";
import { cloneExact, parseExact, printExact } from "./exact";
import { useRuleHistory } from "./useRuleHistory";
import { definitionFor, documentFor } from "./model";
import { useRuleTask } from "./useRuleTask";
import type { Asset, RuleDocument, Scene, RuleKind, Problem } from "./types";
const TestPanel = defineAsyncComponent(
  () => import("./components/TestPanel.vue"),
);
const DecisionEditor = defineAsyncComponent(
  () => import("./components/DecisionEditor.vue"),
);
const FlowEditor = defineAsyncComponent(
  () => import("./components/FlowEditor.vue"),
);
const flowTrace = ref<import("./types").Trace[]>([]);
const CodeEditor = defineAsyncComponent(
    () => import("./components/CodeEditor.vue"),
  ),
  SimulationPanel = defineAsyncComponent(
    () => import("./components/SimulationPanel.vue"),
  );
const route = useRoute(),
  router = useRouter(),
  { t } = useI18n(),
  auth = useAuth(),
  task = useRuleTask(),
  { busy, error, uncertain } = task;
const creating = computed(() => !!route.params.sceneId),
  code = ref(String(history.state?.ruleTemplateCode || ""));
const row = ref<Asset<RuleDocument>>(),
  draft = ref<RuleDocument>(),
  name = ref(""),
  sceneId = ref(""),
  allowedKinds = ref<RuleKind[]>([]),
  initial = ref(""),
  tab = ref("definition"),
  form = ref<HTMLFormElement>(),
  invalidEdit = ref(false),
  line = ref<number | null>(),
  column = ref<number | null>(),
  diagnostics = ref<Problem[]>([]),
  simulationOpen = ref(false);
const readonly = computed(
  () => !auth.hasPermission("rule:template:edit") || busy.value || uncertain.value,
);
const functions = ref<string[]>([]),
  referenceTask = useRuleTask();
provide(
  ruleReferencesKey,
  computed(() => referencesFor(draft.value, functions.value)),
);
function loadFunctions() {
  return referenceTask.run(
    () => ruleGet<string[]>("/functions"),
    (value) => {
      functions.value = value;
    },
  );
}
const editorGeneration = ref(0);
const authoringSnapshot = computed(() =>
  draft.value
    ? printExact({ code: code.value, name: name.value, document: draft.value })
    : undefined,
);
const editHistory = useRuleHistory(authoringSnapshot, (snapshot) => {
  const restored = parseExact<{
    code: string;
    name: string;
    document: RuleDocument;
  }>(snapshot);
  code.value = restored.code;
  name.value = restored.name;
  draft.value = restored.document;
  invalidEdit.value = false;
  flowTrace.value = [];
  diagnostics.value = [];
  editorGeneration.value++;
});
const { canUndo, canRedo } = editHistory;
const simulationEditVersion = ref(0);
function invalidateSimulation() {
  simulationEditVersion.value++;
  flowTrace.value = [];
  diagnostics.value = [];
  line.value = null;
  column.value = null;
}
function rawEdit() {
  invalidEdit.value = true;
  invalidateSimulation();
}
watch(authoringSnapshot, invalidateSimulation, { flush: "sync" });
const dirty = computed(
  () =>
    invalidEdit.value ||
    (!!draft.value &&
      printExact({ name: name.value, document: draft.value }) !==
        initial.value),
);
useUnsavedChanges(dirty, busy);
async function load() {
  if (
    dirty.value &&
    !(await confirmAction(t("common.unsavedConfirm"), t("common.confirm")))
  )
    return;
  if (creating.value) {
    await task.run(
      async () => {
        for (let page = 1; page <= 100; page++) {
          const scenes = await ruleGet<Asset<Scene>[]>("/scenes", {
            page,
            size: 100,
          });
          const scene = scenes.find((item) => item.id === route.params.sceneId);
          if (scene) return scene;
          if (scenes.length < 100) break;
        }
        throw new Error("SCENE_NOT_FOUND");
      },
      (scene) => {
        sceneId.value = scene.id;
        allowedKinds.value = scene.draft.allowedKinds;
        const sample = String(history.state?.ruleTemplateSample || "");
        draft.value = isSampleId(sample)
          ? sampleDocument(sample, scene.draft, t)
          : documentFor(scene.draft);
        name.value = String(history.state?.ruleTemplateName || "");
        initial.value = printExact({ name: name.value, document: draft.value });
        editHistory.reset(authoringSnapshot.value!);
      },
    );
    return;
  }
  await task.run(
    async () => {
      const asset = await ruleGet<Asset<RuleDocument>>(
        "/templates/" + route.params.id,
      );
      const scene = await ruleGet<Asset<Scene>>(
        "/templates/" + asset.id + "/scene",
      );
      return { asset, scene };
    },
    (value) => {
      row.value = value.asset;
      draft.value = cloneExact(value.asset.draft);
      name.value = value.asset.name;
      sceneId.value = value.scene?.id || "";
      allowedKinds.value = value.scene?.draft.allowedKinds || [
        value.asset.draft.definition.kind,
      ];
      initial.value = printExact({ name: name.value, document: draft.value });
      invalidEdit.value = false;
      uncertain.value = false;
      editHistory.reset(authoringSnapshot.value!);
      editorGeneration.value++;
    },
  );
}
watch(() => creating.value ? 'rule.templates.new' : 'rule.assets.' + String(route.params.id), resource => task.bind(resource), { immediate: true })
async function recovered(state = 'SUCCEEDED') {
  if (state !== 'SUCCEEDED') return
  if (creating.value) { await router.replace('/rule/templates'); return }
  initial.value = printExact({ name: name.value, document: draft.value }); invalidEdit.value = false
  await load()
}
async function reconcileOriginal() { const state = await task.check(); if (state) await recovered(state) }
async function save() {
  if (readonly.value || !draft.value || !form.value?.reportValidity() || !sceneId.value) return
  const submittedSnapshot = printExact({ name: name.value, document: draft.value }), submittedEditVersion = simulationEditVersion.value
  const asset = row.value, create = creating.value
  if (!create && !asset) return
  await task.command<Asset<RuleDocument>>({ operation: 'rule.template-save', resource: create ? 'rule.templates.new' : 'rule.assets.' + asset!.id,
    path: create ? '/templates' : '/templates/' + asset!.id + '/draft', method: create ? 'POST' : 'PUT',
    body: create ? { code: code.value, name: name.value, sceneId: sceneId.value, document: draft.value } : { expectedRevision: asset!.revision, template: { code: asset!.code, name: name.value, sceneId: sceneId.value, document: draft.value } },
    completed: () => recovered(),
  }, value => {
    initial.value = submittedSnapshot
    if (submittedEditVersion === simulationEditVersion.value) invalidEdit.value = false
    if (create) void router.replace('/rule/templates/' + value.id); else row.value = value
  })
}
async function changeKind(event: Event) {
  const element = event.target as HTMLSelectElement,
    kind = element.value as RuleKind;
  if (
    !draft.value ||
    !(await confirmAction(t("common.unsavedConfirm"), t("common.confirm")))
  ) {
    element.value = draft.value?.definition.kind || "";
    return;
  }
  draft.value.definition = definitionFor(kind);
}
function locate(path: string, rowLine?: number | null, col?: number | null) {
  line.value = rowLine;
  column.value = col;
  tab.value = "definition";
  diagnostics.value = [{ path, code: "", line: rowLine, column: col }];
}
void load();
void loadFunctions();
</script>
<template>
  <PageHeader
    :title="row?.name || t(creating ? 'rule.newTemplate' : 'rule.templates')"
    :description="t('rule.noAutoPublish')"
    ><Button
      variant="ghost"
      :disabled="busy"
      @click="router.push('/rule/templates')"
      >{{ t("rule.back") }}</Button
    ><Button variant="outline" :disabled="busy" @click="uncertain ? reconcileOriginal() : load()">{{
      uncertain ? t("rule.reconcile") : t("rule.refresh")
    }}</Button
    ><PermissionButton
      :permission="'rule:template:edit'"
      :disabled="busy || uncertain || (!dirty && !creating)"
      @click="save"
      >{{ t("rule.saveDraft") }}</PermissionButton
    ><PermissionButton
      :permission="'rule:simulation:run'"
      variant="outline"
      @click="simulationOpen = !simulationOpen"
      >{{ t("rule.simulation") }}</PermissionButton
    ></PageHeader
  ><ErrorNotice :message="error" /><RuleOperationStatus :task="task" @resolved="recovered" />
  <p v-if="uncertain" role="alert" class="mb-3 text-sm text-destructive">
    {{ t("rule.uncertain") }}
  </p>
  <template v-if="draft"
    ><div
      v-if="!readonly"
      class="mb-3 flex flex-wrap items-center gap-2"
      role="group"
      :aria-label="t('rule.editHistory')"
    >
      <Button
        type="button"
        variant="outline"
        size="sm"
        :disabled="busy || uncertain || !canUndo"
        @click="editHistory.undo"
        >{{ t("rule.undo") }}</Button
      >
      <Button
        type="button"
        variant="outline"
        size="sm"
        :disabled="busy || uncertain || !canRedo"
        @click="editHistory.redo"
        >{{ t("rule.redo") }}</Button
      >
      <span class="text-xs text-muted-foreground">{{
        t("rule.historyHint")
      }}</span>
    </div>
    <div
      v-if="row"
      class="mb-4 flex flex-wrap items-center gap-x-5 gap-y-2 text-xs text-muted-foreground"
    >
      <span
        >{{ t("rule.state") }}{{ t("rule.separator")
        }}{{ t("rule.states." + row.state) }}</span
      ><span
        >{{ t("rule.revision") }}{{ t("rule.separator")
        }}{{ row.revision }}</span
      ><span
        >{{ t("rule.releaseRevision") }}{{ t("rule.separator")
        }}{{ row.releaseRevision }}</span
      ><span class="text-primary" role="status">{{
        dirty ? t("rule.unsaved") : t("rule.saved")
      }}</span>
    </div>
    <ResizablePanels side="right" :initial="340" :min="280" :max="520" :main-min="500" :enabled="simulationOpen" :label="t('common.resizePanels.rulePanel')">
      <form
        :key="editorGeneration"
        ref="form"
        class="min-w-0 space-y-4"
        @input="invalidateSimulation"
        @submit.prevent="save"
      >
        <div
          class="flex flex-wrap items-center gap-3 rounded-xl border bg-card p-4"
        >
          <label v-if="creating" class="grid min-w-40 gap-1 text-xs"
            >{{ t("rule.code")
            }}<input
              v-model="code"
              required
              :readonly="readonly"
              pattern="[a-z][a-z0-9_-]{0,63}"
              class="h-10 rounded-lg border bg-card px-3" /></label
          ><label class="grid min-w-48 flex-1 gap-1 text-xs"
            >{{ t("rule.name")
            }}<input
              v-model="name"
              required
              :readonly="readonly"
              maxlength="128"
              class="h-10 rounded-lg border bg-card px-3 text-sm" /></label
          ><label class="grid gap-1 text-xs"
            >{{ t("rule.kind")
            }}<select
              :value="draft.definition.kind"
              :disabled="readonly"
              class="h-10 rounded-lg border bg-card px-3 text-sm"
              @change="changeKind"
            >
              <option v-for="kind in allowedKinds" :key="kind" :value="kind">
                {{ t("rule.kinds." + kind) }}
              </option>
            </select></label
          >
        </div>
        <ReferencePanel
          :busy="referenceTask.busy.value"
          :error="referenceTask.error.value"
          @reload="loadFunctions"
        />
        <div
          role="tablist"
          :aria-label="t('rule.templates')"
          class="flex flex-wrap gap-1 rounded-xl bg-muted/60 p-1"
        >
          <Button
            v-for="section in ['definition', 'contract', 'examples']"
            :key="section"
            type="button"
            role="tab"
            :aria-selected="tab === section"
            :variant="tab === section ? 'secondary' : 'ghost'"
            @click="tab = section"
            >{{ t("rule." + section) }}</Button
          >
        </div>
        <section
          v-show="tab === 'definition'"
          class="space-y-4 rounded-xl border bg-card p-4"
        >
          <template v-if="draft.definition.kind === 'EXPRESSION'"
            ><CodeEditor
              v-model="draft.definition.expression!"
              :label="t('rule.expression')"
              :readonly="readonly"
              :line="line"
              :column="column" /></template
          ><template v-else-if="draft.definition.kind === 'CONDITION'"
            ><ConditionEditor
              v-if="draft.definition.condition"
              v-model="draft.definition.condition"
              :readonly="readonly" />
            <div class="grid gap-3 sm:grid-cols-2">
              <JsonField
                v-model="draft.definition.onMatch"
                :label="t('rule.onMatch')"
                :readonly="readonly"
              /><JsonField
                v-model="draft.definition.otherwise"
                :label="t('rule.otherwise')"
                :readonly="readonly"
              /></div></template
          ><FlowEditor
            v-else-if="
              draft.definition.kind === 'FLOW' && draft.definition.flow
            "
            v-model="draft.definition.flow"
            :document="draft"
            :readonly="readonly"
            :locate="diagnostics[0]?.path"
            :line="line"
            :column="column"
            :trace="flowTrace"
          /><DecisionEditor
            v-else-if="
              draft.definition.kind === 'DECISION_TABLE' &&
              draft.definition.table
            "
            v-model="draft.definition.table"
            :input-schema="draft.inputSchema"
            :output-schema="draft.outputSchema"
            :readonly="readonly"
            :locate="diagnostics[0]?.path"
          /><JsonField
            v-else
            v-model="draft.definition"
            :label="t('rule.definition')"
            :readonly="readonly"
            :rows="14"
          />
          <details>
            <summary class="cursor-pointer text-xs text-muted-foreground">
              {{ t("rule.advanced") }}
            </summary>
            <JsonField
              v-model="draft.definition"
              :label="t('rule.definition')"
              :readonly="readonly"
              :rows="10"
              @edit="rawEdit"
            />
          </details>
          <p
            v-for="problem in diagnostics"
            :key="problem.path"
            class="break-all text-xs text-muted-foreground"
          >
            {{ problem.path }}
          </p>
        </section>
        <section v-show="tab === 'contract'" class="space-y-4">
          <SchemaEditor
            v-model="draft.parameterSchema"
            :label="t('rule.parameterSchema')"
            :readonly="readonly"
          /><JsonField
            v-model="draft.parameterUi"
            :label="t('rule.parameterUi')"
            :readonly="readonly"
            @edit="rawEdit"
          /><IntervalEditor
            v-model="draft.intervalConstraints"
            :schema="draft.parameterSchema"
            :readonly="readonly"
          />
          <details>
            <summary class="cursor-pointer text-sm">
              {{ t("rule.inputSchema") }}
            </summary>
            <JsonField
              :model-value="draft.inputSchema"
              :label="t('rule.inputSchema')"
              readonly
            /><JsonField
              :model-value="draft.outputSchema"
              :label="t('rule.outputSchema')"
              readonly
            />
          </details>
        </section>
        <section v-show="tab === 'examples'">
          <JsonField
            v-model="draft.examples"
            :label="t('rule.examples')"
            :rows="16"
            :readonly="readonly"
            @edit="rawEdit"
          />
        </section>
      </form>
      <template #panel><div class="space-y-4">
        <ReleasePanel
          v-if="row"
          :key="row.revision"
          :asset="row"
          :dirty="dirty"
          @changed="load"
        /><TemplateGovernance
          v-if="row"
          :key="'governance' + row.revision"
          :asset="row"
          :dirty="dirty"
          @changed="load"
        /><TestPanel
          v-if="row && auth.hasPermission('rule:test:view')"
          :id="row.id"
        /><SimulationPanel
          v-if="simulationOpen"
          :document="draft"
          :edit-version="simulationEditVersion"
          @locate="locate"
          @result="flowTrace = $event.trace"
          @reset="flowTrace = []"
        />
      </div></template></ResizablePanels></template>
</template>
