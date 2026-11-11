<script setup lang="ts">
import { computed, ref, shallowRef, watch } from "vue";
import {
  VueFlow,
  Handle,
  Position,
  useVueFlow,
  type Node,
  type Connection,
} from "@vue-flow/core";
import "@vue-flow/core/dist/style.css";
import "@vue-flow/core/dist/theme-default.css";
import { useI18n } from "vue-i18n";
import { Button } from "@/components/ui/button";
import {
  edgesFor,
  connectFlow,
  disconnectFlow,
  flowDragType,
  paletteKinds,
} from "../flow";
import type { Flow, NodeKind } from "../types";
const props = defineProps<{
  readonly?: boolean;
  selected?: string;
  outcomes?: Record<string, string>;
  placement?: { id: string; x: number; y: number };
}>();
const flow = defineModel<Flow>({ required: true }),
  emit = defineEmits<{
    select: [id: string];
    error: [message: string];
    dropNode: [kind: NodeKind, position: { x: number; y: number }];
  }>(),
  { t } = useI18n();
const nodes = shallowRef<Node[]>([]);
const graphId = "rule-flow-" + crypto.randomUUID(),
  viewport = useVueFlow(graphId);
watch(
  [
    () => flow.value.nodes,
    () => props.selected,
    () => props.outcomes,
    () => t("rule.node"),
  ],
  ([list]) => {
    nodes.value = list.map((node, index) => ({
      id: node.id,
      label: node.id + " · " + t("rule.nodeKinds." + node.kind),
      position:
        nodes.value.find((item) => item.id === node.id)?.position ||
        (props.placement?.id === node.id
          ? { x: props.placement.x, y: props.placement.y }
          : {
              x: 80 + (index % 3) * 230,
              y: 45 + Math.floor(index / 3) * 150,
            }),
      data: {
        kind: node.kind,
        exits:
          node.kind === "RESULT"
            ? []
            : node.kind === "CONDITION"
              ? ["true", "false"]
              : node.kind === "SWITCH"
                ? [
                    "next",
                    ...Object.keys(node.cases || {}).map(
                      (key) => "case:" + key,
                    ),
                  ]
                : ["next"],
      },
      class: props.outcomes?.[node.id] === "SKIPPED" ? "rule-node-skipped" : "",
      selected: props.selected === node.id,
    }));
  },
  { deep: true, immediate: true },
);
const selectedEdge = ref("");
const edges = computed(() =>
  edgesFor(flow.value).map((edge) => ({
    ...edge,
    label: exitLabel(edge.sourceHandle),
    selected: edge.id === selectedEdge.value,
  })),
);
function exitLabel(exit: string) {
  return exit === "true"
    ? t("rule.onTrue")
    : exit === "false"
      ? t("rule.onFalse")
      : exit === "next"
        ? t("rule.next")
        : exit.slice(5);
}
function disconnect() {
  const edge = edges.value.find((edge) => edge.id === selectedEdge.value);
  if (!props.readonly && edge)
    flow.value = disconnectFlow(flow.value, edge.source, edge.sourceHandle);
  selectedEdge.value = "";
}
function dragOver(event: DragEvent) {
  if (!props.readonly && event.dataTransfer?.types.includes(flowDragType)) {
    event.preventDefault();
    event.dataTransfer.dropEffect = "copy";
  }
}
function drop(event: DragEvent) {
  if (props.readonly) return;
  const kind = event.dataTransfer?.getData(flowDragType);
  if (!paletteKinds.some((value) => value === kind)) return;
  event.preventDefault();
  const position = viewport.screenToFlowCoordinate({
    x: event.clientX,
    y: event.clientY,
  });
  emit("dropNode", kind as NodeKind, position);
}
function connect(connection: Connection) {
  if (props.readonly || !connection.source || !connection.target) return;
  try {
    flow.value = connectFlow(
      flow.value,
      connection.source,
      connection.target,
      connection.sourceHandle || "next",
    );
  } catch (cause) {
    emit("error", (cause as Error).message);
  }
}
function arrange() {
  nodes.value.forEach((node, index) => {
    node.position = {
      x: 80 + (index % 3) * 230,
      y: 45 + Math.floor(index / 3) * 150,
    };
  });
  nodes.value = [...nodes.value];
  void viewport.fitView({ padding: 0.2 });
}
</script>
<template>
  <div
    class="rule-canvas relative h-[460px] min-w-0 overflow-hidden rounded-xl border bg-muted/30"
    :aria-label="t('rule.flowCanvas')"
    @dragover="dragOver"
    @drop="drop"
  >
    <VueFlow
      :id="graphId"
      v-model:nodes="nodes"
      :edges="edges"
      :nodes-draggable="!readonly"
      :nodes-connectable="!readonly"
      :edges-updatable="false"
      :delete-key-code="null"
      :max-zoom="1.2"
      fit-view-on-init
      @connect="connect"
      @node-click="emit('select', $event.node.id)"
      @edge-click="selectedEdge = $event.edge.id"
      @pane-click="selectedEdge = ''"
      ><template #node-default="{ id, label, data }"
        ><div class="relative">
          <Handle
            v-if="data.kind !== 'START'"
            type="target"
            :position="Position.Top"
            :aria-label="id + ' · ' + t('rule.input')"
          /><Button
            type="button"
            variant="outline"
            class="h-auto min-w-32 whitespace-normal border-primary/30 bg-card px-4 py-3 shadow-sm"
            @click="emit('select', id)"
            >{{ label }}</Button
          ><Handle
            v-for="(exit, index) in data.exits"
            :id="exit"
            :key="exit"
            type="source"
            :position="Position.Bottom"
            :style="{
              left: ((Number(index) + 1) / (data.exits.length + 1)) * 100 + '%',
            }"
            :aria-label="id + ' · ' + exitLabel(exit)"
          /></div></template
    ></VueFlow>
    <div
      class="absolute bottom-3 left-3 right-3 z-10 flex flex-wrap items-center gap-1 rounded-lg border bg-card p-1 shadow-sm"
    >
      <Button
        type="button"
        size="sm"
        variant="ghost"
        :aria-label="t('rule.zoomIn')"
        @click="viewport.zoomIn()"
        >{{ t("rule.zoomIn") }}</Button
      ><Button
        type="button"
        size="sm"
        variant="ghost"
        :aria-label="t('rule.zoomOut')"
        @click="viewport.zoomOut()"
        >{{ t("rule.zoomOut") }}</Button
      ><Button type="button" size="sm" variant="ghost" @click="arrange">{{
        t("rule.arrange")
      }}</Button>
      <select
        v-if="edges.length && !readonly"
        v-model="selectedEdge"
        :aria-label="t('rule.connections')"
        class="h-8 min-w-0 max-w-48 rounded-md border bg-card px-2 text-xs"
      >
        <option value="">{{ t("rule.connections") }}</option>
        <option v-for="edge in edges" :key="edge.id" :value="edge.id">
          {{ edge.source }}{{ t("rule.separator") }}{{ edge.label
          }}{{ t("rule.separator") }}{{ edge.target }}
        </option>
      </select>
      <Button
        v-if="selectedEdge && !readonly"
        type="button"
        size="sm"
        variant="ghost"
        @click="disconnect"
        >{{ t("rule.disconnect") }}</Button
      >
    </div>
  </div>
</template>
<style>
.rule-canvas .vue-flow__node-default {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--foreground);
}
.rule-canvas .vue-flow__edge-path {
  stroke: var(--muted-foreground);
  stroke-width: 1.5;
}
.rule-canvas .vue-flow__edge-text {
  fill: var(--foreground);
}
.rule-canvas .vue-flow__edge-textbg {
  fill: var(--card);
}
.rule-canvas .vue-flow__controls-button {
  background: var(--card);
  color: var(--foreground);
}
.rule-canvas .vue-flow__node.selected button {
  outline: 2px solid var(--primary);
  outline-offset: 3px;
}
.rule-canvas .rule-node-skipped {
  opacity: 0.65;
}
.rule-canvas .vue-flow__handle {
  width: 12px;
  height: 12px;
  background: var(--primary);
  border: 2px solid var(--card);
}
</style>
