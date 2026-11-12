package com.sma.admin.common.deployment;

/** Lifecycle of an owned auxiliary transport, not a database or global application shutdown API.
 * Only trusted host deployment control may initiate shutdown, after stopping new parent admission
 * AND waiting for admitted parent requests/work to finish. A parent may make several transport calls.
 * Implementations must reject authenticated/HTTP/transaction callers, stop new submissions, and let
 * accepted tasks finish before shutting down their library client. This call must not wait for I/O.
 * An idempotent shutdown request is not proof of completion. Observe actual executor AND client
 * termination; a timeout or cancelled Future cannot satisfy that proof. No implicit restart exists.
 */
public interface DeploymentTransport {
    String id();
    void shutdownTransport();
    /** Bounded read-only observation; false before shutdown or while any owned task/client remains. */
    boolean transportTerminated();
}
