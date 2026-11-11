package com.sma.release.catalog;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.sma.extension.protocol.Hashes;
import com.sma.extension.protocol.ProtocolException;
import com.sma.release.auth.SecurityRecords;
import com.sma.release.catalog.CatalogRecords.*;
import com.sma.release.store.ReleaseDatabase;
import com.sma.release.deployment.DeploymentRecords.*;

/** Bounded housekeeping. No active artifact or live business version is ever deleted here. */
@Service
public class ControlMaintenance {
    private final ReleaseDatabase database;
    private final ArtifactFiles files;
    private final com.sma.release.deployment.DeploymentService deployments;
    public ControlMaintenance(ReleaseDatabase database,ArtifactFiles files,com.sma.release.deployment.DeploymentService deployments) { this.database=database;this.files=files;this.deployments=deployments; }
    @Scheduled(fixedDelayString="PT1M",initialDelayString="PT1M")
    public void expire() {
        database.transaction(tx -> {
            deployments.reconcileAll(tx);
            deployments.evaluateGuards(tx);
            tx.list("node-lease",NodeLeaseRetention.class).stream().filter(l -> !tx.now().isBefore(l.expiresAt())).limit(200).forEach(l -> tx.delete("node-lease",l.id()));
            tx.list("session",SecurityRecords.Session.class).stream().filter(s -> !tx.now().isBefore(s.expiresAt())).limit(200).forEach(s -> {tx.delete("session",s.tokenHash());tx.delete("console-origin",s.id());});
            tx.list("console-ticket",com.sma.release.auth.ConsoleIdentityService.Ticket.class).stream().filter(t -> !tx.now().isBefore(t.expiresAt())).limit(200).forEach(t -> tx.delete("console-ticket",t.id()));
            tx.list("challenge",SecurityRecords.Challenge.class).stream().filter(c -> !tx.now().isBefore(c.expiresAt())).limit(200).forEach(c -> tx.delete("challenge",c.id()));
            tx.list("grant",SecurityRecords.Grant.class).stream().filter(g -> !tx.now().isBefore(g.expiresAt())).limit(200).forEach(g -> tx.delete("grant",g.tokenHash()));
            int remaining=200;
            for(var artifact:tx.list("artifact",Artifact.class).stream().filter(a -> a.state().equals("DELETE_PENDING")).limit(50).toList()) {
                try {files.deleteUnpublishedArtifact(artifact.id());}catch(IOException failure){throw new ProtocolException("artifact-cleanup-failed",failure);}
                tx.put("artifact",artifact.id(),new Artifact(artifact.id(),artifact.uploadId(),artifact.fileIndex(),artifact.groupId(),artifact.pluginId(),artifact.environment(),artifact.sha256(),artifact.bytes(),artifact.name(),"DELETED",artifact.manifest(),artifact.report(),artifact.createdAt()));
                tx.audit("security-service","artifact-delete",artifact.id(),"deleted",artifact.sha256());remaining--;
            }
            var uploads=tx.list("upload",Upload.class);
            for(var upload:uploads) {
                if(remaining<=0) break;
                boolean complete=upload.state().equals("COMPLETE");
                boolean expired=upload.state().equals("RECEIVING") && !tx.now().isBefore(upload.expiresAt().plus(Duration.ofHours(24)));
                if(!complete && !expired) continue;
                List<Chunk> chunks=tx.list("chunk",Chunk.class).stream().filter(c -> c.uploadId().equals(upload.id())).toList();
                for(var chunk:chunks) {
                    if(remaining--<=0) break;
                    try {files.deleteChunk(upload.id(),chunk.fileIndex(),chunk.index());}
                    catch(IOException error) {throw new ProtocolException("upload-cleanup-failed",error);}
                    tx.delete("chunk",chunk.id());
                }
                // Keep reservation until all staged chunks are actually gone. Failed cleanup cannot free fictitious disk capacity.
                if(expired && tx.list("chunk",Chunk.class).stream().noneMatch(c -> c.uploadId().equals(upload.id()))) {
                    for(int index=0;index<upload.files().size();index++) {
                        String artifactId=upload.id()+"-"+index;
                        if(tx.get("artifact",artifactId,Artifact.class).isEmpty()) {
                            try {files.deleteUnpublishedArtifact(artifactId);}
                            catch(IOException error) {throw new ProtocolException("upload-cleanup-failed",error);}
                        }
                    }
                    tx.put("upload",upload.id(),upload.state("EXPIRED"));
                    tx.audit("security-service","upload-expire",upload.id(),"staging-cleaned",Hashes.sha256(new byte[0]));
                }
            }
            for(var job:tx.list("job",ValidationJob.class)) {
                if(remaining<=0) break;
                boolean finished=job.state().equals("COMPLETE");
                boolean expired=!tx.now().isBefore(job.expiresAt().plus(Duration.ofHours(24)));
                if(!finished && !expired) continue;
                for(var chunk:tx.list("build-chunk",Chunk.class).stream().filter(c -> c.uploadId().equals(job.id())).toList()) {
                    if(remaining--<=0) break;
                    try {files.deleteChunk(job.id(),0,chunk.index());}catch(IOException failure){throw new ProtocolException("build-cleanup-failed",failure);}
                    tx.delete("build-chunk",chunk.id());
                }
                if(tx.list("build-chunk",Chunk.class).stream().noneMatch(c -> c.uploadId().equals(job.id()))) {
                    var output=tx.get("build-output",job.id(),BuildOutput.class);
                    if(output.isPresent() && tx.get("artifact",output.get().artifactId(),Artifact.class).isEmpty()) {
                        try {files.deleteUnpublishedArtifact(output.get().artifactId());}catch(IOException failure){throw new ProtocolException("build-cleanup-failed",failure);}
                    }
                    tx.delete("build-reservation",job.id());
                    if(expired && !finished && !job.state().equals("FAILED")) tx.put("job",job.id(),new ValidationJob(job.id(),job.artifactId(),job.environment(),"FAILED",job.workerId(),job.createdAt(),job.expiresAt(),job.policyRevision(),"job-expired"));
                }
            }
            return null;
        });
    }
}
