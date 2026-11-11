package com.sma.admin.extension.internal.service;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sma.extension.api.InvocationIdentity;
import com.sma.extension.runtime.InvocationObservation;

class ExtensionTelemetryTests {
    @Test void restartedOrInFlightNodeCannotReportAHealthyCompleteWindowAndRetryIsImmutable() {
        var telemetry=new ExtensionTelemetry();var first=telemetry.observation("deployment",1,"candidate",false);
        assertThat(first.complete()).isFalse();assertThat(telemetry.observation("deployment",1,"candidate",true)).isEqualTo(first);
    }
    @Test void countersKeepVersionsDistinctAndOverflowCannotBreakBusinessCalls() {
        var telemetry=new ExtensionTelemetry();var identity=new InvocationIdentity(1,"42",InvocationIdentity.Kind.USER);
        telemetry.observe(new InvocationObservation("call-one","deployment",1,"stable","example","1.0.0","a".repeat(64),"execute",identity,true,10_000_000));
        telemetry.observe(new InvocationObservation("call-two","deployment",2,"canary","example","2.0.0","b".repeat(64),"execute",identity,false,500_000_000));
        telemetry.metric("example","1.0.0","count",Long.MAX_VALUE);telemetry.metric("example","1.0.0","count",1);
        var snapshot=telemetry.snapshot();assertThat(snapshot.entries()).hasSize(2);
        assertThat(snapshot.entries().getFirst().failures()).isZero();assertThat(snapshot.entries().get(1).failures()).isEqualTo(1);
        assertThat(snapshot.entries().get(1).p95UpperBoundMs()).isEqualTo(500);
        assertThat(snapshot.customMetrics().get("example:1.0.0:count")).isEqualTo(Long.MAX_VALUE);
    }
}
