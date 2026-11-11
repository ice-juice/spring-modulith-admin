package com.sma.admin.rule.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.security.TenantScope;
import com.sma.admin.rule.RuleDefinition.Condition;
import com.sma.admin.rule.RuleDefinition.Operand;
import com.sma.admin.rule.RuleSchema;
import com.sma.admin.rule.RuleTrace;

class RuleEngineTests {
    private final ExpressionEngine engine = new ExpressionEngine();
    @Test void decimalMathFunctionsAndStructuredOutputUseExactResults() {
        var result = engine.evaluate("{'amount': round(min(input.amount * params.ratio, params.cap), 2), 'ok': true}",
                Map.of("amount", new BigDecimal("300.15")),
                Map.of("ratio", new BigDecimal("0.9"), "cap", new BigDecimal("999")), Map.of());
        assertThat(((Map<?, ?>) result.value()).get("amount")).isEqualTo(new BigDecimal("270.14"));
        assertThat(result.trace()).isNotEmpty().allMatch(trace -> trace.outcome() != null);
        assertThat(engine.evaluate("0.1 + 0.2 == 0.3", Map.of(), Map.of(), Map.of()).value()).isEqualTo(true);
    }
    @Test void shortCircuitPreservesSkippedTraceAndAvoidsDivisionByZero() {
        var result = engine.evaluate("false && (1 / 0 > 1)", Map.of(), Map.of(), Map.of());
        assertThat(result.value()).isEqualTo(false);
        assertThat(result.trace()).anyMatch(trace -> !trace.evaluated());
    }
    @Test void unsafeSyntaxMethodsUnknownFunctionsAndAssignmentsAreRejectedBeforeExecution() {
        for (String source : List.of("while(true) {}", "import java.lang.System; 1", "new String('x')",
                "input.getClass()", "input.amount = 0", "a = 1; a", "() -> 1", "System.exit(0)",
                "unknown(1)", "input.amount++", "for(i=0;i<9;i++){i}")) {
            assertThatThrownBy(() -> engine.validate(source)).as(source).isInstanceOf(RuleFailure.class);
        }
    }
    @Test void sameCompiledRuleDoesNotRetainParametersAcrossCalls() {
        String source = "input.amount * params.ratio";
        assertThat(engine.evaluate(source, Map.of("amount", new BigDecimal("100")), Map.of("ratio", new BigDecimal(".9")), Map.of()).value())
                .isEqualTo(new BigDecimal("90.0"));
        assertThat(engine.evaluate(source, Map.of("amount", new BigDecimal("100")), Map.of("ratio", new BigDecimal(".5")), Map.of()).value())
                .isEqualTo(new BigDecimal("50.0"));
    }
    @Test void compilationCacheIsBoundedGloballyAndPerTenant() {
        for (int i = 0; i < 90; i++) engine.validate("input.amount + " + i);
        assertThat(engine.cacheSize()).isEqualTo(64);
        for (long tenant = 2; tenant < 7; tenant++) {
            final long id = tenant;
            TenantScope.within(id, () -> { for (int i = 0; i < 70; i++) engine.validate("input.amount + " + i); return null; });
        }
        assertThat(engine.cacheSize()).isLessThanOrEqualTo(256);
    }
    @Test void schemaRejectsUnknownFieldsMissingValuesAndNumericCoercion() {
        var schema = new RuleSchema(Map.of("amount", RuleSchema.Field.required(RuleSchema.Type.DECIMAL)), false);
        assertThat(RuleValues.normalize(schema, Map.of("amount", 12), "input").get("amount")).isEqualTo(new BigDecimal("12"));
        assertThatThrownBy(() -> RuleValues.normalize(schema, Map.of(), "input")).isInstanceOf(RuleFailure.class);
        assertThatThrownBy(() -> RuleValues.normalize(schema, Map.of("amount", "12"), "input")).isInstanceOf(RuleFailure.class);
        assertThatThrownBy(() -> RuleValues.normalize(schema, Map.of("amount", 12, "tenantId", 9), "input")).isInstanceOf(RuleFailure.class);
        assertThatThrownBy(() -> RuleValues.normalize(schema, Map.of("amount", Double.NaN), "input")).isInstanceOf(RuleFailure.class);
    }
    @Test void nullIsDistinctFromMissingAndDefaultsAreCopiedWithoutMutatingCaller() {
        var optional = new RuleSchema.Field(RuleSchema.Type.STRING, false, null, null, null, null, null,
                null, false, null, null, "default");
        var schema = new RuleSchema(Map.of("name", optional), false);
        assertThat(RuleValues.normalize(schema, Map.of(), "input")).containsEntry("name", "default");
        var values = new java.util.HashMap<String, Object>(); values.put("name", null);
        var snapshot = RuleValues.normalize(schema, values, "input");
        assertThat(snapshot).containsKey("name"); assertThat(snapshot.get("name")).isNull();
        assertThatThrownBy(() -> snapshot.put("name", "x")).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void conditionGroupsShortCircuitAndCompareNumbersWithoutStringCoercion() {
        var first = new Condition("threshold", "GE", new Operand("input", "amount", null), new Operand("literal", null, 300), null);
        var second = new Condition("member", "IN", new Operand("input", "level", null), new Operand("literal", null, List.of("A", "B")), null);
        var root = new Condition("root", "AND", null, null, List.of(first, second));
        ConditionEngine.validate(root);
        var trace = new ArrayList<RuleTrace>();
        assertThat(ConditionEngine.evaluate(root, Map.of("amount", new BigDecimal("200.00")), Map.of(), Map.of(), trace)).isFalse();
        assertThat(trace).anyMatch(item -> item.id().equals("member") && !item.evaluated());
        trace.clear();
        assertThat(ConditionEngine.evaluate(root, Map.of("amount", new BigDecimal("300.00"), "level", "A"), Map.of(), Map.of(), trace)).isTrue();
    }
    @Test void conditionValidationRejectsDuplicateIdsAndInvalidOperators() {
        var item = new Condition("same", "IS_NULL", new Operand("input", "amount", null), null, null);
        assertThatThrownBy(() -> ConditionEngine.validate(new Condition("root", "OR", null, null, List.of(item, item))))
                .isInstanceOf(RuleFailure.class);
        assertThatThrownBy(() -> ConditionEngine.validate(new Condition("root", "EXECUTE", null, null, null)))
                .isInstanceOf(RuleFailure.class);
    }
    @Test void sourceBudgetsRejectDeepNestingAndExtremeLiteralsBeforeParsing() {
        assertThatThrownBy(() -> engine.validate("(".repeat(1000) + "1" + ")".repeat(1000))).isInstanceOf(RuleFailure.class);
        assertThatThrownBy(() -> engine.validate("1e999999999")).isInstanceOf(RuleFailure.class);
        assertThatThrownBy(() -> engine.validate("1+".repeat(500) + "1")).isInstanceOf(RuleFailure.class);
    }
    @Test void unboundedLoopsAreRejectedBeforeExecutionRatherThanRelyingOnNativeTimeout() {
        for (String source : List.of("while(true) {}", "while(true) { 1 + 1 }", "for(;;) {}")) {
            assertThatThrownBy(() -> engine.evaluate(source, Map.of(), Map.of(), Map.of()))
                    .isInstanceOf(RuleFailure.class);
        }
    }
    @Test void graphBudgetsRejectBroadAndCyclicJavaInputWithoutCoercingFloatingPointMoney() {
        var broad = java.util.Collections.nCopies(100, java.util.Collections.nCopies(100, 1));
        assertThatThrownBy(() -> RuleValues.freeze(broad, 0)).isInstanceOf(RuleFailure.class);
        var cycle = new java.util.ArrayList<Object>(); cycle.add(cycle);
        assertThatThrownBy(() -> RuleValues.freeze(cycle, 0)).isInstanceOf(RuleFailure.class);
        var schema = new RuleSchema(Map.of("amount", RuleSchema.Field.required(RuleSchema.Type.DECIMAL)), false);
        assertThatThrownBy(() -> RuleValues.normalize(schema, Map.of("amount", .1d), "input")).isInstanceOf(RuleFailure.class);
        Map<String, RuleSchema.Field> fields = Map.of("value", RuleSchema.Field.required(RuleSchema.Type.DECIMAL));
        for (int level = 0; level < 3; level++) {
            var parent = new java.util.LinkedHashMap<String, RuleSchema.Field>();
            var child = new RuleSchema.Field(RuleSchema.Type.OBJECT, true, null, null, null, null, null, null, false, fields, null, null);
            for (int i = 0; i < 30; i++) parent.put("f" + i, child);
            fields = parent;
        }
        var expandedSchema = fields;
        assertThatThrownBy(() -> RuleValues.validateSchema(expandedSchema, 0)).isInstanceOf(RuleFailure.class);
    }
    @Test void nestedSchemaValidatesTypesLimitsAndProtectsSnapshots() {
        var ratio = new RuleSchema.Field(RuleSchema.Type.DECIMAL, true, new BigDecimal("0"), new BigDecimal("1"),
                4, null, null, null, false, null, null, null);
        var schema = new RuleSchema(Map.of("ratio", ratio), false);
        assertThatThrownBy(() -> RuleValues.normalize(schema, Map.of("ratio", new BigDecimal("1.01")), "params"))
                .isInstanceOf(RuleFailure.class);
        assertThatThrownBy(() -> RuleValues.normalize(schema, Map.of("ratio", new BigDecimal("0.12345")), "params"))
                .isInstanceOf(RuleFailure.class);
        assertThat(RuleValues.normalize(schema, Map.of("ratio", new BigDecimal("0.9000")), "params"))
                .containsEntry("ratio", new BigDecimal("0.9000"));
    }
}
