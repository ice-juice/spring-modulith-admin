package com.sma.admin.rule.internal.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.sma.admin.rule.*;
import com.sma.admin.rule.RuleDefinition.*;

/** Platform decision tables, intentionally not advertised as a complete DMN/FEEL implementation. */
final class DecisionTableEngine {
    private static final Set<String> OPS = Set.of("ANY", "EQ", "NE", "GT", "GE", "LT", "LE", "IN", "CONTAINS", "IS_NULL", "IS_NOT_NULL");
    private DecisionTableEngine() {}
    static void validate(RuleTemplateDocument document, ExpressionEngine expressions) {
        var table = document.definition().table();
        if (table == null || table.hitPolicy() == null || table.inputs() == null || table.inputs().isEmpty()
                || table.inputs().size() > 16 || table.rows() == null || table.rows().isEmpty() || table.rows().size() > 200
                || document.outputSchema().type() != RuleSchema.Type.OBJECT)
            throw new RuleFailure("table", "INVALID_TABLE");
        var columns = new HashSet<String>(); var rows = new HashSet<String>();
        for (var column : table.inputs()) {
            if (column == null || !identifier(column.id()) || !columns.add(column.id()) || column.source() == null || column.schema() == null)
                throw new RuleFailure("table", "INVALID_COLUMN");
            RuleValues.validateSchema(Map.of("column", column.schema()), 0);
            ConditionEngine.validate(new Condition("probe", "IS_NULL", column.source(), null, null));
            var sourceSchema = sourceSchema(document, column.source());
            if (sourceSchema != null && sourceSchema.type() != column.schema().type()) throw new RuleFailure(column.id(), "COLUMN_TYPE_MISMATCH");
            if (column.source().source().equals("literal")) RuleValues.output(column.schema(), column.source().value());
        }
        for (var row : table.rows()) {
            if (row == null || !identifier(row.id()) || !rows.add(row.id()) || row.cells() == null || !row.cells().keySet().equals(columns)
                    || row.outputs() == null || !document.outputSchema().properties().keySet().containsAll(row.outputs().keySet()))
                throw new RuleFailure("table", "INVALID_ROW");
            for (var column : table.inputs()) {
                var cell = row.cells().get(column.id());
                if (cell == null || cell.operator() == null || !OPS.contains(cell.operator())) throw new RuleFailure(row.id(), "INVALID_CELL");
                if (Set.of("ANY", "IS_NULL", "IS_NOT_NULL").contains(cell.operator())) {
                    if (cell.operand() != null) throw new RuleFailure(row.id(), "UNEXPECTED_CELL_OPERAND");
                } else {
                    ConditionEngine.validate(new Condition("probe", "EQ", column.source(), cell.operand(), null));
                    sourceSchema(document, cell.operand());
                    if (Set.of("GT", "GE", "LT", "LE").contains(cell.operator())
                            && !Set.of(RuleSchema.Type.DECIMAL, RuleSchema.Type.INTEGER, RuleSchema.Type.STRING, RuleSchema.Type.INSTANT).contains(column.schema().type()))
                        throw new RuleFailure(row.id(), "INCOMPATIBLE_COLUMN_OPERATOR");
                    if (cell.operator().equals("CONTAINS") && !Set.of(RuleSchema.Type.LIST, RuleSchema.Type.STRING).contains(column.schema().type()))
                        throw new RuleFailure(row.id(), "INCOMPATIBLE_COLUMN_OPERATOR");
                    if (cell.operand().source().equals("literal")) literal(column.schema(), cell.operator(), cell.operand().value());
                }
            }
            document.outputSchema().properties().forEach((key, field) -> {
                if (field.required() && field.defaultValue() == null && !row.outputs().containsKey(key))
                    throw new RuleFailure(row.id(), "MISSING_OUTPUT_COLUMN");
            });
            row.outputs().values().forEach(expressions::validate);
        }
        RuleValues.output(document.outputSchema(), table.defaultOutput());
    }
    static RuleEvaluator.Evaluation evaluate(RuleTemplateDocument document, ExpressionEngine expressions,
            Map<String, Object> input, Map<String, Object> params, Map<String, Object> context) {
        var table = document.definition().table(); var trace = new ArrayList<RuleTrace>(); Row hit = null;
        for (var row : table.rows()) {
            if (hit != null && table.hitPolicy() == HitPolicy.FIRST) {
                append(trace, new RuleTrace(row.id(), "TABLE_ROW", false, null, null, "SKIPPED")); continue;
            }
            boolean matched = true;
            for (var column : table.inputs()) {
                String id = row.id() + "/" + column.id();
                if (!matched) { append(trace, new RuleTrace(id, "TABLE_CELL", false, null, null, "SKIPPED")); continue; }
                Object left = ConditionEngine.resolve(column.source(), input, params, context);
                left = RuleValues.output(column.schema(), left);
                var cell = row.cells().get(column.id());
                Object right = cell.operand() == null ? null : ConditionEngine.resolve(cell.operand(), input, params, context);
                matched = match(left, right, column.schema(), cell.operator());
                append(trace, new RuleTrace(id, "TABLE_CELL", true, null, null, matched ? "MATCHED" : "NOT_MATCHED"));
            }
            append(trace, new RuleTrace(row.id(), "TABLE_ROW", true, null, null, matched ? "MATCHED" : "NOT_MATCHED"));
            if (matched) {
                if (hit != null) throw new RuleFailure("table." + row.id(), "UNIQUE_HIT_CONFLICT");
                hit = row;
            }
        }
        if (hit == null) return new RuleEvaluator.Evaluation(RuleValues.output(document.outputSchema(), table.defaultOutput()), false, List.copyOf(trace));
        var output = new LinkedHashMap<String, Object>();
        for (var entry : hit.outputs().entrySet()) {
            var result = expressions.evaluate(entry.getValue(), input, params, context); output.put(entry.getKey(), result.value());
            for (var item : result.trace()) append(trace, new RuleTrace(hit.id() + "/" + entry.getKey() + "/" + item.id(),
                    item.kind(), item.evaluated(), item.line(), item.column(), item.outcome()));
        }
        return new RuleEvaluator.Evaluation(RuleValues.output(document.outputSchema(), output), true, List.copyOf(trace));
    }
    private static boolean match(Object left, Object right, RuleSchema.Field schema, String operator) {
        if (operator.equals("ANY")) return true;
        if (operator.equals("IS_NULL")) return left == null;
        if (operator.equals("IS_NOT_NULL")) return left != null;
        if (left == null || right == null) return false;
        if (operator.equals("IN")) {
            if (!(right instanceof List<?> values)) throw new RuleFailure("table", "LIST_OPERAND_REQUIRED");
            return values.stream().anyMatch(value -> RuleEvaluator.deepEqual(left, RuleValues.output(schema, value)));
        }
        if (operator.equals("CONTAINS")) {
            if (left instanceof String text && right instanceof String needle) return text.contains(needle);
            if (left instanceof List<?> values) {
                Object target = RuleValues.output(schema.items(), right);
                return values.stream().anyMatch(value -> RuleEvaluator.deepEqual(value, target));
            }
            throw new RuleFailure("table", "INCOMPATIBLE_CONTAINS");
        }
        right = RuleValues.output(schema, right);
        if (operator.equals("EQ")) return RuleEvaluator.deepEqual(left, right);
        if (operator.equals("NE")) return !RuleEvaluator.deepEqual(left, right);
        int compared = switch (schema.type()) {
            case INTEGER, DECIMAL -> RuleValues.decimal(left, "table").compareTo(RuleValues.decimal(right, "table"));
            case INSTANT -> Instant.parse((String) left).compareTo(Instant.parse((String) right));
            case STRING -> ((String) left).compareTo((String) right);
            default -> throw new RuleFailure("table", "INCOMPATIBLE_COMPARISON");
        };
        return switch (operator) { case "GT" -> compared > 0; case "GE" -> compared >= 0; case "LT" -> compared < 0; case "LE" -> compared <= 0;
            default -> throw new RuleFailure("table", "INVALID_OPERATOR"); };
    }
    private static boolean identifier(String id) { return id != null && id.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}"); }
    private static void literal(RuleSchema.Field schema, String operator, Object value) {
        if (value == null) throw new RuleFailure("table", "EXPLICIT_NULL_OPERATOR_REQUIRED");
        if (operator.equals("IN")) {
            if (!(value instanceof List<?> list)) throw new RuleFailure("table", "LIST_OPERAND_REQUIRED");
            list.forEach(item -> RuleValues.output(schema, item));
        } else if (operator.equals("CONTAINS") && schema.type() == RuleSchema.Type.LIST) RuleValues.output(schema.items(), value);
        else RuleValues.output(schema, value);
    }
    private static RuleSchema.Field sourceSchema(RuleTemplateDocument document, Operand operand) {
        if (operand.source().equals("literal")) return null;
        if (operand.source().equals("context")) {
            if (!operand.path().equals("evaluatedAt")) throw new RuleFailure("table", "UNKNOWN_CONTEXT_FIELD");
            return RuleSchema.Field.required(RuleSchema.Type.INSTANT);
        }
        Map<String, RuleSchema.Field> fields = operand.source().equals("input") ? document.inputSchema().fields() : document.parameterSchema().fields();
        RuleSchema.Field field = null;
        for (String part : operand.path().split("\\.")) {
            field = fields == null ? null : fields.get(part);
            if (field == null) throw new RuleFailure(operand.path(), "UNKNOWN_FIELD");
            fields = field.properties();
        }
        return field;
    }
    private static void append(List<RuleTrace> trace, RuleTrace row) { if (trace.size() < 256) trace.add(row); }
}
