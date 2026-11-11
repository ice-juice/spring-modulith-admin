package com.sma.extension.runtime;

import com.sma.extension.api.InvocationIdentity;
import com.sma.extension.protocol.ArtifactManifest;

/** Implemented by the stable host. Identity, entry authorization and business capabilities are always checked here. */
public interface RuntimeHost {
    void authorize(String pluginId, ArtifactManifest.Entry entry, InvocationIdentity identity);
    byte[] capability(String pluginId, String version, String invocationId, InvocationIdentity identity, String capabilityId, byte[] json) throws Exception;
    void metric(String pluginId, String version, String metric, long increment);
    long durableReferences(String artifactHash);
    /** Runtime-generated metadata only. Observability failure must not change an already completed business result. */
    default void observe(InvocationObservation observation) {}
}
