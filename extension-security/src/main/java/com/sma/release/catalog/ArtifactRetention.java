package com.sma.release.catalog;

import java.time.Duration;
import java.util.Set;
import com.sma.extension.protocol.*;
import com.sma.release.store.ReleaseDatabase;
import com.sma.release.catalog.CatalogRecords.*;
import com.sma.release.deployment.DeploymentRecords.*;

/** Retention reports survive node lease expiry: offline durable work must never lose the bytes it references. */
public final class ArtifactRetention {
    private ArtifactRetention() {}
    public static void requireDeletable(ReleaseDatabase.Tx tx,Artifact artifact,ResourcePolicy policy) {
        Checks.require(!artifact.state().equals("DELETED") && !artifact.state().equals("DELETE_PENDING"),"artifact-deletion-already-started");
        for(var deployment:tx.list("deployment",Deployment.class)) {
            if(!deployment.environment().equals(artifact.environment()))continue;
            if(deployment.active()!=null) Checks.require(!contains(deployment.active(),artifact.id()),"artifact-currently-published");
            if(deployment.pending()!=null && deployment.pending().state().equals("PREPARING") && tx.now().isBefore(deployment.pending().expiresAt()))
                Checks.require(!contains(deployment.pending().release(),artifact.id()),"artifact-pending-publication");
        }
        for(var node:tx.list("node-retention",NodeRetention.class)) {
            if(!node.environment().equals(artifact.environment()))continue;
            Checks.require(!node.retained().artifactHashes().contains(artifact.sha256()),"artifact-durably-referenced");
            Checks.require(!tx.now().isBefore(node.leaseExpiresAt()) || !node.leasedHashes().contains(artifact.sha256()),"artifact-execution-lease-active");
        }
        for(var lease:tx.list("node-lease",NodeLeaseRetention.class)) {
            Checks.require(!lease.environment().equals(artifact.environment()) || !tx.now().isBefore(lease.expiresAt())
                    || !lease.artifactHashes().contains(artifact.sha256()),"artifact-execution-lease-active");
        }
        var activeJobs=tx.list("job",ValidationJob.class).stream().filter(j -> Set.of("RUNNING","QUEUED").contains(j.state()) && tx.now().isBefore(j.expiresAt())).toList();
        for(var job:activeJobs) {
            Checks.require(!job.artifactId().equals(artifact.id()),"artifact-validation-in-progress");
            tx.get("job-build",job.id(),SourceBuildPlan.Assignment.class).ifPresent(b -> Checks.require(b.inputs().stream().noneMatch(i -> i.artifactId().equals(artifact.id())),"artifact-build-in-progress"));
        }
        for(var provenance:tx.list("build-provenance",BuildProvenance.class)) {
            if(provenance.sources().inputs().stream().noneMatch(i -> i.artifactId().equals(artifact.id())))continue;
            var output=tx.get("artifact",provenance.artifactId(),Artifact.class);
            Checks.require(output.isEmpty() || output.get().state().equals("DELETED"),"artifact-build-source-retained");
        }
        var publishedIds=tx.list("history",com.sma.release.deployment.DeploymentService.History.class).stream().filter(h -> h.environment().equals(artifact.environment()))
                .flatMap(h -> h.release().bundles().stream()).flatMap(b -> b.members().stream()).map(ExecutionDocuments.Member::artifactId).collect(java.util.stream.Collectors.toSet());
        boolean published=publishedIds.contains(artifact.id());
        if(published) {
            var publishedAt=tx.get("publication-time",artifact.environment()+":"+artifact.id(),java.time.Instant.class).orElseThrow(() -> new ProtocolException("artifact-publication-time-unknown"));
            Checks.require(!tx.now().isBefore(publishedAt.plus(Duration.ofDays(policy.retentionDays()))),"artifact-retention-period-active");
            var history=tx.list("artifact",Artifact.class).stream().filter(a -> a.pluginId().equals(artifact.pluginId()) && a.environment().equals(artifact.environment()) && publishedIds.contains(a.id()) && !a.state().equals("DELETED"))
                    .sorted(java.util.Comparator.comparing(Artifact::createdAt).reversed().thenComparing(Artifact::id)).limit(policy.retainedVersions()).toList();
            Checks.require(history.stream().noneMatch(a -> a.id().equals(artifact.id())),"artifact-retained-version-limit");
        }
    }
    private static boolean contains(ExecutionDocuments.Release release,String id){return release.bundles().stream().flatMap(b -> b.members().stream()).anyMatch(m -> m.artifactId().equals(id));}
}
