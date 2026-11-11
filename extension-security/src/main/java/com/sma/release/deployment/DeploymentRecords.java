package com.sma.release.deployment;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import com.sma.extension.protocol.*;
import com.sma.extension.protocol.ExecutionDocuments.*;

public final class DeploymentRecords {
    private DeploymentRecords() {}
    public record ValidationJob(String id, String artifactId, String environment, String state, String workerId,
            Instant createdAt, Instant expiresAt, long policyRevision, String failure) {}
    public record ValidationResult(String artifactId, String sha256, ArtifactManifest manifest, ScanReport scan,
            Set<String> inventory, String validationKind, Instant validatedAt) {
        public ValidationResult { inventory = Set.copyOf(inventory); }
    }
    public record WorkAssignment(ValidationJob job, ResourcePolicy policy, java.util.Map<String,String> dependencies, String artifactSha256, long bytes, String name,SourceBuildPlan.Assignment build) {}
    public record BuildReservation(String jobId,String pluginId,long bytes) {}
    public record BuildOutput(String jobId,String artifactId,String sha256,long bytes,String state) {}
    public record BuildProvenance(String artifactId,String sourceDigest,SourceBuildPlan.Assignment sources,String workerId,String toolchainDigest,Instant builtAt) {}
    public record OutputRequest(String sha256,long bytes,String toolchainDigest) {
        public OutputRequest { Checks.hash(sha256);Checks.hash(toolchainDigest);Checks.require(bytes>0,"invalid-build-output"); }
    }
    public record Deployment(String id, String environment, long revision, Release active, Pending pending) {}
    public record Pending(Release release, String actorId, String actorSessionId, long actorCredentialVersion, ActionIntent.Action action, Instant expiresAt, Set<String> readyNodes,
            String state, String actionId) {
        public Pending { readyNodes = Set.copyOf(readyNodes); }
    }
    public record NodePackage(String state, String preparationId, SignedEnvelope preparation, SignedEnvelope activation, SignedEnvelope lease, List<ArtifactDelivery> artifacts) {
        public NodePackage { artifacts = List.copyOf(artifacts); }
    }
    public record ArtifactDelivery(String artifactId, String sha256, long bytes, SignedEnvelope proof,SignedEnvelope preparation) {}
    public record Ready(String deploymentId, String preparationId, long revision, String releaseSha256, Set<String> artifactHashes) {
        public Ready { Checks.id(deploymentId);Checks.id(preparationId);Checks.hash(releaseSha256);artifactHashes = Set.copyOf(artifactHashes);Checks.unique(artifactHashes,1000);artifactHashes.forEach(Checks::hash); }
    }
    public record Revoked(String sha256, Instant revokedAt, String actorId, String reason) {}
    public record NodeRetention(String nodeId,String environment,String deploymentId,Retention retained,Set<String> leasedHashes,Instant leaseExpiresAt) {
        public NodeRetention { leasedHashes=Set.copyOf(leasedHashes); }
    }
    public record NodeLeaseRetention(String id,String environment,Set<String> artifactHashes,Instant expiresAt) {
        public NodeLeaseRetention {artifactHashes=Set.copyOf(artifactHashes);}
    }
}
