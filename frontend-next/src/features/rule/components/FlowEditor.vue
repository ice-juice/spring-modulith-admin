<script setup lang="ts">
import {
  computed,
  defineAsyncComponent,
  inject,
  provide,
  ref,
  watch,
} from "vue";
import { useI18n } from "vue-i18n";
import { Button } from "@/components/ui/button";
import { confirmAction } from "@/lib/feedback";
import CodeEditor from "./CodeEditor.vue";
import JsonField from "./JsonField.vue";
import ConditionEditor from "./ConditionEditor.vue";
import RuleSourceInput from "./RuleSourceInput.vue";
import FieldConfig from "./FieldConfig.vue";
import SubruleConfig from "./SubruleConfig.vue";
import IdentifierInput from "./IdentifierInput.vue";
import { ruleReferencesKey } from "../references";
import { conditionMode, renameCase, stepReferences } from "../flowAuthoring";
import {
  newNode,
  removeNode,
  paletteKinds,
  flowDragType,
  connectFlow,
  disconnectFlow,
} from "../flow";
import type { Flow, FlowNode, NodeKind, Trace, RuleDocument } from "../types";
const Canvas = defineAsyncComponent(() => import("./FlowCanvas.vue"));
const flow = defineModel<Flow>({ required: true }),
  props = defineProps<{
    readonly?: boolean;
    locate?: string;
    line?: number | null;
    column?: number | null;
    trace?: Trace[];
    document?: RuleDocument;
  }>(),
  { t } = useI18n(),
  selected = ref(flow.value.startNode),
  kind = ref<NodeKind>("EXPRESSION"),
  error = ref("");
const placement = ref<{ id: string; x: number; y: number }>();
const inheritedReferences = inject(
  ruleReferencesKey,
  computed(() => []),
);
const references = computed(() => [
  ...inheritedReferences.value.filter(
    (entry) => !entry.label.startsWith("context.steps."),
  ),
  ...stepReferences(flow.value, selected.value),
]);
provide(ruleReferencesKey, references);
const node = computed(() =>
    flow.value.nodes.find((item) => item.id === selected.value),
  ),
  outcomes = computed(() =>
    Object.fromEntries(
      (props.trace || [])
        .filter((item) => item.kind === "FLOW")
        .map((item) => [item.id, item.outcome]),
    ),
  );
