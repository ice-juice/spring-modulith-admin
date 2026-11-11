package com.sma.extension.runtime;

/** Durable monotonic revision/high-water state. An implementation must atomically reject stale compare-and-set. */
public interface RuntimeCheckpoint {
    long revision(String deploymentId);
    void advance(String deploymentId, long expected, long next);
}
