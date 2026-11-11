package com.sma.admin.rule.internal.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.sma.admin.rule.RuleDefinition.*;

/** Compiles a single-path, exclusive-branch DAG into a bounded, structured LiteFlow expression. */
final class RuleFlowPlan {
    final Map<String, Node> nodes;
    final String el;
    private RuleFlowPlan(Map<String, Node> nodes, String el) { this.nodes = Map.copyOf(nodes); this.el = el; }
    static RuleFlowPlan compile(Flow flow) {
        if (flow == null || flow.nodes() == null || flow.nodes().isEmpty() || flow.nodes().size() > 64)
            throw new RuleFailure("flow", "FLOW_LIMIT");
        var nodes = new LinkedHashMap<String, Node>();
        for (var node : flow.nodes()) {
            if (node == null || node.kind() == null || node.id() == null || !node.id().matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")
                    || nodes.putIfAbsent(node.id(), node) != null) throw new RuleFailure("flow", "INVALID_NODE");
        }
        var start = nodes.get(flow.startNode());
        if (start == null || start.kind() != NodeKind.START || nodes.values().stream().filter(node -> node.kind() == NodeKind.START).count() != 1)
            throw new RuleFailure("flow", "START_REQUIRED");
        var reached = new HashSet<String>(); var active = new HashSet<String>();
        visit(flow.startNode(), nodes, reached, active, 0);
        if (reached.size() != nodes.size()) throw new RuleFailure("flow", "UNREACHABLE_NODE");
        int[] budget = {0}; String el = emit(start.id(), nodes, budget);
        if (el.length() > 32000) throw new RuleFailure("flow", "PLAN_LIMIT");
        return new RuleFlowPlan(nodes, el);
    }
    private static void visit(String id, Map<String, Node> nodes, Set<String> reached, Set<String> active, int depth) {
        if (id == null || !nodes.containsKey(id)) throw new RuleFailure("flow", "MISSING_TARGET");
        if (depth > 32 || active.contains(id)) throw new RuleFailure(id, "FLOW_CYCLE_OR_DEPTH");
        if (reached.contains(id)) return;
        active.add(id); var node = nodes.get(id);
        switch (node.kind()) {
            case START, EXPRESSION, SUBRULE, TABLE -> {
                if (node.onTrue() != null || node.onFalse() != null) throw new RuleFailure(id, "UNEXPECTED_BRANCH");
                visit(node.next(), nodes, reached, active, depth + 1);
            }
            case CONDITION -> {
                if (node.next() != null || node.onTrue() == null || node.onFalse() == null)
                    throw new RuleFailure(id, "BRANCH_TARGETS_REQUIRED");
                visit(node.onTrue(), nodes, reached, active, depth + 1);
                visit(node.onFalse(), nodes, reached, active, depth + 1);
            }
            case SWITCH -> {
                if (node.cases() == null || node.cases().isEmpty() || node.cases().size() > 16
                        || node.onTrue() != null || node.onFalse() != null
                        || node.cases().keySet().stream().anyMatch(key -> !key.matches("[a-zA-Z0-9_-]{1,64}")))
                    throw new RuleFailure(id, "SWITCH_CASES_REQUIRED");
                for (String target : targets(node)) visit(target, nodes, reached, active, depth + 1);
            }
            case RESULT -> {
                if (node.next() != null || node.onTrue() != null || node.onFalse() != null) throw new RuleFailure(id, "RESULT_MUST_TERMINATE");
            }
        }
        active.remove(id); reached.add(id);
    }
    private static String emit(String id, Map<String, Node> nodes, int[] budget) {
        if (++budget[0] > 256) throw new RuleFailure("flow", "PLAN_LIMIT");
        var node = nodes.get(id);
        String action = "smaRuleActionV1.tag(\"" + id + "\")";
        return switch (node.kind()) {
            case RESULT -> action;
            case CONDITION -> "IF(smaRuleBranchV1.tag(\"" + id + "\")," + emit(node.onTrue(), nodes, budget) + "," + emit(node.onFalse(), nodes, budget) + ")";
            case SWITCH -> {
                var choices = new ArrayList<String>(); int index = 0;
                for (String key : node.cases().keySet().stream().sorted().toList())
                    choices.add("THEN(" + emit(node.cases().get(key), nodes, budget) + ").id(\"case" + index++ + "\")");
                choices.add("THEN(" + emit(node.next(), nodes, budget) + ").id(\"default\")");
                yield "SWITCH(smaRuleSwitchV1.tag(\"" + id + "\")).to(" + String.join(",", choices) + ")";
            }
            default -> "THEN(" + action + "," + emit(node.next(), nodes, budget) + ")";
        };
    }
    Map<String, Set<String>> guaranteedPredecessors() {
        var incoming = new HashMap<String, List<String>>();
        nodes.keySet().forEach(id -> incoming.put(id, new ArrayList<>()));
        nodes.values().forEach(node -> targets(node).forEach(target -> incoming.get(target).add(node.id())));
        var result = new HashMap<String, Set<String>>();
        for (String id : nodes.keySet()) guarantees(id, incoming, result);
        return result;
    }
    private static Set<String> guarantees(String id, Map<String, List<String>> incoming, Map<String, Set<String>> memo) {
        if (memo.containsKey(id)) return memo.get(id);
        Set<String> common = null;
        for (String parent : incoming.get(id)) {
            var available = new HashSet<>(guarantees(parent, incoming, memo)); available.add(parent);
            if (common == null) common = available; else common.retainAll(available);
        }
        Set<String> result = common == null ? Set.of() : Set.copyOf(common); memo.put(id, result); return result;
    }
    static List<String> targets(Node node) {
        return switch (node.kind()) {
            case RESULT -> List.of(); case CONDITION -> List.of(node.onTrue(), node.onFalse());
            case SWITCH -> {
                var targets = new ArrayList<>(node.cases().values());
                if (node.next() == null) throw new RuleFailure(node.id(), "SWITCH_DEFAULT_REQUIRED");
                targets.add(node.next()); yield targets;
            }
            default -> List.of(node.next());
        };
    }
}
