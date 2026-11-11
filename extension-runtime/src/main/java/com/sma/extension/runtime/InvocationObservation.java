package com.sma.extension.runtime;

import com.sma.extension.api.InvocationIdentity;

/** No request/response body, business key, credential or plugin-provided error text. */
public record InvocationObservation(String invocationId,String deploymentId,long releaseRevision,String bundleId,
        String pluginId,String version,String artifactHash,String entryId,InvocationIdentity identity,boolean success,long elapsedNanos) {}
