package com.sma.admin.mail.internal.service;

import com.sma.admin.common.deployment.DeploymentWork;

/** One admitted claim: completion requires both domain handling and the actual transport thread. */
final class MailDispatchAttempt implements AutoCloseable {
    private final DeploymentWork.Permit permit;
    private boolean ownerDone, transportStarted, transportDone = true, released;

    MailDispatchAttempt(DeploymentWork.Permit permit) { this.permit = permit; }

    synchronized DeploymentWork.Permit transport() {
        if (ownerDone || transportStarted) throw new IllegalStateException("Mail transport already assigned");
        transportStarted = true;
        transportDone = false;
        return this::transportFinished;
    }

    private synchronized void transportFinished() { transportDone = true; releaseIfComplete(); }

    @Override public synchronized void close() { ownerDone = true; releaseIfComplete(); }

    private void releaseIfComplete() {
        if (ownerDone && transportDone && !released) { released = true; permit.close(); }
    }
}
