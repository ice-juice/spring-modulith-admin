import type { Flow, FlowNode } from "./types";
export const flowDragType = "application/x-sma-rule-node";
export const paletteKinds = [
  "EXPRESSION",
  "CONDITION",
  "SWITCH",
  "SUBRULE",
  "TABLE",
  "RESULT",
] as const;
export function nodeExits(
  node: FlowNode,
): [string, string | null | undefined][] {
  return node.kind === "RESULT"
    ? []
    : node.kind === "CONDITION"
      ? [
          ["true", node.onTrue],
          ["false", node.onFalse],
        ]
      : node.kind === "SWITCH"
        ? [
            ["next", node.next],
            ...Object.entries(node.cases || {}).map(
              ([key, value]): [string, string] => ["case:" + key, value],
            ),
          ]
        : [["next", node.next]];
}
export interface FlowEdge {
  id: string;
  source: string;
  target: string;
  sourceHandle: string;
  label: string;
}
export function edgesFor(flow: Flow): FlowEdge[] {
  return flow.nodes.flatMap((node) => {
    const exits = nodeExits(node);
    return exits
      .filter((exit): exit is [string, string] => !!exit[1])
      .map(([handle, target]) => ({
        id: node.id + ":" + handle,
        source: node.id,
        target,
        sourceHandle: handle,
        label: handle,
      }));
  });
}
export function connectFlow(
  flow: Flow,
  source: string,
  target: string,
  handle: string,
): Flow {
  const origin = flow.nodes.find((node) => node.id === source);
  if (
    !origin ||
    !nodeExits(origin).some(([exit]) => exit === handle) ||
    source === target ||
    target === flow.startNode ||
    !flow.nodes.some((node) => node.id === target)
  )
    throw new Error("INVALID_EDGE");
  const nodes = flow.nodes.map((node) => {
    if (node.id !== source) return node;
    if (node.kind === "RESULT") throw new Error("INVALID_EDGE");
    if (node.kind === "CONDITION")
      return {
        ...node,
        ...(handle === "true" ? { onTrue: target } : { onFalse: target }),
      };
    if (node.kind === "SWITCH" && handle.startsWith("case:"))
      return { ...node, cases: { ...node.cases, [handle.slice(5)]: target } };
    return { ...node, next: target };
  });
  const candidate = { ...flow, nodes },
    active = new Set<string>(),
    done = new Set<string>();
  function visit(id: string) {
    if (active.has(id)) throw new Error("FLOW_CYCLE");
    if (done.has(id)) return;
    active.add(id);
    edgesFor(candidate)
      .filter((edge) => edge.source === id)
      .forEach((edge) => visit(edge.target));
    active.delete(id);
    done.add(id);
  }
  nodes.forEach((node) => visit(node.id));
  return candidate;
}
export function disconnectFlow(
  flow: Flow,
  source: string,
  handle: string,
): Flow {
  return {
    ...flow,
    nodes: flow.nodes.map((node) => {
      if (node.id !== source) return node;
      if (node.kind === "CONDITION")
        return {
          ...node,
          ...(handle === "true" ? { onTrue: null } : { onFalse: null }),
        };
      if (node.kind === "SWITCH" && handle.startsWith("case:"))
        return { ...node, cases: { ...node.cases, [handle.slice(5)]: "" } };
      return { ...node, next: null };
    }),
  };
}
export function removeNode(flow: Flow, id: string): Flow {
  if (id === flow.startNode) throw new Error("START_REQUIRED");
  return {
    ...flow,
    nodes: flow.nodes
      .filter((node) => node.id !== id)
      .map((node) => ({
        ...node,
        next: node.next === id ? null : node.next,
        onTrue: node.onTrue === id ? null : node.onTrue,
        onFalse: node.onFalse === id ? null : node.onFalse,
        cases: node.cases
          ? Object.fromEntries(
              Object.entries(node.cases).filter(([, target]) => target !== id),
            )
          : node.cases,
      })),
  };
}
export function newFlow(): Flow {
  return {
    startNode: "start",
    nodes: [
      { id: "start", kind: "START", next: "result" },
      { id: "result", kind: "RESULT", expression: "null" },
    ],
  };
}
export function newNode(kind: FlowNode["kind"], nodes: FlowNode[]): FlowNode {
  let index = 1;
  while (nodes.some((node) => node.id === "node" + index)) index++;
  return {
    id: "node" + index,
    kind,
    expression: ["EXPRESSION", "RESULT", "CONDITION", "SWITCH"].includes(kind)
      ? kind === "CONDITION"
        ? "true"
        : "null"
      : null,
    outputSchema:
      kind === "EXPRESSION" ? { type: "STRING", required: false } : null,
    ...(kind === "SWITCH" ? { cases: { option: "" } } : {}),
  };
}
