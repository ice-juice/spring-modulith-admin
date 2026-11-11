import { describe, expect, it } from "vitest";
import {
  conditionMode,
  predecessors,
  renameCase,
  stepReferences,
} from "../flowAuthoring";
import type { Flow } from "../types";
const graph = (): Flow => ({
  startNode: "start",
  nodes: [
    { id: "start", kind: "START", next: "branch" },
    {
      id: "branch",
      kind: "CONDITION",
      expression: "true",
      onTrue: "yes",
      onFalse: "no",
    },
    {
      id: "yes",
      kind: "EXPRESSION",
      outputSchema: {
        type: "OBJECT",
        properties: { amount: { type: "DECIMAL" } },
      },
      next: "finish",
    },
    {
      id: "no",
      kind: "EXPRESSION",
      outputSchema: { type: "DECIMAL" },
      next: "finish",
    },
    { id: "finish", kind: "RESULT", expression: "1" },
  ],
});
describe("visual flow configuration", () => {
  it("offers only guaranteed outputs after exclusive branches and no sibling data", () => {
    const flow = graph();
    expect([...predecessors(flow, "finish")].sort()).toEqual([
      "branch",
      "start",
    ]);
    expect(stepReferences(flow, "finish").map((item) => item.label)).toEqual([
      "context.steps.branch",
    ]);
    flow.nodes[2]!.next = "later";
    flow.nodes.push({
      id: "later",
      kind: "EXPRESSION",
      outputSchema: { type: "DECIMAL" },
      next: "finish",
    });
    expect(stepReferences(flow, "later").map((item) => item.label)).toContain(
      "context.steps.yes.amount",
    );
    expect(
      stepReferences(flow, "later").map((item) => item.label),
    ).not.toContain("context.steps.no");
  });
  it("fails closed for a cycle, missing node or unreachable selection", () => {
    const flow = graph();
    flow.nodes[2]!.next = "branch";
    expect(stepReferences(flow, "finish")).toEqual([]);
    flow.nodes[2]!.next = "missing";
    expect(predecessors(flow, "finish").size).toBe(0);
    expect(predecessors(graph(), "missing").size).toBe(0);
  });
  it("switches conditions without retaining both expression and structured content", () => {
    const node = graph().nodes[1]!;
    const structured = conditionMode(node, true);
    expect(structured.expression).toBeNull();
    expect(structured.condition?.operator).toBe("AND");
    expect(node.expression).toBe("true");
    const expression = conditionMode(structured, false);
    expect(expression.condition).toBeNull();
    expect(expression.expression).toBe("true");
  });
  it("renames a switch port preserving the edge and rejects invalid or duplicate values", () => {
    const flow = graph();
    flow.nodes.push({
      id: "choice",
      kind: "SWITCH",
      next: "finish",
      cases: { gold: "yes", other: "no" },
    });
    const changed = renameCase(flow, "choice", "gold", "member");
    expect(changed.nodes.at(-1)?.cases).toEqual({ member: "yes", other: "no" });
    expect(flow.nodes.at(-1)?.cases?.gold).toBe("yes");
    expect(() => renameCase(flow, "choice", "gold", "other")).toThrow();
    expect(() => renameCase(flow, "choice", "gold", "invalid.name")).toThrow();
  });
});
