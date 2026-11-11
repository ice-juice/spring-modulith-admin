package com.sma.admin.rule;

import java.util.List;

/** Authoring documents are data. Runtime objects from expression/flow libraries never enter the public API. */
public record RuleDefinition(Kind kind, String expression, Condition condition, Object onMatch, Object otherwise,
        Flow flow, DecisionTable table) {
    public RuleDefinition(Kind kind, String expression, Condition condition, Object onMatch, Object otherwise) {
        this(kind, expression, condition, onMatch, otherwise, null, null);
    }
    public RuleDefinition(Kind kind, String expression, Condition condition, Object onMatch, Object otherwise, Flow flow) {
        this(kind, expression, condition, onMatch, otherwise, flow, null);
    }
    public enum Kind { EXPRESSION, CONDITION, FLOW, DECISION_TABLE }
    public record Operand(String source, String path, Object value) {}
    public record Condition(String id, String operator, Operand left, Operand right, List<Condition> children) {}
    public enum HitPolicy { UNIQUE, FIRST }
    public record Column(String id, Operand source, RuleSchema.Field schema) {}
    public record Cell(String operator, Operand operand) {}
    public record Row(String id, java.util.Map<String, Cell> cells, java.util.Map<String, String> outputs) {}
    public record DecisionTable(HitPolicy hitPolicy, List<Column> inputs, List<Row> rows, java.util.Map<String, Object> defaultOutput) {}
    public enum NodeKind { START, EXPRESSION, CONDITION, SWITCH, SUBRULE, TABLE, RESULT }
    public record Flow(String startNode, List<Node> nodes) {}
    public record Node(String id, NodeKind kind, String expression, Condition condition, String next,
            String onTrue, String onFalse, Long subRuleVersionId, java.util.Map<String, String> inputBindings,
            java.util.Map<String, String> parameterBindings, RuleSchema.Field outputSchema,
            java.util.Map<String, String> cases) {
        public Node(String id, NodeKind kind, String expression, Condition condition, String next,
                String onTrue, String onFalse, Long subRuleVersionId, java.util.Map<String, String> inputBindings,
                java.util.Map<String, String> parameterBindings, RuleSchema.Field outputSchema) {
            this(id, kind, expression, condition, next, onTrue, onFalse, subRuleVersionId, inputBindings, parameterBindings, outputSchema, null);
        }
    }
    public static RuleDefinition expression(String source) {
        return new RuleDefinition(Kind.EXPRESSION, source, null, null, null);
    }
}
