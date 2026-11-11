import type {
  Definition,
  Field,
  RuleDocument,
  RuleKind,
  Scene,
  Schema,
} from "./types";
import { cloneExact, printExact } from "./exact";
import { documentFor, valuesFor } from "./model";

export const sampleIds = [
  "ratio",
  "eligibility",
  "reduction",
  "tiers",
] as const;
export type SampleId = (typeof sampleIds)[number];
type Translate = (key: string) => string;
const kinds: Record<SampleId, RuleKind> = {
  ratio: "EXPRESSION",
  eligibility: "CONDITION",
  reduction: "FLOW",
  tiers: "DECISION_TABLE",
};
const amount = (): Field => ({
  type: "DECIMAL",
  required: true,
  minimum: 0,
  scale: 2,
});
const money = (defaultValue: number): Field => ({ ...amount(), defaultValue });
const ratio = (defaultValue: number): Field => ({
  type: "DECIMAL",
  required: true,
  minimum: 0,
  maximum: 1,
  scale: 4,
  defaultValue,
});
export function isSampleId(value: string): value is SampleId {
  return sampleIds.some((id) => id === value);
}
export function sampleScene(id: SampleId, t: Translate): Scene {
  const parameters: Record<SampleId, Record<string, Field>> = {
    ratio: { ratio: ratio(0.9) },
    eligibility: { threshold: money(100) },
    reduction: { threshold: money(100), reduction: money(10) },
    tiers: {
      highThreshold: money(200),
      lowThreshold: money(100),
      highRatio: ratio(0.8),
      lowRatio: ratio(0.9),
    },
  };
  return {
    code: "example_" + id,
    name: t("rule.sampleTitles." + id),
    inputSchema: {
      fields: {
        amount: amount(),
        ...(id === "eligibility"
          ? { member: { type: "BOOLEAN" as const, required: true } }
          : {}),
      },
      additionalProperties: false,
    },
    parameterPolicy: { fields: parameters[id], additionalProperties: false },
    outputSchema:
      id === "eligibility"
        ? { type: "BOOLEAN", required: true }
        : id === "tiers"
          ? {
              type: "OBJECT",
              required: true,
              properties: {
                payable: { ...amount(), required: false },
                rate: {
                  type: "DECIMAL",
                  required: true,
                  minimum: 0,
                  maximum: 1,
                  scale: 4,
                },
              },
            }
          : amount(),
    allowedKinds: [kinds[id]],
  };
}
function contractField(field: Field): unknown {
  return {
    type: field.type,
    required: !!field.required,
    sensitive: !!field.sensitive,
    minimum: field.minimum ?? null,
    maximum: field.maximum ?? null,
    scale: field.scale ?? null,
    maxLength: field.maxLength ?? null,
    maxItems: field.maxItems ?? null,
    enumValues: field.enumValues ?? null,
    defaultValue: field.defaultValue ?? null,
    properties: field.properties ? contractFields(field.properties) : null,
    items: field.items ? contractField(field.items) : null,
  };
}
function contractFields(fields: Record<string, Field>) {
  return Object.fromEntries(
    Object.keys(fields)
      .sort()
      .map((key) => [key, contractField(fields[key]!)]),
  );
}
function contractSchema(schema: Schema) {
  return {
    fields: contractFields(schema.fields),
    additionalProperties: !!schema.additionalProperties,
  };
}
export function matchingSamples(scene: Scene | undefined): SampleId[] {
  if (!scene) return [];
  return sampleIds.filter((id) => {
    const expected = sampleScene(id, (key) => key);
    return (
      scene.allowedKinds.includes(kinds[id]) &&
      printExact(contractSchema(scene.inputSchema)) ===
        printExact(contractSchema(expected.inputSchema)) &&
      printExact(contractSchema(scene.parameterPolicy)) ===
        printExact(contractSchema(expected.parameterPolicy)) &&
      printExact(contractField(scene.outputSchema)) ===
        printExact(contractField(expected.outputSchema))
    );
  });
}
export function sampleDocument(
  id: SampleId,
  scene: Scene,
  t: Translate,
): RuleDocument {
  if (!matchingSamples(scene).includes(id))
    throw new Error("SAMPLE_CONTRACT_MISMATCH");
  const doc = documentFor(scene),
    params = valuesFor(doc.parameterSchema);
  const definitions: Record<SampleId, Definition> = {
    ratio: {
      kind: "EXPRESSION",
      expression: "round(input.amount * params.ratio, 2)",
    },
    eligibility: {
      kind: "CONDITION",
      condition: {
        id: "eligible",
        operator: "AND",
        children: [
          {
            id: "member",
            operator: "EQ",
            left: { source: "input", path: "member" },
            right: { source: "literal", value: true },
          },
          {
            id: "threshold",
            operator: "GE",
            left: { source: "input", path: "amount" },
            right: { source: "params", path: "threshold" },
          },
        ],
      },
      onMatch: true,
      otherwise: false,
    },
    reduction: {
      kind: "FLOW",
      flow: {
        startNode: "start",
        nodes: [
          { id: "start", kind: "START", next: "threshold" },
          {
            id: "threshold",
            kind: "CONDITION",
            expression: "input.amount >= params.threshold",
            onTrue: "discount",
            onFalse: "original",
          },
          {
            id: "discount",
            kind: "RESULT",
            expression: "round(max(input.amount - params.reduction, 0), 2)",
          },
          { id: "original", kind: "RESULT", expression: "input.amount" },
        ],
      },
    },
    tiers: {
      kind: "DECISION_TABLE",
      table: {
        hitPolicy: "FIRST",
        defaultOutput: { payable: null, rate: 1 },
        inputs: [
          {
            id: "amount",
            source: { source: "input", path: "amount" },
            schema: amount(),
          },
        ],
        rows: [
          {
            id: "high",
            cells: {
              amount: {
                operator: "GE",
                operand: { source: "params", path: "highThreshold" },
              },
            },
            outputs: {
              payable: "round(input.amount * params.highRatio, 2)",
              rate: "params.highRatio",
            },
          },
          {
            id: "low",
            cells: {
              amount: {
                operator: "GE",
                operand: { source: "params", path: "lowThreshold" },
              },
            },
            outputs: {
              payable: "round(input.amount * params.lowRatio, 2)",
              rate: "params.lowRatio",
            },
          },
          {
            id: "original",
            cells: { amount: { operator: "ANY" } },
            outputs: { payable: "input.amount", rate: "1" },
          },
        ],
      },
    },
  };
  const examples: Record<
    SampleId,
    Array<{
      input: Record<string, unknown>;
      params?: Record<string, unknown>;
      expectedOutput: unknown;
    }>
  > = {
    ratio: [
      { input: { amount: 100 }, expectedOutput: 90 },
      { input: { amount: 0 }, expectedOutput: 0 },
      {
        input: { amount: 19.99 },
        params: { ratio: 0.85 },
        expectedOutput: 16.99,
      },
    ],
    eligibility: [
      { input: { amount: 100, member: true }, expectedOutput: true },
      { input: { amount: 99.99, member: true }, expectedOutput: false },
      { input: { amount: 200, member: false }, expectedOutput: false },
    ],
    reduction: [
      { input: { amount: 100 }, expectedOutput: 90 },
      { input: { amount: 99.99 }, expectedOutput: 99.99 },
      {
        input: { amount: 5 },
        params: { threshold: 0, reduction: 10 },
        expectedOutput: 0,
      },
    ],
    tiers: [
      { input: { amount: 200 }, expectedOutput: { payable: 160, rate: 0.8 } },
      { input: { amount: 100 }, expectedOutput: { payable: 90, rate: 0.9 } },
      { input: { amount: 99.99 }, expectedOutput: { payable: 99.99, rate: 1 } },
    ],
  };
  doc.definition = definitions[id];
  doc.parameterUi = Object.fromEntries(
    Object.keys(scene.parameterPolicy.fields).map((key, index) => [
      key,
      {
        label: t("rule.sampleParameters." + key),
        help: t("rule.sampleParameterHelp." + key),
        order: index,
      },
    ]),
  );
  doc.examples = examples[id].map((example, index) => ({
    name: t("rule.sampleTitles." + id) + " " + (index + 1),
    input: example.input,
    params: { ...params, ...example.params },
    expectedOutput: example.expectedOutput,
  }));
  return cloneExact(doc);
}
