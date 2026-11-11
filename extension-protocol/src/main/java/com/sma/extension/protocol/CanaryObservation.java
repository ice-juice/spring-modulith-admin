package com.sma.extension.protocol;

/** Complete minute of host-generated execution counts, never plugin-supplied metrics. Slow means >= 1 second. */
public record CanaryObservation(String deploymentId,long revision,String bundleId,long minute,long calls,long failures,long slowCalls,boolean complete) {
    public CanaryObservation {Checks.id(deploymentId);Checks.id(bundleId);Checks.require(revision>0 && minute>0 && calls>=0 && calls<=1_000_000_000L && failures>=0 && failures<=calls && slowCalls>=0 && slowCalls<=calls,"invalid-observation");}
}
