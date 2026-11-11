package com.sma.admin.rule.internal.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.sma.admin.rule.RuleDefinition.Condition;
import com.sma.admin.rule.RuleDefinition.Operand;
import com.sma.admin.rule.RuleTrace;

final class ConditionEngine {
    private static final Set<String> OPS = Set.of("AND", "OR", "NOT", "EQ", "NE", "GT", "GE", "LT", "LE",
            "IN", "CONTAINS", "IS_NULL", "IS_NOT_NULL");
    private ConditionEngine() {}
    static void validate(Condition condition) { validate(condition, 0, new HashSet<>()); }
    private static void validate(Condition node, int depth, Set<String> ids) {
        if (node == null || depth > 16 || ids.size() >= 256 || node.id() == null
                || !node.id().matches("[a-zA-Z][a-zA-Z0-9_-]{0,63}") || !ids.add(node.id()) || node.operator() == null || !OPS.contains(node.operator()))
            throw new RuleFailure("condition", "INVALID_CONDITION");
        if (Set.of("AND", "OR", "NOT").contains(node.operator())) {
            if (node.left() != null || node.right() != null || node.children() == null || node.children().isEmpty()
                    || node.operator().equals("NOT") && node.children().size() != 1)
                throw new RuleFailure(node.id(), "INVALID_GROUP");
            node.children().forEach(child -> validate(child, depth + 1, ids));
        } else {
            if (node.children() != null && !node.children().isEmpty()) throw new RuleFailure(node.id(), "INVALID_LEAF");
            operand(node.left());
            if (!Set.of("IS_NULL", "IS_NOT_NULL").contains(node.operator())) operand(node.right());
            else if (node.right() != null) throw new RuleFailure(node.id(), "UNEXPECTED_OPERAND");
        }
    }
    private static void operand(Operand value) {
        if (value == null || value.source() == null || !Set.of("input", "params", "context", "literal").contains(value.source()))
            throw new RuleFailure("condition", "INVALID_OPERAND");
        if (value.source().equals("literal")) RuleValues.freeze(value.value(), 0);
        else if (value.path() == null || !value.path().matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*){0,7}"))
            throw new RuleFailure("condition", "INVALID_PATH");
    }
    static boolean evaluate(Condition node, Map<String, Object> input, Map<String, Object> params,
            Map<String, Object> context, List<RuleTrace> trace) {
        boolean result;
        if (Set.of("AND", "OR", "NOT").contains(node.operator())) {
            result = !node.operator().equals("OR"); boolean shorted = false;
            for (var child : node.children()) {
                if (shorted) { skip(child, trace); continue; }
                boolean value = evaluate(child, input, params, context, trace);
                if (node.operator().equals("NOT")) result = !value;
                else { result = value; shorted = node.operator().equals("AND") ? !value : value; }
            }
        } else {
            Object left = resolve(node.left(), input, params, context);
            Object right = node.right() == null ? null : resolve(node.right(), input, params, context);
            result = switch (node.operator()) {
                case "IS_NULL" -> left == null;
                case "IS_NOT_NULL" -> left != null;
                case "EQ" -> left != null && right != null && RuleValues.equal(left, right);
                case "NE" -> left != null && right != null && !RuleValues.equal(left, right);
                case "IN" -> right instanceof List<?> list && list.stream().anyMatch(value -> RuleValues.equal(left, value));
                case "CONTAINS" -> left instanceof List<?> list && list.stream().anyMatch(value -> RuleValues.equal(right, value))
                        || left instanceof String text && right instanceof String needle && text.contains(needle);
                default -> {
                    if (left == null || right == null) yield false;
                    if (!(left instanceof Number) || !(right instanceof Number)) throw new RuleFailure(node.id(), "INCOMPATIBLE_COMPARISON");
                    int compare = RuleValues.decimal(left, node.id()).compareTo(RuleValues.decimal(right, node.id()));
                    yield switch (node.operator()) { case "GT" -> compare > 0; case "GE" -> compare >= 0;
                        case "LT" -> compare < 0; case "LE" -> compare <= 0;
                        default -> throw new RuleFailure(node.id(), "INVALID_OPERATOR"); };
                }
            };
        }
        trace.add(new RuleTrace(node.id(), "CONDITION", true, null, null, result ? "MATCHED" : "NOT_MATCHED"));
        return result;
    }
    private static void skip(Condition node, List<RuleTrace> trace) {
        trace.add(new RuleTrace(node.id(), "CONDITION", false, null, null, "SKIPPED"));
        if (node.children() != null) node.children().forEach(child -> skip(child, trace));
    }
    static Object resolve(Operand operand, Map<String, Object> input, Map<String, Object> params, Map<String, Object> context) {
        if (operand.source().equals("literal")) return RuleValues.freeze(operand.value(), 0);
        Object current = switch (operand.source()) { case "input" -> input; case "params" -> params; default -> context; };
        for (String segment : operand.path().split("\\.")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(segment)) throw new RuleFailure(operand.path(), "MISSING_FIELD");
            current = map.get(segment);
        }
        return current;
    }
}
