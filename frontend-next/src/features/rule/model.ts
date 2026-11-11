import type {
  RuleDocument,
  Scene,
  RuleKind,
  Definition,
  Schema,
  Field,
} from "./types";
import { cloneExact } from "./exact";
import { newFlow } from "./flow";
export function definitionFor(kind: RuleKind): Definition {
  if (kind === "EXPRESSION") return { kind, expression: "null" };
  if (kind === "CONDITION")
    return {
      kind,
      condition: { id: "root", operator: "AND", children: [] },
      onMatch: true,
      otherwise: false,
    };
  if (kind === "FLOW") return { kind, flow: newFlow() };
  return {
    kind,
    table: { hitPolicy: "UNIQUE", inputs: [], rows: [], defaultOutput: {} },
  };
}
export function documentFor(scene: Scene): RuleDocument {
  return {
    formatVersion: 1,
    inputSchema: cloneExact(scene.inputSchema),
    parameterSchema: cloneExact(scene.parameterPolicy),
    outputSchema: cloneExact(scene.outputSchema),
    definition: definitionFor(scene.allowedKinds[0] || "EXPRESSION"),
    parameterUi: {},
    examples: [],
    intervalConstraints: [],
  };
}
export function valuesFor(schema: Schema): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(schema.fields).map(([key, field]) => [
      key,
      defaultFor(field),
    ]),
  );
}
function defaultFor(field: Field): unknown {
  return (
    field.defaultValue ??
    (field.type === "BOOLEAN"
      ? false
      : field.type === "OBJECT"
        ? valuesFor({ fields: field.properties || {} })
        : field.type === "LIST"
          ? []
          : null)
  );
}
export function validInstant(value: string) {
  return (
    !value ||
    (/(?:Z|[+-]\d\d:\d\d)$/.test(value) && !Number.isNaN(Date.parse(value)))
  );
}
