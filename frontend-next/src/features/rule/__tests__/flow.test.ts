import { describe, expect, it } from "vitest";
import {
  connectFlow,
  disconnectFlow,
  edgesFor,
  newFlow,
  removeNode,
} from "../flow";
describe("authoring graph", () => {
  it("round trips condition, default and named switch exits", () => {
    const flow = newFlow();
    flow.nodes.push(
      { id: "branch", kind: "CONDITION", onTrue: "result", onFalse: "result" },
      {
        id: "switch",
        kind: "SWITCH",
        cases: { gold: "result" },
        next: "branch",
      },
    );
    expect(edgesFor(flow)).toHaveLength(5);
    expect(
      connectFlow(flow, "switch", "branch", "case:gold").nodes.at(-1)?.cases
        ?.gold,
    ).toBe("branch");
  });
  it("rejects cycles without mutating the input and removes dangling exits", () => {
    const flow = newFlow();
    flow.nodes.push({ id: "work", kind: "EXPRESSION", next: "result" });
    expect(() => connectFlow(flow, "work", "start", "next")).toThrow(
      "INVALID_EDGE",
    );
    const connected = connectFlow(flow, "start", "work", "next");
    connected.nodes.push({ id: "other", kind: "EXPRESSION", next: "work" });
    expect(() => connectFlow(connected, "work", "other", "next")).toThrow(
      "FLOW_CYCLE",
    );
    expect(flow.nodes[0]?.next).toBe("result");
    expect(removeNode(connected, "work").nodes[0]?.next).toBeNull();
    expect(() => removeNode(flow, "start")).toThrow();
  });
  it("rejects unknown handles and sources, and disconnects just the chosen branch", () => {
    const flow = newFlow();
    flow.nodes.push({
      id: "branch",
      kind: "CONDITION",
      onTrue: "result",
      onFalse: "result",
    });
    expect(() => connectFlow(flow, "missing", "result", "next")).toThrow();
    expect(() => connectFlow(flow, "branch", "result", "typo")).toThrow();
    const cleared = disconnectFlow(flow, "branch", "true");
    expect(cleared.nodes.at(-1)?.onTrue).toBeNull();
    expect(cleared.nodes.at(-1)?.onFalse).toBe("result");
    expect(flow.nodes.at(-1)?.onTrue).toBe("result");
  });
});
