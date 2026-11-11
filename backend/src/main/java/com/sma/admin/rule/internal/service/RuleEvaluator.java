package com.sma.admin.rule.internal.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.sma.admin.rule.*;
import com.sma.admin.rule.RuleDefinition.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.rule.internal.persistence.AssetMapper;
import com.sma.admin.rule.internal.persistence.VersionMapper;

@Component
public class RuleEvaluator {
    private final ExpressionEngine expressions;
    private final Function<Long, RuleTemplateDocument> dependencies;
    private final RuleJson json;
    @Autowired
    public RuleEvaluator(ExpressionEngine expressions, VersionMapper versions, AssetMapper assets, RuleJson json) {
        this(expressions, id -> {
            var version = versions.selectById(id);
            if (version == null || !"TEMPLATE".equals(version.getKind()) || !CurrentUser.portal().equals(version.getPortal()))
                throw new RuleFailure("dependency", "VERSION_UNAVAILABLE");
            if (!json.hash(version.getContentJson()).equals(version.getContentHash())) throw new RuleFailure("dependency", "CONTENT_HASH_MISMATCH");
            var asset = assets.selectById(version.getAssetId());
            if (asset == null || !CurrentUser.portal().equals(asset.getPortal()) || "REVOKED".equals(asset.getState()))
                throw new RuleFailure("dependency", "VERSION_UNAVAILABLE");
            return json.read(version.getContentJson(), RuleTemplateDocument.class);
        }, json);
    }
    RuleEvaluator(ExpressionEngine expressions, Function<Long, RuleTemplateDocument> dependencies, RuleJson json) {
        this.expressions = expressions; this.dependencies = dependencies; this.json = json;
    }
    public record Evaluation(Object output, boolean matched, List<RuleTrace> trace) {}
    public void validate(RuleTemplateDocument doc) {
        validate(doc, new HashSet<>(), new HashSet<>());
    }
    private void validate(RuleTemplateDocument doc, Set<Long> active, Set<Long> verified) {
        if (doc == null || doc.formatVersion() != 1 || doc.definition() == null || doc.definition().kind() == null
                || doc.inputSchema() == null || doc.parameterSchema() == null || doc.outputSchema() == null)
            throw new RuleFailure("document", "INVALID_DOCUMENT");
        RuleValues.validateSchema(doc.inputSchema().fields(), 0);
        RuleValues.validateSchema(doc.parameterSchema().fields(), 0);
        RuleValues.validateSchema(Map.of("output", doc.outputSchema()), 0);
        RuleParameters.validate(doc);
        if (doc.inputSchema().additionalProperties() || doc.parameterSchema().additionalProperties())
            throw new RuleFailure("schema", "CLOSED_SCHEMA_REQUIRED");
        switch (doc.definition().kind()) {
            case EXPRESSION -> expressions.validate(doc.definition().expression());
            case CONDITION -> {
                ConditionEngine.validate(doc.definition().condition());
                RuleValues.output(doc.outputSchema(), doc.definition().onMatch());
                RuleValues.output(doc.outputSchema(), doc.definition().otherwise());
            }
            case FLOW -> validateFlow(doc, active, verified);
            case DECISION_TABLE -> DecisionTableEngine.validate(doc, expressions);
        }
        if (doc.parameterUi() != null) doc.parameterUi().forEach((key, ui) -> {
            if (!doc.parameterSchema().fields().containsKey(key) || ui == null || ui.label() == null
                    || ui.label().length() > 128 || ui.help() != null && ui.help().length() > 1024)
                throw new RuleFailure("parameterUi", "INVALID_UI_SCHEMA");
        });
    }
    public Evaluation evaluate(RuleTemplateDocument document, Map<String, Object> rawInput,
            Map<String, Object> rawParams, Instant now) {
        validate(document);
        return evaluate(document, rawInput, rawParams, now, new HashSet<>(), new int[]{256});
    }
    private Evaluation evaluate(RuleTemplateDocument document, Map<String, Object> rawInput,
            Map<String, Object> rawParams, Instant now, Set<Long> active, int[] budget) {
        var input = RuleValues.normalize(document.inputSchema(), rawInput, "input");
        var params = parameters(document, rawParams);
        var context = Map.<String, Object>of("evaluatedAt", now.toString());
        Object output; boolean matched = true; var trace = new ArrayList<RuleTrace>();
        if (document.definition().kind() == RuleDefinition.Kind.EXPRESSION) {
            var result = expressions.evaluate(document.definition().expression(), input, params, context);
            output = result.value(); trace.addAll(result.trace());
        } else if (document.definition().kind() == Kind.CONDITION) {
            matched = ConditionEngine.evaluate(document.definition().condition(), input, params, context, trace);
            output = matched ? document.definition().onMatch() : document.definition().otherwise();
        } else if (document.definition().kind() == Kind.FLOW) {
            var flow = evaluateFlow(document, input, params, now, active, budget);
            output = flow.output(); trace.addAll(flow.trace());
        } else {
            var table = DecisionTableEngine.evaluate(document, expressions, input, params, context);
            output = table.output(); matched = table.matched(); trace.addAll(table.trace());
        }
        return new Evaluation(RuleValues.output(document.outputSchema(), output), matched, List.copyOf(trace));
    }
    public Map<String, Object> parameters(RuleTemplateDocument document, Map<String, Object> params) {
        return RuleParameters.normalize(document, params);
    }
    private void validateFlow(RuleTemplateDocument document, Set<Long> active, Set<Long> verified) {
        var plan = RuleFlowPlan.compile(document.definition().flow()); var available = plan.guaranteedPredecessors();
        for (var node : plan.nodes.values()) {
            switch (node.kind()) {
                case START -> { if (node.expression() != null || node.condition() != null) throw new RuleFailure(node.id(), "INVALID_START"); }
                case EXPRESSION, RESULT -> {
                    expression(node.expression(), node.id(), available.get(node.id()));
                    if (node.kind() == NodeKind.EXPRESSION) RuleValues.validateSchema(Map.of("output", requiredOutput(node)), 0);
                }
                case CONDITION -> {
                    if ((node.expression() == null) == (node.condition() == null)) throw new RuleFailure(node.id(), "CONDITION_REQUIRED");
                    if (node.expression() != null) expression(node.expression(), node.id(), available.get(node.id()));
                    else { ConditionEngine.validate(node.condition()); conditionReferences(node.condition(), available.get(node.id())); }
                }
                case SWITCH -> expression(node.expression(), node.id(), available.get(node.id()));
                case SUBRULE, TABLE -> {
                    Long id = node.subRuleVersionId();
                    if (id == null || active.contains(id) || active.size() >= 8 || verified.size() >= 64)
                        throw new RuleFailure(node.id(), "DEPENDENCY_CYCLE_OR_LIMIT");
                    var child = dependencies.apply(id);
                    if (child == null) throw new RuleFailure(node.id(), "VERSION_UNAVAILABLE");
                    if (node.kind() == NodeKind.TABLE && child.definition().kind() != Kind.DECISION_TABLE)
                        throw new RuleFailure(node.id(), "DECISION_TABLE_REQUIRED");
                    if (!child.outputSchema().equals(requiredOutput(node))) throw new RuleFailure(node.id(), "SUBRULE_OUTPUT_MISMATCH");
                    bindings(node.inputBindings(), child.inputSchema(), node.id(), available.get(node.id()));
                    bindings(node.parameterBindings(), child.parameterSchema(), node.id(), available.get(node.id()));
                    if (!verified.contains(id)) {
                        active.add(id); validate(child, active, verified); active.remove(id); verified.add(id);
                    }
                }
            }
        }
    }
    private RuleSchema.Field requiredOutput(Node node) {
        if (node.outputSchema() == null) throw new RuleFailure(node.id(), "OUTPUT_SCHEMA_REQUIRED");
        return node.outputSchema();
    }
    private void bindings(Map<String, String> bindings, RuleSchema schema, String id, Set<String> available) {
        var values = bindings == null ? Map.<String, String>of() : bindings;
        if (!schema.fields().keySet().containsAll(values.keySet())) throw new RuleFailure(id, "UNKNOWN_BINDING");
        schema.fields().forEach((key, field) -> {
            if (field.required() && field.defaultValue() == null && !values.containsKey(key)) throw new RuleFailure(id, "MISSING_BINDING");
        });
        values.values().forEach(source -> expression(source, id, available));
    }
    private void expression(String source, String id, Set<String> available) {
        expressions.validate(source);
        for (var path : expressions.referencedPaths(source)) {
            if (path.size() >= 3 && path.get(0).equals("context") && path.get(1).equals("steps") && !available.contains(path.get(2)))
                throw new RuleFailure(id, "STEP_NOT_DEFINITELY_AVAILABLE");
        }
    }
    private void conditionReferences(Condition node, Set<String> available) {
        for (Operand operand : new Operand[]{node.left(), node.right()}) {
            if (operand != null && "context".equals(operand.source()) && operand.path().startsWith("steps.")) {
                String id = operand.path().split("\\.")[1];
                if (!available.contains(id)) throw new RuleFailure(node.id(), "STEP_NOT_DEFINITELY_AVAILABLE");
            }
        }
        if (node.children() != null) node.children().forEach(child -> conditionReferences(child, available));
    }
    private Evaluation evaluateFlow(RuleTemplateDocument document, Map<String, Object> input, Map<String, Object> params,
            Instant now, Set<Long> active, int[] budget) {
        var plan = RuleFlowPlan.compile(document.definition().flow());
        var steps = new LinkedHashMap<String, Object>(); var trace = new ArrayList<RuleTrace>();
        var visited = new HashSet<String>(); var output = new Object[1];
        RuleFlowRuntime.execute(json.hash(json.write(document)), plan, id -> {
            if (--budget[0] < 0) throw new RuleFailure(id, "STEP_BUDGET_EXCEEDED");
            var node = plan.nodes.get(id); visited.add(id);
            var context = Map.<String, Object>of("evaluatedAt", now.toString(), "steps", Collections.unmodifiableMap(new LinkedHashMap<>(steps)));
            Object value;
            try {
                value = switch (node.kind()) {
                    case START -> true;
                    case EXPRESSION, RESULT, SWITCH -> {
                        var evaluated = expressions.evaluate(node.expression(), input, params, context);
                        prefix(trace, id, evaluated.trace()); yield evaluated.value();
                    }
                    case CONDITION -> {
                        if (node.expression() != null) {
                            var evaluated = expressions.evaluate(node.expression(), input, params, context);
                            prefix(trace, id, evaluated.trace());
                            if (!(evaluated.value() instanceof Boolean)) throw new RuleFailure(id, "BOOLEAN_REQUIRED");
                            yield evaluated.value();
                        }
                        var conditions = new ArrayList<RuleTrace>();
                        boolean matched = ConditionEngine.evaluate(node.condition(), input, params, context, conditions);
                        prefix(trace, id, conditions); yield matched;
                    }
                    case SUBRULE, TABLE -> {
                        Long dependency = node.subRuleVersionId();
                        if (active.contains(dependency) || active.size() >= 8) throw new RuleFailure(id, "DEPENDENCY_CYCLE_OR_LIMIT");
                        var child = dependencies.apply(dependency);
                        if (child == null) throw new RuleFailure(id, "VERSION_UNAVAILABLE");
                        active.add(dependency);
                        try {
                            var evaluated = evaluate(child, mapped(node.inputBindings(), input, params, context),
                                    mapped(node.parameterBindings(), input, params, context), now, active, budget);
                            prefix(trace, id, evaluated.trace()); yield evaluated.output();
                        } finally { active.remove(dependency); }
                    }
                };
                if (node.outputSchema() != null) value = RuleValues.output(node.outputSchema(), value);
                steps.put(id, RuleValues.freeze(value, 0));
                add(trace, new RuleTrace(id, "FLOW", true, null, null,
                        node.kind() == NodeKind.CONDITION ? Boolean.TRUE.equals(value) ? "TRUE" : "FALSE" : "EXECUTED"));
                if (node.kind() == NodeKind.RESULT) output[0] = value;
                if (node.kind() == NodeKind.SWITCH) {
                    if (!(value instanceof String)) throw new RuleFailure(id, "STRING_SELECTION_REQUIRED");
                    int selected = node.cases().keySet().stream().sorted().toList().indexOf(value);
                    return selected < 0 ? "default" : "case" + selected;
                }
                return node.kind() == NodeKind.CONDITION && Boolean.TRUE.equals(value);
            } catch (RuleFailure failure) {
                var problem = failure.problem();
                throw new RuleFailure(new RuleProblem(id + "." + problem.path(), problem.code(), problem.line(), problem.column()));
            }
        });
        plan.nodes.keySet().stream().filter(id -> !visited.contains(id)).sorted()
                .forEach(id -> add(trace, new RuleTrace(id, "FLOW", false, null, null, "SKIPPED")));
        return new Evaluation(output[0], true, List.copyOf(trace));
    }
    private Map<String, Object> mapped(Map<String, String> mappings, Map<String, Object> input,
            Map<String, Object> params, Map<String, Object> context) {
        var values = new LinkedHashMap<String, Object>();
        if (mappings != null) mappings.forEach((key, source) -> values.put(key, expressions.evaluate(source, input, params, context).value()));
        return Collections.unmodifiableMap(values);
    }
    private static void prefix(List<RuleTrace> target, String id, List<RuleTrace> source) {
        for (var trace : source) add(target, new RuleTrace(id + "/" + trace.id(), trace.kind(), trace.evaluated(), trace.line(), trace.column(), trace.outcome()));
    }
    private static void add(List<RuleTrace> target, RuleTrace trace) { if (target.size() < 256) target.add(trace); }
    public void examples(RuleTemplateDocument document) {
        if (document.examples() == null || document.examples().isEmpty() || document.examples().size() > 50)
            throw new RuleFailure("examples", "EXAMPLES_REQUIRED");
        for (var example : document.examples()) {
            if (example == null || example.name() == null || example.name().length() > 128)
                throw new RuleFailure("examples", "INVALID_EXAMPLE");
            var actual = evaluate(document, example.input(), example.params(), Instant.parse("2026-01-01T00:00:00Z"));
            var expected = RuleValues.output(document.outputSchema(), example.expectedOutput());
            if (!deepEqual(actual.output(), expected)) throw new RuleFailure("examples." + example.name(), "ASSERTION_FAILED");
        }
    }
    static boolean deepEqual(Object left, Object right) {
        if (left instanceof Map<?, ?> a && right instanceof Map<?, ?> b)
            return a.keySet().equals(b.keySet()) && a.keySet().stream().allMatch(key -> deepEqual(a.get(key), b.get(key)));
        if (left instanceof List<?> a && right instanceof List<?> b) {
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); i++) if (!deepEqual(a.get(i), b.get(i))) return false;
            return true;
        }
        return RuleValues.equal(left, right);
    }
}
