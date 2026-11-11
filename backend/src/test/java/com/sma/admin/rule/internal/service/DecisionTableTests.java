package com.sma.admin.rule.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sma.admin.rule.*;
import com.sma.admin.rule.RuleDefinition.*;

class DecisionTableTests {
    private final RuleEvaluator evaluator = new RuleEvaluator(new ExpressionEngine(), id -> null, new RuleJson());
    private RuleSchema.Field number() { return RuleSchema.Field.required(RuleSchema.Type.DECIMAL); }
    private RuleSchema.Field output() {
        return new RuleSchema.Field(RuleSchema.Type.OBJECT, true, null, null, null, null, null, null, false,
                Map.of("payRatio", number(), "reason", RuleSchema.Field.required(RuleSchema.Type.STRING)), null, null);
    }
    private Row row(String id, String operator, Object bound, String ratio) {
        return new Row(id, Map.of("amount", new Cell(operator, operator.equals("ANY") ? null : new Operand("literal", null, bound))),
                Map.of("payRatio", ratio, "reason", "'" + id + "'"));
    }
    private RuleTemplateDocument table(HitPolicy hitPolicy, List<Row> rows) {
        var table = new DecisionTable(hitPolicy, List.of(new Column("amount", new Operand("input", "amount", null), number())),
                rows, Map.of("payRatio", BigDecimal.ONE, "reason", "DEFAULT"));
        return new RuleTemplateDocument(1, new RuleSchema(Map.of("amount", number()), false), RuleSchema.empty(), output(),
                new RuleDefinition(Kind.DECISION_TABLE, null, null, null, null, null, table), Map.of(), List.of());
    }
    @Test void noHitUsesExplicitDefaultAndSingleHitReturnsTypedOutput() {
        var doc = table(HitPolicy.UNIQUE, List.of(row("high", "GE", 300, "0.9")));
        var miss = evaluator.evaluate(doc, Map.of("amount", 100), Map.of(), Instant.now());
        assertThat(miss.matched()).isFalse(); assertThat(((Map<?, ?>) miss.output()).get("reason")).isEqualTo("DEFAULT");
        var hit = evaluator.evaluate(doc, Map.of("amount", new BigDecimal("300.00")), Map.of(), Instant.now());
        assertThat(hit.matched()).isTrue(); assertThat(((Map<?, ?>) hit.output()).get("payRatio")).isEqualTo(new BigDecimal("0.9"));
    }
    @Test void uniqueRejectsMultipleHitsEvenIfTheyWouldProduceEqualOutputs() {
        var doc = table(HitPolicy.UNIQUE, List.of(row("first", "GE", 100, "0.9"), row("second", "GE", 300, "0.9")));
        assertThatThrownBy(() -> evaluator.evaluate(doc, Map.of("amount", 400), Map.of(), Instant.now()))
                .isInstanceOf(RuleFailure.class).hasMessage("UNIQUE_HIT_CONFLICT");
    }
    @Test void firstHonorsVisibleOrderAndMarksLaterRowsAsSkipped() {
        var first = row("first", "GE", 100, "0.9"); var second = row("second", "GE", 300, "0.8");
        var result = evaluator.evaluate(table(HitPolicy.FIRST, List.of(first, second)), Map.of("amount", 400), Map.of(), Instant.now());
        assertThat(((Map<?, ?>) result.output()).get("reason")).isEqualTo("first");
        assertThat(result.trace()).anyMatch(trace -> trace.id().equals("second") && !trace.evaluated());
        var reordered = evaluator.evaluate(table(HitPolicy.FIRST, List.of(second, first)), Map.of("amount", 400), Map.of(), Instant.now());
        assertThat(((Map<?, ?>) reordered.output()).get("reason")).isEqualTo("second");
    }
    @Test void wildcardIsExplicitAndEmptyCellOrMissingOutputIsInvalid() {
        assertThat(evaluator.evaluate(table(HitPolicy.FIRST, List.of(row("all", "ANY", null, "1"))),
                Map.of("amount", 1), Map.of(), Instant.now()).matched()).isTrue();
        assertThatThrownBy(() -> evaluator.validate(table(HitPolicy.FIRST, List.of(new Row("missing", Map.of(), Map.of())))))
                .isInstanceOf(RuleFailure.class);
    }
    @Test void numericDateAndNullOperatorsUseDeclaredTypes() {
        var instant = new RuleSchema.Field(RuleSchema.Type.INSTANT, false, null, null, null, null, null, null, false, null, null, null);
        var input = new RuleSchema(Map.of("at", instant), false);
        var rows = List.of(new Row("after", Map.of("time", new Cell("GT", new Operand("literal", null, "2026-01-01T00:00:00Z"))),
                Map.of("payRatio", "0.9", "reason", "'AFTER'")));
        var table = new DecisionTable(HitPolicy.UNIQUE, List.of(new Column("time", new Operand("input", "at", null), instant)), rows,
                Map.of("payRatio", BigDecimal.ONE, "reason", "DEFAULT"));
        var doc = new RuleTemplateDocument(1, input, RuleSchema.empty(), output(), new RuleDefinition(Kind.DECISION_TABLE, null, null, null, null, null, table), Map.of(), List.of());
        assertThat(evaluator.evaluate(doc, Map.of("at", "2026-01-01T00:00:00.001Z"), Map.of(), Instant.now()).matched()).isTrue();
        assertThat(evaluator.evaluate(doc, Map.of(), Map.of(), Instant.now()).matched()).isFalse();
    }
    @Test void tableDocumentRoundTripsWithoutEditorRuntimeObjects() {
        var doc = table(HitPolicy.FIRST, List.of(row("first", "GE", 300, "0.9")));
        var json = new RuleJson(); var loaded = json.read(json.write(doc), RuleTemplateDocument.class);
        assertThat(evaluator.evaluate(loaded, Map.of("amount", 400), Map.of(), Instant.now()).matched()).isTrue();
    }
    @Test void invalidLiteralTypesAreRejectedEvenWhenTheRowWouldNotBeReached() {
        var doc = table(HitPolicy.FIRST, List.of(row("always", "ANY", null, "1"), row("bad", "GE", "wrong type", "0.9")));
        assertThatThrownBy(() -> evaluator.validate(doc)).isInstanceOf(RuleFailure.class);
    }
}