watch(
  () => props.locate,
  (path) => {
    const id = path?.split(/[/.]/)[0];
    if (id && flow.value.nodes.some((node) => node.id === id))
      selected.value = id;
  },
);
function add(type: NodeKind = kind.value, position?: { x: number; y: number }) {
  if (props.readonly || flow.value.nodes.length >= 64) return;
  const node = newNode(type, flow.value.nodes);
  placement.value = position ? { id: node.id, ...position } : undefined;
  flow.value = { ...flow.value, nodes: [...flow.value.nodes, node] };
  selected.value = node.id;
}
async function changeCondition(event: Event) {
  if (!node.value || props.readonly) return;
  const select = event.target as HTMLSelectElement,
    structured = select.value === "structured";
  if (
    !(await confirmAction(
      t("rule.changeConditionConfirm"),
      t("common.confirm"),
    ))
  ) {
    select.value = node.value.condition ? "structured" : "expression";
    return;
  }
  const changed = conditionMode(node.value, structured);
  flow.value = {
    ...flow.value,
    nodes: flow.value.nodes.map((item) =>
      item.id === changed.id ? changed : item,
    ),
  };
}
function addCase() {
  if (
    !node.value ||
    props.readonly ||
    Object.keys(node.value.cases || {}).length >= 16
  )
    return;
  let index = 1;
  while (Object.hasOwn(node.value.cases || {}, "option" + index)) index++;
  node.value.cases = { ...node.value.cases, ["option" + index]: "" };
}
function caseKey(old: string, value: string) {
  if (!node.value || props.readonly) return;
  try {
    flow.value = renameCase(flow.value, node.value.id, old, value);
    error.value = "";
  } catch {
    error.value = t("rule.invalidCase");
  }
}
function caseTarget(key: string, event: Event) {
  if (!node.value || props.readonly) return;
  const select = event.target as HTMLSelectElement;
  try {
    flow.value = select.value
      ? connectFlow(flow.value, node.value.id, select.value, "case:" + key)
      : disconnectFlow(flow.value, node.value.id, "case:" + key);
    error.value = "";
  } catch {
    select.value = node.value.cases?.[key] || "";
    error.value = t("rule.invalidConnection");
  }
}
function removeCase(key: string) {
  if (node.value && !props.readonly) {
    const cases = { ...node.value.cases };
    delete cases[key];
    node.value.cases = cases;
  }
}
async function remove() {
  if (
    props.readonly ||
    !(await confirmAction(
      t("rule.removeNodeConfirm"),
      t("common.confirm"),
      true,
    ))
  )
    return;
  try {
    flow.value = removeNode(flow.value, selected.value);
    selected.value = flow.value.startNode;
  } catch {
    error.value = t("rule.invalidJson");
  }
}
function drag(event: DragEvent, type: NodeKind) {
  if (props.readonly || !event.dataTransfer) return;
  event.dataTransfer.setData(flowDragType, type);
  event.dataTransfer.effectAllowed = "copy";
}
function exitChange(handle: string, event: Event) {
  if (!node.value || props.readonly) return;
  const input = event.target as HTMLSelectElement;
  try {
    flow.value = input.value
      ? connectFlow(flow.value, node.value.id, input.value, handle)
      : disconnectFlow(flow.value, node.value.id, handle);
    error.value = "";
  } catch {
    input.value =
      handle === "true"
        ? node.value.onTrue || ""
        : handle === "false"
          ? node.value.onFalse || ""
          : node.value.next || "";
    error.value = t("rule.invalidConnection");
  }
}
function update(value: unknown) {
  if (value && typeof value === "object" && "id" in value && "kind" in value) {
    const edited = value as FlowNode;
    if (edited.id !== selected.value) {
      error.value = t("rule.invalidJson");
      return;
    }
    flow.value = {
      ...flow.value,
      nodes: flow.value.nodes.map((item) =>
        item.id === selected.value ? edited : item,
      ),
    };
  }
}
</script>
<template>
  <section class="space-y-3">
    <div class="flex flex-wrap items-center gap-2">
      <label class="text-sm"
        >{{ t("rule.node")
        }}<select
          v-model="selected"
          :aria-label="t('rule.node')"
          class="ml-2 h-9 rounded-md border bg-card px-2"
        >
          <option v-for="item in flow.nodes" :key="item.id" :value="item.id">
            {{ item.id }}
          </option>
        </select></label
      ><template v-if="!readonly"
        ><select
          v-model="kind"
          :aria-label="t('rule.kind')"
          class="h-9 rounded-md border bg-card px-2"
        >
          <option
            v-for="type in [
              'EXPRESSION',
              'CONDITION',
              'SWITCH',
              'SUBRULE',
              'TABLE',
              'RESULT',
            ]"
            :key="type"
            :value="type"
          >
            {{ t("rule.nodeKinds." + type) }}
          </option></select
        ><Button
          type="button"
          variant="outline"
          size="sm"
          :disabled="flow.nodes.length >= 64"
          @click="add()"
          >{{ t("rule.addNode") }}</Button
        ></template
      >
    </div>
    <p class="text-xs text-muted-foreground">{{ t("rule.flowHint") }}</p>
    <p v-if="error" role="alert" class="text-xs text-destructive">
      {{ error }}
    </p>
    <div
      class="grid min-w-0 gap-3 xl:grid-cols-[136px_minmax(0,1fr)] 2xl:grid-cols-[136px_minmax(0,1fr)_320px]"
    >
      <aside
        class="min-w-0 self-start rounded-xl border bg-card p-3"
        :aria-label="t('rule.componentLibrary')"
      >
        <h3 class="mb-2 text-sm font-semibold">
          {{ t("rule.componentLibrary") }}
        </h3>
        <p class="mb-3 text-xs text-muted-foreground">
          {{ t("rule.paletteHint") }}
        </p>
        <div class="grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-1">
          <Button
            v-for="type in paletteKinds"
            :key="type"
            type="button"
            variant="outline"
            class="h-auto min-w-0 whitespace-normal px-2 py-3 text-xs"
            :draggable="!readonly"
            :disabled="readonly || flow.nodes.length >= 64"
            :data-node-kind="type"
            :aria-label="
              t('rule.addComponent', { kind: t('rule.nodeKinds.' + type) })
            "
            @dragstart="drag($event, type)"
            @click="add(type)"
            >{{ t("rule.nodeKinds." + type) }}</Button
          >
        </div>
      </aside>
      <Canvas
        v-model="flow"
        :readonly="readonly"
        :selected="selected"
        :outcomes="outcomes"
        :placement="placement"
        @select="selected = $event"
        @error="error = t('rule.invalidConnection')"
        @drop-node="add"
      />
      <section
        v-if="node"
        :key="node!.id"
        class="min-w-0 self-start space-y-3 rounded-xl border bg-card p-4 xl:col-span-2 2xl:col-span-1"
      >
        <div class="flex justify-between gap-2">
          <h3 class="text-sm font-semibold">
            {{ node!.id }}{{ t("rule.separator")
            }}{{ t("rule.nodeKinds." + node!.kind) }}
          </h3>
          <Button
            v-if="!readonly && node!.kind !== 'START'"
            type="button"
            size="sm"
            variant="ghost"
            @click="remove"
            >{{ t("rule.remove") }}</Button
          >
        </div>
        <section
          v-if="node!.kind === 'START' && document"
          class="space-y-3 text-xs"
        >
          <p class="text-muted-foreground">{{ t("rule.flowContractHint") }}</p>
          <h4 class="font-semibold">{{ t("rule.inputSchema") }}</h4>
          <p v-for="(field, key) in document.inputSchema.fields" :key="key">
            {{ key }}{{ t("rule.separator") }}{{ field.type
            }}{{ field.required ? t("rule.requiredSuffix") : "" }}
          </p>
          <h4 class="font-semibold">{{ t("rule.parameterSchema") }}</h4>
          <p v-for="(field, key) in document.parameterSchema.fields" :key="key">
            {{ key }}{{ t("rule.separator") }}{{ field.type }}
          </p>
          <p>
            {{ t("rule.finalOutput") }}{{ t("rule.separator")
            }}{{ document.outputSchema.type }}
          </p>
        </section>
        <label v-if="node!.kind === 'CONDITION'" class="grid gap-1 text-xs"
          >{{ t("rule.conditionMode")
          }}<select
            :value="node!.condition ? 'structured' : 'expression'"
            :disabled="readonly"
            :aria-label="t('rule.conditionMode')"
            class="h-9 min-w-0 rounded-md border bg-card px-2"
            @change="changeCondition"
          >
            <option value="structured">{{ t("rule.conditionBuilder") }}</option>
            <option value="expression">
              {{ t("rule.booleanExpression") }}
            </option>
          </select></label
        >
        <ConditionEditor
          v-if="node!.kind === 'CONDITION' && node!.condition"
          v-model="node!.condition"
          :readonly="readonly"
        />
        <RuleSourceInput
          v-if="['EXPRESSION', 'RESULT', 'SWITCH'].includes(node!.kind)"
          :model-value="node!.expression || ''"
          :label="t('rule.nodeValue')"
          :readonly="readonly"
          @update:model-value="node!.expression = $event"
        />
        <p
          v-if="node!.kind === 'RESULT' && document"
          class="text-xs text-muted-foreground"
        >
          {{ t("rule.finalOutput") }}{{ t("rule.separator")
          }}{{ document.outputSchema.type }}
        </p>
        <CodeEditor
          v-if="
            ['EXPRESSION', 'RESULT', 'CONDITION', 'SWITCH'].includes(
              node!.kind,
            ) && !node!.condition
          "
          v-model="node!.expression!"
          :readonly="readonly"
          :label="node!.id + ' · ' + t('rule.expression')"
          :line="line"
          :column="column"
        /><template
          v-for="exit in node!.kind === 'CONDITION'
            ? (['onTrue', 'onFalse'] as const)
            : node!.kind === 'RESULT'
              ? []
              : (['next'] as const)"
          :key="exit"
          ><label class="grid gap-1 text-xs"
            >{{ t("rule." + exit)
            }}<select
              :value="node[exit] || ''"
              :aria-label="node!.id + ' · ' + t('rule.' + exit)"
              :disabled="readonly"
              class="h-9 max-w-full rounded-md border bg-card px-2"
              @change="
                exitChange(
                  exit === 'onTrue'
                    ? 'true'
                    : exit === 'onFalse'
                      ? 'false'
                      : 'next',
                  $event,
                )
              "
            >
              <option value="">{{ t("rule.choose") }}</option>
              <option
                v-for="target in flow.nodes.filter(
                  (item) => item.id !== node!.id && item.id !== flow.startNode,
                )"
                :key="target.id"
                :value="target.id"
              >
                {{ target.id }}
              </option>
            </select></label
          ></template
        >
        <section
          v-if="node!.kind === 'SWITCH'"
          class="space-y-2 rounded-lg border p-3"
        >
          <h4 class="text-xs font-semibold">{{ t("rule.cases") }}</h4>
          <div
            v-for="(target, key) in node!.cases"
            :key="key"
            class="grid min-w-0 gap-2 rounded-md border p-2"
          >
            <IdentifierInput
              :value="key"
              :readonly="readonly"
              :label="t('rule.caseKey', { key })"
              pattern="[a-zA-Z0-9_-]{1,64}"
              @commit="caseKey(String(key), $event)"
            />
            <select
              :value="target"
              :disabled="readonly"
              :aria-label="t('rule.caseTarget', { key })"
              class="h-9 min-w-0 rounded-md border bg-card px-2 text-xs"
              @change="caseTarget(String(key), $event)"
            >
              <option value="">{{ t("rule.choose") }}</option>
              <option
                v-for="item in flow.nodes.filter(
                  (item) => item.id !== node!.id && item.id !== flow.startNode,
                )"
                :key="item.id"
                :value="item.id"
              >
                {{ item.id }}
              </option>
            </select>
            <Button
              v-if="!readonly"
              type="button"
              size="sm"
              variant="ghost"
              @click="removeCase(String(key))"
              >{{ t("rule.remove") }}</Button
            >
          </div>
          <Button
            v-if="!readonly"
            type="button"
            size="sm"
            variant="outline"
            :disabled="Object.keys(node!.cases || {}).length >= 16"
            @click="addCase"
            >{{ t("rule.addCase") }}</Button
          >
        </section>
        <template v-if="['SUBRULE', 'TABLE'].includes(node!.kind)">
          <SubruleConfig
            v-model="
              flow.nodes[flow.nodes.findIndex((item) => item.id === selected)]!
            "
            :readonly="readonly" />
          <details>
            <summary class="cursor-pointer text-xs">
              {{ t("rule.advanced") }}
            </summary>
            <label class="grid gap-1 text-xs"
              >{{ t("rule.subRuleVersionId")
              }}<input
                v-model="node!.subRuleVersionId"
                required
                pattern="[0-9]+"
                :readonly="readonly"
                class="h-9 min-w-0 rounded-md border bg-card px-2" /></label
            ><JsonField
              v-model="node!.inputBindings"
              :label="t('rule.inputBindings')"
              :readonly="readonly"
            /><JsonField
              v-model="node!.parameterBindings"
              :label="t('rule.parameterBindings')"
              :readonly="readonly"
            /></details></template
        ><FieldConfig
          v-if="node!.kind === 'EXPRESSION' && node!.outputSchema"
          v-model="node!.outputSchema"
          :label="node!.id + ' · ' + t('rule.outputSchema')"
          :readonly="readonly"
        />
        <FieldConfig
          v-if="['SUBRULE', 'TABLE'].includes(node!.kind) && node!.outputSchema"
          :model-value="node!.outputSchema"
          :label="node!.id + ' · ' + t('rule.outputSchema')"
          readonly
        />
        <section
          v-if="node!.kind !== 'START'"
          class="space-y-2 rounded-lg border p-3"
        >
          <h4 class="text-xs font-semibold">{{ t("rule.availableInputs") }}</h4>
          <p
            v-for="entry in references.filter(
              (entry) => entry.kind === 'field',
            )"
            :key="entry.label"
            class="break-all font-mono text-xs"
          >
            {{ entry.label }}{{ t("rule.separator") }}{{ entry.detail }}
          </p>
          <p class="text-xs text-muted-foreground">
            {{ t("rule.predecessorHint") }}
          </p>
        </section>
        <details>
          <summary class="cursor-pointer text-xs text-muted-foreground">
            {{ t("rule.advanced") }}
          </summary>
          <JsonField
            :model-value="node"
            :label="t('rule.node')"
            :readonly="readonly"
            @update:model-value="update"
          />
        </details>
      </section>
    </div>
  </section>
</template>
