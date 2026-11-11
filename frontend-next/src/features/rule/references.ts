import type { ComputedRef, InjectionKey } from "vue";
import type { Field, RuleDocument } from "./types";

export interface RuleReference {
  label: string;
  kind: "field" | "function";
  detail: string;
  insertText: string;
}
export const ruleReferencesKey: InjectionKey<ComputedRef<RuleReference[]>> =
  Symbol("rule-authoring-references");
const signatures: Record<string, string[]> = {
  min: ["a", "b"],
  max: ["a", "b"],
  abs: ["value"],
  round: ["value", "scale"],
  size: ["value"],
  contains: ["value", "item"],
  intervalValue: [
    "rows",
    "point",
    "lowerField",
    "upperField",
    "outputField",
    "fallback",
  ],
};
export function referencesFor(
  document: RuleDocument | undefined,
  functions: string[],
): RuleReference[] {
  const result: RuleReference[] = [];
  function fields(prefix: string, values: Record<string, Field>, depth = 0) {
    for (const [key, field] of Object.entries(values)) {
      if (result.length >= 256) return;
      if (!/^[a-zA-Z][a-zA-Z0-9_]{0,63}$/.test(key)) continue;
      const label = prefix + "." + key;
      result.push({
        label,
        kind: "field",
        detail: field.type,
        insertText: label,
      });
      if (field.type === "OBJECT" && field.properties && depth < 8)
        fields(label, field.properties, depth + 1);
    }
  }
  if (document) {
    fields("input", document.inputSchema.fields);
    fields("params", document.parameterSchema.fields);
  }
  for (const name of [...new Set(functions)].slice(0, 64)) {
    if (!/^[a-zA-Z][a-zA-Z0-9_]{0,63}$/.test(name)) continue;
    const args = signatures[name] || [];
    result.push({
      label: name,
      kind: "function",
      detail: `${name}(${args.join(", ")})`,
      insertText:
        name +
        "(" +
        args
          .map((arg, index) => "${" + (index + 1) + ":" + arg + "}")
          .join(", ") +
        ")",
    });
  }
  return result;
}
