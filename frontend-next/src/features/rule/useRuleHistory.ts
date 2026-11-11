import { computed, ref, shallowRef, watch, type ComputedRef } from "vue";

// Page-local snapshots only: no browser storage, simulation input or shared user state.
export function useRuleHistory(
  source: ComputedRef<string | undefined>,
  apply: (snapshot: string) => void,
  limits = { steps: 50, characters: 2 * 1024 * 1024 },
) {
  const entries = shallowRef<string[]>([]),
    cursor = ref(-1);
  function reset(snapshot: string) {
    entries.value = [snapshot];
    cursor.value = 0;
  }
  function capture() {
    const snapshot = source.value;
    if (snapshot === undefined || snapshot === entries.value[cursor.value])
      return;
    const next = [...entries.value.slice(0, cursor.value + 1), snapshot];
    let characters = next.reduce((sum, entry) => sum + entry.length, 0);
    while (
      next.length > 1 &&
      (next.length > limits.steps + 1 || characters > limits.characters)
    ) {
      characters -= next.shift()!.length;
    }
    entries.value = next;
    cursor.value = next.length - 1;
  }
  watch(source, capture);
  function move(offset: number) {
    capture();
    const target = cursor.value + offset;
    if (target < 0 || target >= entries.value.length) return;
    cursor.value = target;
    apply(entries.value[target]!);
  }
  return {
    reset,
    undo: () => move(-1),
    redo: () => move(1),
    canUndo: computed(() => cursor.value > 0),
    canRedo: computed(
      () => cursor.value >= 0 && cursor.value < entries.value.length - 1,
    ),
  };
}
