package com.sma.admin.rule.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.security.TenantScope;
import com.sma.admin.rule.*;
import com.sma.admin.rule.RuleDefinition.*;

class RuleCapacityTests {
    private Flow flow() {
        return new Flow("start", List.of(
                new Node("start", NodeKind.START, null, null, "result", null, null, null, null, null, null),
                new Node("result", NodeKind.RESULT, "round(input.amount * params.ratio, 2)", null, null, null, null, null, null, null, null)));
    }
    @Test void cacheMaintenanceKeepsAnInFlightPlanUntilItsLeaseIsReleased() throws Exception {
        var plan = RuleFlowPlan.compile(flow()); var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var pending = pool.submit(() -> TenantScope.within(74001L, () -> {
                RuleFlowRuntime.execute("lease_capacity_test", plan, id -> {
                    if (id.equals("result")) {
                        entered.countDown();
                        try { if (!finish.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("lease wait expired"); }
                        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                    }
                    return null;
                }); return null;
            }));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                TenantScope.within(74001L, () -> {
                    assertThat(RuleFlowRuntime.tenantSize()).isEqualTo(1);
                    assertThat(RuleFlowRuntime.clearTenantIdle()).isZero(); return null;
                });
            } finally { finish.countDown(); }
            pending.get(10, TimeUnit.SECONDS);
            TenantScope.within(74001L, () -> { assertThat(RuleFlowRuntime.clearTenantIdle()).isEqualTo(1); return null; });
        }
    }
    @Test void recordsLocalWarmExecutionSamplesWithoutTurningThemIntoThroughputPromises() throws Exception {
        var expressions = new ExpressionEngine(); var json = new RuleJson(); var evaluator = new RuleEvaluator(expressions, id -> null, json);
        var input = new RuleSchema(Map.of("amount", RuleSchema.Field.required(RuleSchema.Type.DECIMAL)), false);
        var params = new RuleSchema(Map.of("ratio", RuleSchema.Field.required(RuleSchema.Type.DECIMAL)), false);
        var number = RuleSchema.Field.required(RuleSchema.Type.DECIMAL);
        var expression = new RuleTemplateDocument(1, input, params, number,
                RuleDefinition.expression("round(input.amount * params.ratio, 2)"), Map.of(), List.of());
        var flow = new RuleTemplateDocument(1, input, params, number, new RuleDefinition(Kind.FLOW, null, null, null, null, flow()), Map.of(), List.of());
        var rows = new java.util.ArrayList<Row>();
        for (int i = 0; i < 200; i++) rows.add(new Row("row" + i, Map.of("amount", new Cell("EQ", new Operand("literal", null, i))), Map.of("price", "input.amount")));
        var table = new RuleTemplateDocument(1, input, params,
                new RuleSchema.Field(RuleSchema.Type.OBJECT, true, null, null, null, null, null, null, false, Map.of("price", number), null, null),
                new RuleDefinition(Kind.DECISION_TABLE, null, null, null, null, null,
                        new DecisionTable(HitPolicy.UNIQUE, List.of(new Column("amount", new Operand("input", "amount", null), number)), rows, Map.of("price", 0))), Map.of(), List.of());
        var measurements = new java.util.LinkedHashMap<String, Object>();
        long[] cold = new long[100]; long[] warm = new long[100];
        for (int i = 0; i < 100; i++) {
            String source = "input.amount + " + i;
            long started = System.nanoTime(); expressions.validate(source); cold[i] = (System.nanoTime() - started) / 1000;
            started = System.nanoTime(); expressions.validate(source); warm[i] = (System.nanoTime() - started) / 1000;
        }
        java.util.Arrays.sort(cold); java.util.Arrays.sort(warm);
        measurements.put("coldCompile", Map.of("samples", 100, "p50Micros", cold[49], "p95Micros", cold[94], "maxMicros", cold[99]));
        measurements.put("warmCompileLookup", Map.of("samples", 100, "p50Micros", warm[49], "p95Micros", warm[94], "maxMicros", warm[99]));
        for (var entry : Map.of("expression", expression, "flow", flow, "table200Rows", table).entrySet()) {
            for (int i = 0; i < 20; i++) evaluator.evaluate(entry.getValue(), Map.of("amount", 100), Map.of("ratio", new BigDecimal(".9")), Instant.EPOCH);
            long[] samples = new long[100];
            for (int i = 0; i < samples.length; i++) {
                long start = System.nanoTime();
                var value = evaluator.evaluate(entry.getValue(), Map.of("amount", 100), Map.of("ratio", new BigDecimal(".9")), Instant.EPOCH);
                samples[i] = (System.nanoTime() - start) / 1000;
                assertThat(value.output()).isNotNull();
                if (!entry.getKey().equals("table200Rows")) assertThat(value.output()).isEqualTo(new BigDecimal("90.00"));
            }
            java.util.Arrays.sort(samples);
            measurements.put(entry.getKey(), Map.of("samples", samples.length, "p50Micros", samples[49], "p95Micros", samples[94], "maxMicros", samples[99]));
        }
        measurements.put("scope", "single JVM sequential, warm, validation included, no HTTP/database, not a capacity SLA");
        var directory = java.nio.file.Path.of(RuleCapacityTests.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        java.nio.file.Files.writeString(directory.resolve("rule-capacity-samples.json"), json.write(measurements));
        System.out.println("RULE_CAPACITY_SAMPLES=" + json.write(measurements));
    }
}
