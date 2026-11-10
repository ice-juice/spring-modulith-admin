package com.sma.extension.api;

import java.time.Instant;
import java.util.Objects;

/** Host-owned identity and execution version. Plugins never choose the caller or tenant. JSON bytes cross the boundary. */
public record PluginCall(String invocationId, String entryId, String businessKey, InvocationIdentity identity,
        String implementationVersion, long releaseRevision, Instant deadline, byte[] input) {
    public PluginCall {
        Objects.requireNonNull(invocationId); Objects.requireNonNull(entryId); Objects.requireNonNull(identity);
        Objects.requireNonNull(implementationVersion); Objects.requireNonNull(deadline);
        if (releaseRevision < 1) throw new IllegalArgumentException("Invalid release revision");
        input = Objects.requireNonNull(input).clone();
    }
    @Override public byte[] input() { return input.clone(); }
}
