package com.sma.admin.rule.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sma.admin.rule.*;

class RuleParameterTests {
    private final RuleEvaluator evaluator = new RuleEvaluator(new ExpressionEngine(), id -> null, new RuleJson());
    private RuleSchema.Field number(boolean required) { return new RuleSchema.Field(RuleSchema.Type.DECIMAL, required, null, null, 2, null, null, null, false, null, null, null); }
    private RuleTemplateDocument document() {
        var band = new RuleSchema.Field(RuleSchema.Type.OBJECT, true, null, null, null, null, null, null, false,
                Map.of("from", number(true), "to", number(false), "ratio", number(true)), null, null);
        var tiers = new RuleSchema.Field(RuleSchema.Type.LIST, true, null, null, null, null, 20, null, false, null, band, null);
        return new RuleTemplateDocument(1, new RuleSchema(Map.of("amount", number(true)), false), new RuleSchema(Map.of("tiers", tiers), false), number(true),
                RuleDefinition.expression("round(input.amount * intervalValue(params.tiers, input.amount, 'from', 'to', 'ratio', 1), 2)"), Map.of(), List.of(),
                List.of(new RuleTemplateDocument.IntervalConstraint("bands", "tiers", "from", "to", true, BigDecimal.ZERO)));
    }
    private Map<String, Object> band(String from, String to, String ratio) {
        var row = new java.util.LinkedHashMap<String, Object>(); row.put("from", new BigDecimal(from));
        row.put("to", to == null ? null : new BigDecimal(to)); row.put("ratio", new BigDecimal(ratio)); return row;
    }
    @Test void constrainedTierParametersUseHalfOpenIntervalsAndAnOpenLastBand() {
        var params = Map.<String, Object>of("tiers", List.of(band("0", "300", "1"), band("300", null, ".9")));
        assertThat(evaluator.evaluate(document(), Map.of("amount", 300), params, Instant.now()).output()).isEqualTo(new BigDecimal("270.00"));
        assertThat(evaluator.evaluate(document(), Map.of("amount", 100), params, Instant.now()).output()).isEqualTo(new BigDecimal("100.00"));
    }
    @Test void overlappingGappedUnorderedAndNonLastOpenBandsAreRejected() {
        for (var rows : List.of(List.of(band("0", "300", "1"), band("200", null, ".9")),
                List.of(band("0", "100", "1"), band("200", null, ".9")),
                List.of(band("0", null, "1"), band("300", null, ".9")),
                List.of(band("100", "200", "1"))))
            assertThatThrownBy(() -> evaluator.evaluate(document(), Map.of("amount", 300), Map.of("tiers", rows), Instant.now()))
                    .isInstanceOf(RuleFailure.class);
    }
    @Test void intervalLookupCannotSilentlyPickOneOfOverlappingRows() {
        var engine = new ExpressionEngine();
        assertThatThrownBy(() -> engine.evaluate("intervalValue(params.tiers, input.amount, 'from', 'to', 'ratio', 1)", Map.of("amount", 400),
                Map.of("tiers", List.of(band("0", "500", "1"), band("300", null, ".9"))), Map.of())).isInstanceOf(RuleFailure.class);
    }
}
