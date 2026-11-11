export type RuleKind = "EXPRESSION" | "CONDITION" | "FLOW" | "DECISION_TABLE";
export type FieldType =
  "STRING" | "BOOLEAN" | "INTEGER" | "DECIMAL" | "INSTANT" | "OBJECT" | "LIST";
export interface Field {
  type: FieldType;
  required?: boolean;
  minimum?: unknown;
  maximum?: unknown;
  scale?: number | null;
  maxLength?: number | null;
  maxItems?: number | null;
  enumValues?: unknown[] | null;
  sensitive?: boolean;
  properties?: Record<string, Field> | null;
  items?: Field | null;
  defaultValue?: unknown;
}
export interface Schema {
  fields: Record<string, Field>;
  additionalProperties?: boolean;
}
export interface Scene {
  code: string;
  name: string;
  inputSchema: Schema;
  parameterPolicy: Schema;
  outputSchema: Field;
  allowedKinds: RuleKind[];
}
export interface Operand {
  source: "input" | "params" | "context" | "literal";
  path?: string | null;
  value?: unknown;
}
export interface Condition {
  id: string;
  operator: string;
  left?: Operand | null;
  right?: Operand | null;
  children?: Condition[] | null;
}
export type NodeKind =
  | "START"
  | "EXPRESSION"
  | "CONDITION"
  | "SWITCH"
  | "SUBRULE"
  | "TABLE"
  | "RESULT";
export interface FlowNode {
  id: string;
  kind: NodeKind;
  expression?: string | null;
  condition?: Condition | null;
  next?: string | null;
  onTrue?: string | null;
  onFalse?: string | null;
  subRuleVersionId?: string | null;
  inputBindings?: Record<string, string> | null;
  parameterBindings?: Record<string, string> | null;
  outputSchema?: Field | null;
  cases?: Record<string, string> | null;
}
export interface Flow {
  startNode: string;
  nodes: FlowNode[];
}
export interface TableColumn {
  id: string;
  source: Operand;
  schema: Field;
}
export interface Cell {
  operator: string;
  operand?: Operand | null;
}
export interface TableRow {
  id: string;
  cells: Record<string, Cell>;
  outputs: Record<string, string>;
}
export interface DecisionTable {
  hitPolicy: "UNIQUE" | "FIRST";
  inputs: TableColumn[];
  rows: TableRow[];
  defaultOutput?: Record<string, unknown> | null;
}
export interface Definition {
  kind: RuleKind;
  expression?: string | null;
  condition?: Condition | null;
  onMatch?: unknown;
  otherwise?: unknown;
  flow?: Flow | null;
  table?: DecisionTable | null;
}
export interface IntervalConstraint {
  id: string;
  parameter: string;
  lowerField: string;
  upperField: string;
  contiguous: boolean;
  firstLower?: unknown;
}
export interface RuleDocument {
  formatVersion: number;
  inputSchema: Schema;
  parameterSchema: Schema;
  outputSchema: Field;
  definition: Definition;
  parameterUi?: Record<
    string,
    { label: string; help?: string; group?: string; order?: number }
  > | null;
  examples: {
    name: string;
    input: Record<string, unknown>;
    params: Record<string, unknown>;
    expectedOutput: unknown;
  }[];
  intervalConstraints?: IntervalConstraint[] | null;
}
export interface Instance {
  code: string;
  name: string;
  templateVersionId: string;
  params: Record<string, unknown>;
  objectType: string;
  objectId: string;
  environment: string;
  startAt: string | null;
  endAt: string | null;
}
export interface Asset<T = Scene | RuleDocument | Instance> {
  id: string;
  kind: "SCENE" | "TEMPLATE" | "INSTANCE";
  code: string;
  name: string;
  revision: string;
  releaseRevision: string;
  state: string;
  activeVersionId: string | null;
  draft: T;
}
export interface Version<T = RuleDocument | Instance> {
  id: string;
  assetId: string;
  revision: string;
  contentHash: string;
  dependencyId: string | null;
  document: T;
  testRunId: string | null;
  caseSetHash: string | null;
}
export interface Problem {
  path: string;
  code: string;
  line?: number | null;
  column?: number | null;
}
export interface Trace {
  id: string;
  kind: string;
  evaluated: boolean;
  line?: number | null;
  column?: number | null;
  outcome: string;
}
export interface Execution {
  executionId: string;
  status: string;
  output: unknown;
  instanceRevision: string | null;
  releaseRevision: string | null;
  templateVersionId: string | null;
  contentHash: string | null;
  evaluatedAt: string;
  trace: Trace[];
  problems: Problem[];
}
export interface Release {
  id: string;
  assetId: string;
  versionId: string | null;
  releaseRevision: string;
  action: string;
}
export interface Migration {
  expectedRevision: string;
  templateVersionId: string;
  params: Record<string, unknown> | null;
  sampleInputs: Record<string, unknown>[];
}
export interface Preview {
  instanceId: string;
  baseRevision: string;
  canMigrate: boolean;
  proposed: Instance | null;
  changes: { path: string; kind: string }[];
  comparisons: {
    sample: number;
    before: Execution;
    after: Execution;
    changed: boolean;
  }[];
  problems: Problem[];
}
export interface TestCaseInput {
  name: string;
  input: Record<string, unknown>;
  params: Record<string, unknown>;
  expectedStatus: string;
  expectedOutput: unknown;
  expectedProblemCode: string | null;
  enabled: boolean;
  evaluatedAt: string;
}
export interface CaseView {
  id: string;
  templateId: string;
  revision: string;
  testcase: TestCaseInput;
}
export interface TestRun {
  id: string;
  templateId: string;
  versionId: string | null;
  contentHash: string;
  caseSetHash: string;
  total: number;
  passed: boolean;
  results: {
    caseId: string;
    name: string;
    actualStatus: string;
    passed: boolean;
    changed: boolean | null;
    problems: Problem[];
  }[];
}
export interface BatchReport {
  id: string;
  state: string;
  total: number;
  allPassed: boolean;
  results: {
    instanceId: string;
    passed: boolean;
    newRevision: string | null;
    error: string | null;
  }[];
  pendingInstanceIds: string[];
}
