import { computed, effectScope, nextTick, ref } from "vue";
import { describe, expect, it } from "vitest";
import { useRuleHistory } from "../useRuleHistory";
import { parseExact, printExact } from "../exact";

function setup(limits?: { steps: number; characters: number }) {
  const scope = effectScope(),
    value = ref("initial");
  const history = scope.run(() =>
    useRuleHistory(
      computed(() => value.value),
      (snapshot) => {
        value.value = snapshot;
      },
      limits,
    ),
  )!;
  history.reset(value.value);
  return { value, history, stop: () => scope.stop() };
}
describe("page-local rule editing history", () => {
  it("undoes a graph edit, redoes it, and drops abandoned redo branches", async () => {
    const { value, history, stop } = setup();
    value.value = "added-node";
    await nextTick();
    value.value = "connected-node";
    await nextTick();
    history.undo();
    expect(value.value).toBe("added-node");
    await nextTick();
    history.redo();
    expect(value.value).toBe("connected-node");
    history.undo();
    value.value = "different-connection";
    await nextTick();
    expect(history.canRedo.value).toBe(false);
    history.undo();
    expect(value.value).toBe("added-node");
    stop();
  });
  it("captures pending edits before undo and resets on a refreshed server draft", async () => {
    const { value, history, stop } = setup();
    value.value = "edited";
    history.undo();
    expect(value.value).toBe("initial");
    await nextTick();
    value.value = "new-server-draft";
    history.reset(value.value);
    await nextTick();
    expect(history.canUndo.value).toBe(false);
    expect(history.canRedo.value).toBe(false);
    stop();
  });
  it("bounds retained steps and character count without losing the latest snapshot", async () => {
    const { value, history, stop } = setup({ steps: 2, characters: 24 });
    for (const edit of ["step-one", "step-two", "step-three"]) {
      value.value = edit;
      await nextTick();
    }
    history.undo();
    expect(value.value).toBe("step-two");
    expect(history.canUndo.value).toBe(false);
    history.redo();
    expect(value.value).toBe("step-three");
    value.value = "x".repeat(30);
    await nextTick();
    expect(history.canUndo.value).toBe(false);
    expect(value.value).toHaveLength(30);
    stop();
  });
  it("retains exact decimal lexemes and string IDs through restoration", async () => {
    const { value, history, stop } = setup();
    const snapshot =
      '{"id":"9007199254740993123","amount":12345678901234567890.12345678}';
    value.value = printExact(parseExact(snapshot));
    history.reset(value.value);
    value.value = "changed";
    await nextTick();
    history.undo();
    expect(value.value).toBe(snapshot);
    stop();
  });
});
