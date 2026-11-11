package com.sma.admin.common.internal.cache;

import java.util.function.LongSupplier;

/** Monotonic cooldown; only one recovery probe, and stale in-flight successes cannot close a newer open cycle. */
final class CacheCircuit {
    record Permit(long epoch, boolean probe) {}
    enum Status { CLOSED, OPEN, HALF_OPEN }
    private final int threshold;
    private final long retryNanos;
    private final LongSupplier nanoTime;
    private int failures;
    private long epoch, retryAt;
    private boolean probing;

    CacheCircuit(CacheResilienceProperties properties) { this(properties, System::nanoTime); }
    CacheCircuit(CacheResilienceProperties properties, LongSupplier nanoTime) {
        threshold = properties.failureThreshold(); retryNanos = properties.retryDelay().toNanos(); this.nanoTime = nanoTime;
    }
    synchronized Permit acquire() {
        if (failures < threshold) return new Permit(epoch, false);
        if (probing || nanoTime.getAsLong() - retryAt < 0) return null;
        probing = true; return new Permit(epoch, true);
    }
    synchronized void success(Permit permit) {
        if (permit == null || permit.epoch() != epoch) return;
        failures = 0; probing = false;
        if (permit.probe()) epoch++;
    }
    synchronized void failure(Permit permit) {
        if (permit == null || permit.epoch() != epoch) return;
        failures = permit.probe() ? threshold : failures + 1;
        if (failures >= threshold) { retryAt = nanoTime.getAsLong() + retryNanos; probing = false; epoch++; }
    }
    synchronized Status status() { return probing ? Status.HALF_OPEN : failures >= threshold ? Status.OPEN : Status.CLOSED; }
}
