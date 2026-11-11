package com.sma.extension.protocol;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bodies of signed ARTIFACT / RELEASE / LEASE documents; expected claims context is checked separately. */
public final class ExecutionDocuments {
    private ExecutionDocuments() {}
    public record Retention(Set<String> artifactHashes,Set<String> bundleIds) {
        public Retention {
            artifactHashes=Set.copyOf(artifactHashes);bundleIds=Set.copyOf(bundleIds);
            Checks.unique(artifactHashes,1000);Checks.unique(bundleIds,1000);artifactHashes.forEach(Checks::hash);bundleIds.forEach(Checks::id);
            Checks.require(artifactHashes.isEmpty()==bundleIds.isEmpty(),"retained-bundle-required");
        }
    }
    public record RevocationList(Set<String> artifactHashes) {
        public RevocationList { artifactHashes=Set.copyOf(artifactHashes);Checks.unique(artifactHashes,10000);artifactHashes.forEach(Checks::hash); }
    }
    public record ArtifactProof(String artifactId, String groupId, long groupRevision, String pluginId,
            String version, String sha256, long bytes, String manifestSha256, long policyRevision,
            String scanSha256, String validationKind, Map<String, String> dependencyAllowlist,String provenanceSha256) {
        public ArtifactProof(String artifactId,String groupId,long groupRevision,String pluginId,String version,String sha256,long bytes,
                String manifestSha256,long policyRevision,String scanSha256,String validationKind,Map<String,String> dependencyAllowlist) {
            this(artifactId,groupId,groupRevision,pluginId,version,sha256,bytes,manifestSha256,policyRevision,scanSha256,validationKind,dependencyAllowlist,null);
        }
        public ArtifactProof {
            Checks.id(artifactId); Checks.id(groupId); Checks.id(pluginId); Checks.text(version, 80);
            Checks.hash(sha256); Checks.hash(manifestSha256); Checks.hash(scanSha256);
            Checks.require(groupRevision > 0 && bytes > 0 && policyRevision > 0, "invalid-artifact-proof");
            Checks.require(Set.of("IMPORT", "BUILD").contains(validationKind), "invalid-validation-kind");
            if(validationKind.equals("BUILD")) Checks.hash(provenanceSha256);
            else Checks.require(provenanceSha256==null,"unexpected-build-provenance");
            dependencyAllowlist = Map.copyOf(dependencyAllowlist);
            Checks.require(dependencyAllowlist.size() <= 100, "too-many-dependencies");
            dependencyAllowlist.values().forEach(Checks::hash);
        }
    }
    public record Member(String pluginId, String artifactId, String sha256) {
        public Member { Checks.id(pluginId); Checks.id(artifactId); Checks.hash(sha256); }
    }
    /** Version vector is sorted and complete: related plugin calls use the same bundle id. */
    public record Bundle(String id, List<Member> members) {
        public Bundle {
            Checks.id(id); members = List.copyOf(members);
            Checks.require(!members.isEmpty(), "empty-release-bundle"); Checks.unique(members.stream().map(Member::pluginId).toList(), 100);
            Checks.require(members.equals(members.stream().sorted(java.util.Comparator.comparing(Member::pluginId)).toList()), "unsorted-release-members");
        }
    }
    public record Release(long revision, long previousRevision, long policyRevision, String deploymentId,
            Bundle stable, Bundle canary, int canaryBasisPoints, String bucketSalt, Set<String> canarySubjects,
            Set<String> targetNodes, String configurationSha256) {
        public Release {
            Checks.require(revision > previousRevision && previousRevision >= 0 && policyRevision > 0, "invalid-release-revision");
            Checks.id(deploymentId); Checks.require(stable != null, "stable-bundle-required");
            Checks.require(canaryBasisPoints >= 0 && canaryBasisPoints <= 10000 && (canary != null || canaryBasisPoints == 0), "invalid-canary-weight");
            Checks.text(bucketSalt, 100); Checks.hash(configurationSha256);
            canarySubjects = Set.copyOf(canarySubjects); targetNodes = Set.copyOf(targetNodes);
            Checks.unique(canarySubjects, 1000); canarySubjects.forEach(v -> Checks.text(v, 200));
            Checks.require(!targetNodes.isEmpty(), "target-nodes-required"); Checks.unique(targetNodes, 100); targetNodes.forEach(Checks::id);
            if (canary == null) Checks.require(canarySubjects.isEmpty(), "canary-bundle-required");
            else Checks.require(!canary.id().equals(stable.id()) && stable.members().stream().map(Member::pluginId).toList()
                    .equals(canary.members().stream().map(Member::pluginId).toList()), "incompatible-bundle-members");
        }
        public List<Bundle> bundles() { return canary == null ? List.of(stable) : List.of(stable, canary); }
    }
    /** Lease authorizes exact content already prepared on a node, including older versions with durable work references. */
    public record Lease(String nodeId, String deploymentId, long releaseRevision, Set<String> artifactHashes, Set<String> revokedHashes) {
        public Lease {
            Checks.id(nodeId); Checks.id(deploymentId); Checks.require(releaseRevision > 0, "invalid-lease-revision");
            artifactHashes = Set.copyOf(artifactHashes); revokedHashes = Set.copyOf(revokedHashes);
            Checks.unique(artifactHashes, 1000); Checks.unique(revokedHashes, 1000);
            artifactHashes.forEach(Checks::hash); revokedHashes.forEach(Checks::hash);
        }
    }
}
