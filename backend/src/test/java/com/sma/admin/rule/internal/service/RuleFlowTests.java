package com.sma.admin.rule.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sma.admin.rule.*;
import com.sma.admin.rule.RuleDefinition.*;

class RuleFlowTests {
    private final ExpressionEngine expressions = new ExpressionEngine();
    private RuleSchema input() { return new RuleSchema(Map.of("amount", RuleSchema.Field.required(RuleSchema.Type.DECIMAL)), false); }
    private RuleSchema params() { return new RuleSchema(Map.of("ratio", RuleSchema.Field.required(RuleSchema.Type.DECIMAL)), false); }
    private RuleSchema.Field number() { return RuleSchema.Field.required(RuleSchema.Type.DECIMAL); }
    private Node node(String id, NodeKind kind, String source, String next, String yes, String no) {
        return new Node(id, kind, source, null, next, yes, no, null, null, null, kind == NodeKind.EXPRESSION ? number() : null);
    }
    private RuleTemplateDocument document(List<Node> nodes) {
        return new RuleTemplateDocument(1, input(), params(), number(),
                new RuleDefinition(Kind.FLOW, null, null, null, null, new Flow("start", nodes)), Map.of(), List.of());
    }
    private RuleEvaluator evaluator(Map<Long, RuleTemplateDocument> dependencies) {
        return new RuleEvaluator(expressions, dependencies::get, new RuleJson());
    }
    @Test void structuredBranchesUseLiteFlowAndTraceOnlyTheExecutedPath() {
        var doc = document(List.of(node("start", NodeKind.START, null, "eligible", null, null),
                node("eligible", NodeKind.CONDITION, "input.amount >= 300", null, "discount", "unchanged"),
                node("discount", NodeKind.EXPRESSION, "round(input.amount * params.ratio, 2)", "result", null, null),
                node("result", NodeKind.RESULT, "context.steps.discount", null, null, null),
                node("unchanged", NodeKind.RESULT, "input.amount", null, null, null)));
        var evaluator = evaluator(Map.of());
        var accepted = evaluator.evaluate(doc, Map.of("amount", new BigDecimal("400")), Map.of("ratio", new BigDecimal(".9")), Instant.now());
        assertThat(accepted.output()).isEqualTo(new BigDecimal("360.00"));
        assertThat(accepted.trace()).anyMatch(item -> item.id().equals("unchanged") && !item.evaluated());
        var skipped = evaluator.evaluate(doc, Map.of("amount", new BigDecimal("200")), Map.of("ratio", new BigDecimal(".9")), Instant.now());
        assertThat(skipped.output()).isEqualTo(new BigDecimal("200"));
        assertThat(skipped.trace()).anyMatch(item -> item.id().equals("discount") && !item.evaluated());
    }
    @Test void graphValidationRejectsCyclesUnreachableNodesBadIdsAndMissingBranches() {
        var start = node("start", NodeKind.START, null, "result", null, null);
        var result = node("result", NodeKind.RESULT, "input.amount", null, null, null);
        for (var nodes : List.of(
                List.of(node("start", NodeKind.START, null, "start", null, null)),
                List.of(start, result, node("orphan", NodeKind.RESULT, "0", null, null, null)),
                List.of(start, result, node("evil\"", NodeKind.RESULT, "0", null, null, null)),
                List.of(node("start", NodeKind.START, null, "choice", null, null), node("choice", NodeKind.CONDITION, "true", null, "result", null), result)))
            assertThatThrownBy(() -> evaluator(Map.of()).validate(document(nodes))).isInstanceOf(RuleFailure.class);
    }
    @Test void mergeCannotReadAnOutputThatOnlyExistsOnOneBranch() {
        var doc = document(List.of(node("start", NodeKind.START, null, "choice", null, null),
                node("choice", NodeKind.CONDITION, "true", null, "discount", "result"),
                node("discount", NodeKind.EXPRESSION, "0", "result", null, null),
                node("result", NodeKind.RESULT, "context.steps.discount", null, null, null)));
        assertThatThrownBy(() -> evaluator(Map.of()).validate(doc)).isInstanceOf(RuleFailure.class)
                .hasMessage("STEP_NOT_DEFINITELY_AVAILABLE");
    }
    @Test void fixedVersionSubrulesGetExplicitInputAndParameterMappings() {
        var child = new RuleTemplateDocument(1, input(), params(), number(), RuleDefinition.expression("input.amount * params.ratio"), Map.of(), List.of());
        var sub = new Node("child", NodeKind.SUBRULE, null, null, "result", null, null, 7L,
                Map.of("amount", "input.amount"), Map.of("ratio", "params.ratio"), number());
        var doc = document(List.of(node("start", NodeKind.START, null, "child", null, null), sub,
                node("result", NodeKind.RESULT, "context.steps.child", null, null, null)));
        var result = evaluator(Map.of(7L, child)).evaluate(doc, Map.of("amount", 100), Map.of("ratio", new BigDecimal(".8")), Instant.now());
        assertThat(result.output()).isEqualTo(new BigDecimal("80.0"));
        assertThat(result.trace()).anyMatch(item -> item.id().startsWith("child/"));
        assertThatThrownBy(() -> evaluator(Map.of()).validate(doc)).isInstanceOf(RuleFailure.class);
    }
    @Test void runtimeErrorsIdentifyTheirFlowNodeWithoutInputValues() {
        var doc = document(List.of(node("start", NodeKind.START, null, "failure", null, null),
                node("failure", NodeKind.RESULT, "input.amount / 0", null, null, null)));
        try {
            evaluator(Map.of()).evaluate(doc, Map.of("amount", 100), Map.of("ratio", BigDecimal.ONE), Instant.now()); fail("Expected error");
        } catch (RuleFailure failure) {
            assertThat(failure.problem().path()).startsWith("failure.");
            assertThat(failure.problem().line()).isNotNull();
        }
    }
    @Test void concurrentRequestsShareOnlyThePlanAndKeepTheirOwnStepValues() throws Exception {
        var doc = document(List.of(node("start", NodeKind.START, null, "result", null, null),
                node("result", NodeKind.RESULT, "input.amount * params.ratio", null, null, null)));
        var evaluator = evaluator(Map.of());
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var results = new java.util.ArrayList<java.util.concurrent.Future<Object>>();
            for (int i = 1; i <= 32; i++) {
                final int value = i;
                results.add(executor.submit(() -> evaluator.evaluate(doc, Map.of("amount", value),
                        Map.of("ratio", new BigDecimal(".5")), Instant.now()).output()));
            }
            for (int i = 0; i < results.size(); i++)
                assertThat(results.get(i).get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(new BigDecimal(i + 1).multiply(new BigDecimal(".5")));
        }
    }
    @Test void switchSelectionUsesExplicitCasesAndMandatoryDefault() {
        var selection = new Node("choose", NodeKind.SWITCH, "input.amount >= 300 ? 'gold' : 'ordinary'", null,
                "base", null, null, null, null, null, null, Map.of("gold", "discount"));
        var doc = document(List.of(node("start", NodeKind.START, null, "choose", null, null), selection,
                node("discount", NodeKind.RESULT, "input.amount * params.ratio", null, null, null),
                node("base", NodeKind.RESULT, "input.amount", null, null, null)));
        assertThat(evaluator(Map.of()).evaluate(doc, Map.of("amount", 400), Map.of("ratio", new BigDecimal(".9")), Instant.now()).output())
                .isEqualTo(new BigDecimal("360.0"));
        assertThat(evaluator(Map.of()).evaluate(doc, Map.of("amount", 100), Map.of("ratio", new BigDecimal(".9")), Instant.now()).output())
                .isEqualTo(new BigDecimal("100"));
    }
    @Test void olderFlowJsonWithoutNewOptionalFieldsRemainsExecutable() {
        var json = new RuleJson();
        var legacy = """
                {"formatVersion":1,"inputSchema":{"fields":{}},"parameterSchema":{"fields":{}},
                "outputSchema":{"type":"DECIMAL","required":true},"definition":{"kind":"FLOW",
                "flow":{"startNode":"start","nodes":[{"id":"start","kind":"START","next":"end"},
                {"id":"end","kind":"RESULT","expression":"1"}]}}}
                """;
        var loaded = json.read(legacy, RuleTemplateDocument.class);
        assertThat(evaluator(Map.of()).evaluate(loaded, Map.of(), Map.of(), Instant.now()).output()).isEqualTo(BigDecimal.ONE);
    }
}
