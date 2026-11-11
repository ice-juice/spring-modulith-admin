import type { Field, Flow, FlowNode } from "./types";
import { nodeExits } from "./flow";
import type { RuleReference } from "./references";

// Fail closed for incomplete/cyclic graphs. Branch siblings are never definite predecessors.
export function predecessors(flow: Flow, target: string): Set<string> {
  const nodes = new Map(flow.nodes.map((node) => [node.id, node]));
  if (
    !nodes.has(flow.startNode) ||
    !nodes.has(target) ||
    flow.nodes.length > 64
  )
    return new Set();
  const reached = new Set<string>(),
    active = new Set<string>();
  function visit(id: string, depth: number) {
    if (!nodes.has(id) || depth > 32 || active.has(id))
      throw new Error("INVALID_GRAPH");
    if (reached.has(id)) return;
    active.add(id);
    for (const [, next] of nodeExits(nodes.get(id)!))
      if (next) visit(next, depth + 1);
    active.delete(id);
    reached.add(id);
  }
  const memo = new Map<string, Set<string>>();
  function guaranteed(id: string): Set<string> {
    if (memo.has(id)) return memo.get(id)!;
    if (id === flow.startNode) return new Set();
    const incoming = flow.nodes.filter(
      (node) =>
        reached.has(node.id) && nodeExits(node).some(([, next]) => next === id),
    );
    let result: Set<string> | undefined;
    for (const node of incoming) {
      const candidate = new Set([...guaranteed(node.id), node.id]);
      result = result
        ? new Set([...result].filter((key) => candidate.has(key)))
        : candidate;
    }
    const values = result || new Set<string>();
    memo.set(id, values);
    return values;
  }
  try {
    visit(flow.startNode, 0);
    return reached.has(target) ? guaranteed(target) : new Set();
  } catch {
    return new Set();
  }
}
export function stepReferences(flow: Flow, target: string): RuleReference[] {
  const allowed = predecessors(flow, target),
    result: RuleReference[] = [];
  function append(path: string, field: Field, depth = 0) {
    if (result.length >= 256 || depth > 8) return;
    result.push({
      label: path,
      kind: "field",
      detail: field.type,
      insertText: path,
    });
    if (field.type === "OBJECT")
      for (const [key, child] of Object.entries(field.properties || {}))
        append(path + "." + key, child, depth + 1);
  }
  for (const node of flow.nodes) {
    if (!allowed.has(node.id) || node.kind === "START") continue;
    const field =
      node.outputSchema ||
      (node.kind === "CONDITION" ? { type: "BOOLEAN" as const } : undefined);
    if (field) append("context.steps." + node.id, field);
  }
  return result;
}
export function conditionMode(node: FlowNode, structured: boolean): FlowNode {
  return {
    ...node,
    expression: structured ? null : "true",
    condition: structured
      ? { id: node.id + "_condition", operator: "AND", children: [] }
      : null,
  };
}
export function renameCase(
  flow: Flow,
  nodeId: string,
  old: string,
  key: string,
): Flow {
  const node = flow.nodes.find((node) => node.id === nodeId);
  if (
    !node?.cases ||
    !/^[a-zA-Z0-9_-]{1,64}$/.test(key) ||
    (old !== key && Object.hasOwn(node.cases, key))
  )
    throw new Error("INVALID_CASE");
  return {
    ...flow,
    nodes: flow.nodes.map((node) =>
      node.id !== nodeId
        ? node
        : {
            ...node,
            cases: Object.fromEntries(
              Object.entries(node.cases!).map(([name, target]) => [
                name === old ? key : name,
                target,
              ]),
            ),
          },
    ),
  };
}
