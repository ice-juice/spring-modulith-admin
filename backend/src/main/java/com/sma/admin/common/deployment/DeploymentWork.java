package com.sma.admin.common.deployment;

import java.util.Optional;

/**
 * Process-local admission for trusted host schedulers and system jobs, never a business authorization.
 * Acquire on the execution thread before each new durable claim and before starting its transaction.
 * Keep the permit until that unit actually finishes; do not reserve it while merely queued.
 * A refused claim stays in its existing durable queue. An admitted unit retains its native fence/receipt
 * semantics, including UNKNOWN; this API neither cancels it nor retries or marks it complete.
 */
public interface DeploymentWork {
    Optional<Permit> tryStart();

    /** Local observation only. Zero work does not mean HTTP or WebSocket connections have drained. */
    Snapshot snapshot();

    record Snapshot(boolean accepting, boolean draining, int activeWork) {}

    /** Idempotent, thread-safe release; may be called by a completion thread without an actor context. */
    interface Permit extends AutoCloseable {
        @Override void close();
    }
}
