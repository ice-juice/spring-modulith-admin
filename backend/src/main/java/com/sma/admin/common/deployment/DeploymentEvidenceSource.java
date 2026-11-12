package com.sma.admin.common.deployment;

/** Trusted host startup SPI. No HTTP or tenant-job entry, secret getters, or business side effects. */
public interface DeploymentEvidenceSource {
    /** Fixed owning-module domain, validated against the actual provider type. */
    String id();
    /** Evidence from the same objects used for real operations, not a separately reloaded key file. */
    DeploymentEvidence.Proof collect(DeploymentEvidence.Challenge challenge);
}
