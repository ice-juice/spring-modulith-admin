package com.sma.admin.common.security;

/** Typed observation only. Implementations capture the current identity; callers never submit arbitrary envelopes. */
public interface SecuritySignals {
    enum Admission { QUEUED_VOLATILE, DISABLED, REJECTED }
    /** No persistence guarantee: process loss before the worker's disk commit can lose a queued observation. */
    Admission observe(SecuritySignal.Fact fact);
}
