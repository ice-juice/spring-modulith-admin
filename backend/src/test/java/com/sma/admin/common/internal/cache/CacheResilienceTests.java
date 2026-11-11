package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.cache.*;

class CacheResilienceTests {
    @Test void perRegionLimitRejectsWithoutBlockingOtherRegionsAndReleasesOnFailure() throws Exception {
        var guard = new CacheLoadGuard(registry(), new CacheResilienceProperties(2, 1, null, null, null));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var running = executor.submit(() -> guard.protect("config.first", () -> { entered.countDown(); await(release); return "ok"; }).get());
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> guard.protect("config.first", () -> "not reached").get()).isInstanceOf(CacheUnavailableException.class);
                assertThat(guard.protect("config.second", () -> "other").get()).isEqualTo("other");
            } finally { release.countDown(); }
            assertThat(running.get(5, TimeUnit.SECONDS)).isEqualTo("ok");
        }
        assertThatThrownBy(() -> guard.protect("config.first", () -> { throw new IllegalStateException("source failed"); }).get())
                .isInstanceOf(IllegalStateException.class).hasMessage("source failed");
        assertThat(guard.protect("config.first", () -> "released").get()).isEqualTo("released");
        assertThat(guard.rejected()).isEqualTo(1);
    }

    @Test void globalLimitAppliesAcrossRegionsAndInterruptedCallsCannotConsumePermits() throws Exception {
        var guard = new CacheLoadGuard(registry(), new CacheResilienceProperties(1, 1, null, null, null));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var running = executor.submit(() -> guard.protect("config.first", () -> { entered.countDown(); await(release); return "ok"; }).get());
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> guard.protect("config.second", () -> "no").get()).isInstanceOf(CacheUnavailableException.class);
            } finally { release.countDown(); }
            running.get(5, TimeUnit.SECONDS);
        }
        Thread.currentThread().interrupt();
        try { assertThatThrownBy(() -> guard.protect("config.first", () -> "no").get()).isInstanceOf(CacheUnavailableException.class); }
        finally { Thread.interrupted(); }
        assertThat(guard.protect("config.first", () -> "yes").get()).isEqualTo("yes");
    }

    @Test void staleSuccessCannotCloseCircuitAndOnlyOneRecoveryProbeIsPermitted() {
        var time = new AtomicLong();
        var circuit = new CacheCircuit(new CacheResilienceProperties(null, null, null, 1, Duration.ofSeconds(1)), time::get);
        var failure = circuit.acquire(); var staleSuccess = circuit.acquire();
        circuit.failure(failure); circuit.success(staleSuccess);
        assertThat(circuit.status()).isEqualTo(CacheCircuit.Status.OPEN);
        assertThat(circuit.acquire()).isNull();
        time.addAndGet(Duration.ofSeconds(1).toNanos());
        var probe = circuit.acquire(); assertThat(probe.probe()).isTrue();
        assertThat(circuit.status()).isEqualTo(CacheCircuit.Status.HALF_OPEN); assertThat(circuit.acquire()).isNull();
        circuit.failure(probe); assertThat(circuit.acquire()).isNull();
        time.addAndGet(Duration.ofSeconds(1).toNanos());
        circuit.success(circuit.acquire()); assertThat(circuit.status()).isEqualTo(CacheCircuit.Status.CLOSED);
        assertThat(circuit.acquire()).isNotNull();
    }

    @Test void invalidConcurrencyAndDelaySettingsFailClosed() {
        assertThatThrownBy(() -> new CacheResilienceProperties(1, 2, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CacheResilienceProperties(null, null, Duration.ofDays(1), null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CacheResilienceProperties(null, null, null, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CacheResilienceProperties(null, null, null, null, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    private CacheRegistry registry() {
        return new CacheRegistry(List.of(() -> List.of(region("config.first"), region("config.second"))));
    }
    private CacheDescriptor region(String id) {
        return new CacheDescriptor(id, "config", "config.cache.parameters", "basic-config", "parameter-read", CacheDescriptor.Kind.DATA,
                CacheDescriptor.Scope.TENANT, 1, CachePolicy.defaults(), Set.of(CacheDescriptor.Operation.CLEAR), Set.of("write"), "monitor:cache:clear");
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test gate timed out"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
    }
}
