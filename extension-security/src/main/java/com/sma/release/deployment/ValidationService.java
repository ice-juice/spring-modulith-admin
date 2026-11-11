package com.sma.release.deployment;

import static com.sma.release.deployment.DeploymentRecords.*;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.sma.extension.protocol.*;
import com.sma.release.auth.MachineService;
import com.sma.release.catalog.ArtifactFiles;
import com.sma.release.catalog.StorageUsage;
import com.sma.release.catalog.CatalogRecords.*;
import com.sma.release.store.ReleaseDatabase;

@Service
public class ValidationService {
    private final ReleaseDatabase database;
    private final MachineService machines;
    private final ArtifactFiles files;
    public ValidationService(ReleaseDatabase database, MachineService machines, ArtifactFiles files) { this.database=database; this.machines=machines; this.files=files; }
    public String enqueue(ReleaseDatabase.Tx tx, Artifact artifact, long policyRevision) {
        Checks.require(!Set.of("DELETE_PENDING","DELETED").contains(artifact.state()),"artifact-deleted");
        var existing = tx.list("job", ValidationJob.class).stream().filter(j -> j.artifactId().equals(artifact.id()) && Set.of("QUEUED","RUNNING").contains(j.state())).findFirst();
        if (existing.isPresent()) return existing.get().id();
        var policy = policy(tx);
        Checks.require(tx.list("job", ValidationJob.class).stream().filter(j -> Set.of("QUEUED","RUNNING").contains(j.state())).count() < policy.queuedJobs()+policy.workers(), "validation-queue-full");
        String id = "j-" + UUID.randomUUID();
        var sourcePlan=artifact.fileIndex()>=0?tx.get("source-plan",artifact.uploadId(),SourceBuildPlan.class):java.util.Optional.<SourceBuildPlan>empty();
        if(sourcePlan.isPresent()) {
            var upload=tx.get("upload",artifact.uploadId(),Upload.class).orElseThrow();
            Checks.require(upload.state().equals("COMPLETE"),"source-upload-incomplete");
            var inputs=tx.list("artifact",Artifact.class).stream().filter(a -> a.uploadId().equals(upload.id()) && a.fileIndex()>=0)
                    .map(a -> new SourceBuildPlan.Input(a.id(),a.name(),a.sha256(),a.bytes())).sorted(java.util.Comparator.comparing(SourceBuildPlan.Input::name)).toList();
            Checks.require(inputs.size()==upload.files().size(),"source-upload-incomplete");
            var plugin=tx.get("plugin",artifact.pluginId(),Plugin.class).orElseThrow();
            StorageUsage.reserve(tx,policy,plugin.id(),plugin.groupId(),policy.jarBytes());
            checkDisk(tx,policy,policy.jarBytes()*2);
            var build=new SourceBuildPlan.Assignment(sourcePlan.get(),inputs,SourceBuildPlan.Assignment.digest(sourcePlan.get(),inputs));
            tx.put("job-build",id,build);tx.put("build-reservation",id,new BuildReservation(id,plugin.id(),policy.jarBytes()));
        }
        tx.put("job", id, new ValidationJob(id, artifact.id(), artifact.environment(), "QUEUED", null, tx.now(), tx.now().plus(Duration.ofHours(1)), policyRevision, null)); return id;
    }
    public WorkAssignment claim(String token, String environment) {
        return database.transaction(tx -> {
            var worker=machines.authenticate(tx, token, MachineService.Kind.WORKER, environment); var policy=policy(tx);
            var jobs=tx.list("job", ValidationJob.class);
            for (var job: jobs) if (Set.of("RUNNING","QUEUED").contains(job.state()) && !tx.now().isBefore(job.expiresAt())) {
                tx.put("job",job.id(),new ValidationJob(job.id(),job.artifactId(),job.environment(),"FAILED",job.workerId(),job.createdAt(),job.expiresAt(),job.policyRevision(),"job-expired"));
            }
            long running = jobs.stream().filter(j -> j.state().equals("RUNNING") && tx.now().isBefore(j.expiresAt())).count();
            if (running >= policy.workers()) return null;
            var selected=jobs.stream().filter(j -> j.environment().equals(environment) && j.state().equals("QUEUED") && tx.now().isBefore(j.expiresAt())).findFirst();
            if (selected.isEmpty()) return null;
            var job=selected.get();
            Checks.require(job.policyRevision()==policy.revision(), "policy-revision-conflict");
            var artifact=tx.get("artifact",job.artifactId(),Artifact.class).orElseThrow();
            var updated=new ValidationJob(job.id(),job.artifactId(),environment,"RUNNING",worker.id(),job.createdAt(),tx.now().plusSeconds(policy.workerSeconds()),job.policyRevision(),null);
            tx.put("job",job.id(),updated); tx.audit(worker.id(),"validation-claim",job.id(),"running",artifact.sha256());
            return new WorkAssignment(updated,policy,dependencies(tx),artifact.sha256(),artifact.bytes(),artifact.name(),tx.get("job-build",job.id(),SourceBuildPlan.Assignment.class).orElse(null));
        });
    }
    public InputStream artifact(String token, String jobId) throws IOException {
        String artifactId=database.transaction(tx -> requireWorker(tx,token,jobId).artifactId());
        return files.openArtifact(artifactId);
    }
    public InputStream source(String token,String jobId,String artifactId) throws IOException {
        database.transaction(tx -> {requireWorker(tx,token,jobId);
            var build=tx.get("job-build",jobId,SourceBuildPlan.Assignment.class).orElseThrow(() -> new ProtocolException("not-a-build-job"));
            Checks.require(build.inputs().stream().anyMatch(i -> i.artifactId().equals(artifactId)),"build-input-not-authorized");return null;});
        return files.openArtifact(artifactId);
    }
    public BuildOutput beginOutput(String token,String jobId,OutputRequest request) {
        return database.transaction(tx -> {
            var job=requireWorker(tx,token,jobId);var policy=policy(tx);
            Checks.require(job.policyRevision()==policy.revision(),"policy-revision-conflict");
            tx.get("job-build",jobId,SourceBuildPlan.Assignment.class).orElseThrow(() -> new ProtocolException("not-a-build-job"));
            Checks.require(request.bytes()<=policy.jarBytes(),"build-output-too-large");
            var previous=tx.get("build-output",jobId,BuildOutput.class);
            if(previous.isPresent()) {
                Checks.require(previous.get().sha256().equals(request.sha256()) && previous.get().bytes()==request.bytes()
                        && tx.get("build-output-request",jobId,OutputRequest.class).orElseThrow().equals(request),"build-output-conflict");return previous.get();
            }
            var reserved=tx.get("build-reservation",jobId,BuildReservation.class).orElseThrow(() -> new ProtocolException("build-reservation-required"));
            Checks.require(request.bytes()<=reserved.bytes(),"build-output-too-large");
            checkDisk(tx,policy,request.bytes()*2);
            var output=new BuildOutput(jobId,"build-"+jobId,request.sha256(),request.bytes(),"RECEIVING");
            tx.put("build-output",jobId,output);tx.put("build-output-request",jobId,request);return output;
        });
    }
    public Chunk outputChunk(String token,String jobId,int index,byte[] bytes) {
        Checks.require(bytes!=null && bytes.length>0 && bytes.length<=ArtifactFiles.CHUNK_BYTES,"invalid-chunk-size");
        return database.transaction(tx -> {
            requireWorker(tx,token,jobId);var output=tx.get("build-output",jobId,BuildOutput.class).orElseThrow(() -> new ProtocolException("build-output-required"));
            long offset=(long)index*ArtifactFiles.CHUNK_BYTES;
            Checks.require(output.state().equals("RECEIVING") && index>=0 && offset<output.bytes()
                    && bytes.length==Math.min(ArtifactFiles.CHUNK_BYTES,output.bytes()-offset),"invalid-chunk-size");
            String id=jobId+":"+index,hash=Hashes.sha256(bytes);
            var previous=tx.get("build-chunk",id,Chunk.class);
            if(previous.isPresent()) {Checks.require(previous.get().sha256().equals(hash),"chunk-content-conflict");return previous.get();}
            try {files.writeChunk(jobId,0,index,bytes);}catch(IOException failure){throw new ProtocolException("build-storage-failed",failure);}
            var chunk=new Chunk(id,jobId,0,index,hash,bytes.length);tx.put("build-chunk",id,chunk);return chunk;
        });
    }
    public Artifact finishOutput(String token,String jobId) {
        return database.transaction(tx -> {
            var job=requireWorker(tx,token,jobId);var output=tx.get("build-output",jobId,BuildOutput.class).orElseThrow(() -> new ProtocolException("build-output-required"));
            if(output.state().equals("COMPLETE")) return tx.get("artifact",output.artifactId(),Artifact.class).orElseThrow();
            Checks.require(job.policyRevision()==policy(tx).revision(),"policy-revision-conflict");
            var build=tx.get("job-build",jobId,SourceBuildPlan.Assignment.class).orElseThrow();
            var source=tx.get("artifact",job.artifactId(),Artifact.class).orElseThrow();
            var chunks=tx.list("build-chunk",Chunk.class).stream().filter(c -> c.uploadId().equals(jobId)).sorted(java.util.Comparator.comparingInt(Chunk::index)).toList();
            Checks.require(chunks.size()==(output.bytes()+ArtifactFiles.CHUNK_BYTES-1)/ArtifactFiles.CHUNK_BYTES,"missing-upload-chunk");
            try {files.assemble(output.artifactId(),jobId,0,chunks,output.bytes(),output.sha256());}
            catch(IOException failure){throw new ProtocolException("build-storage-failed",failure);}
            var artifact=new Artifact(output.artifactId(),source.uploadId(),-1,source.groupId(),source.pluginId(),source.environment(),output.sha256(),output.bytes(),
                    build.plan().manifest().pluginId()+"-"+build.plan().manifest().version()+".jar","BUILT",null,null,tx.now());
            tx.put("artifact",artifact.id(),artifact);tx.delete("build-reservation",jobId);
            tx.put("build-provenance",artifact.id(),new BuildProvenance(artifact.id(),build.sourceDigest(),build,job.workerId(),tx.get("build-output-request",jobId,OutputRequest.class).orElseThrow().toolchainDigest(),tx.now()));
            tx.put("build-output",jobId,new BuildOutput(jobId,output.artifactId(),output.sha256(),output.bytes(),"COMPLETE"));
            tx.audit(job.workerId(),"build-output",artifact.id(),"hash-verified",output.sha256());return artifact;
        });
    }
    public ValidationJob complete(String token, String jobId, ValidationResult result) {
        return database.transaction(tx -> {
            var job=requireWorker(tx,token,jobId);
            var build=tx.get("job-build",jobId,SourceBuildPlan.Assignment.class);
            String artifactId=job.artifactId();
            if(build.isPresent()) {
                var output=tx.get("build-output",jobId,BuildOutput.class).orElseThrow(() -> new ProtocolException("build-output-required"));
                Checks.require(output.state().equals("COMPLETE"),"build-output-incomplete");artifactId=output.artifactId();
                Checks.require(result.validationKind().equals("BUILD") && Hashes.sha256(ProtocolJson.write(result.manifest()))
                        .equals(Hashes.sha256(ProtocolJson.write(build.get().plan().manifest()))),"build-manifest-mismatch");
            } else Checks.require(result.validationKind().equals("IMPORT"),"invalid-validation-kind");
            var artifact=tx.get("artifact",artifactId,Artifact.class).orElseThrow();
            Checks.require(job.policyRevision()==policy(tx).revision(), "policy-revision-conflict");
            Checks.require(result.artifactId().equals(artifact.id()) && result.sha256().equals(artifact.sha256()) && result.manifest()!=null
                    && result.manifest().pluginId().equals(artifact.pluginId()) && result.scan()!=null && !result.validatedAt().isAfter(tx.now())
                    && Duration.between(result.validatedAt(),tx.now()).compareTo(Duration.ofMinutes(10))<=0, "validation-result-mismatch");
            var allowed=dependencies(tx);
            result.manifest().dependencies().forEach(d -> Checks.require(d.sha256().equals(allowed.get(d.coordinate())), "dependency-not-allowed"));
            Checks.require(result.inventory().contains(artifact.sha256()) && result.inventory().stream().allMatch(h -> h.equals(artifact.sha256()) || allowed.containsValue(h)), "validation-inventory-mismatch");
            Checks.require(tx.list("validation",ValidationResult.class).stream().noneMatch(v -> v.manifest().pluginId().equals(result.manifest().pluginId())
                    && v.manifest().version().equals(result.manifest().version()) && !v.sha256().equals(result.sha256())),"version-content-conflict");
            var authoritative=new ValidationResult(result.artifactId(),result.sha256(),result.manifest(),result.scan(),result.inventory(),
                    tx.get("build-provenance",artifact.id(),BuildProvenance.class).isPresent()?"BUILD":"IMPORT",result.validatedAt());
            tx.put("validation",artifact.id(),authoritative);
            var decision=gate(tx,artifact,result);
            String state=decision.allowed()?"VERIFIED":"BLOCKED";
            tx.put("artifact",artifact.id(),new Artifact(artifact.id(),artifact.uploadId(),artifact.fileIndex(),artifact.groupId(),artifact.pluginId(),artifact.environment(),artifact.sha256(),artifact.bytes(),artifact.name(),state,result.manifest(),result.scan(),artifact.createdAt()));
            var completed=new ValidationJob(job.id(),job.artifactId(),job.environment(),"COMPLETE",job.workerId(),job.createdAt(),job.expiresAt(),job.policyRevision(),null);
            tx.put("job",job.id(),completed); tx.audit(job.workerId(),"validation-complete",job.id(),state,artifact.sha256()); return completed;
        });
    }
    public void fail(String token,String jobId,String code) {
        Checks.id(code);
        database.transaction(tx -> { var job=requireWorker(tx,token,jobId);
            tx.put("job",job.id(),new ValidationJob(job.id(),job.artifactId(),job.environment(),"FAILED",job.workerId(),job.createdAt(),job.expiresAt(),job.policyRevision(),code));
            tx.audit(job.workerId(),"validation-failed",job.id(),code,Hashes.sha256(new byte[0])); return null; });
    }
    public VulnerabilityGate.Decision gate(ReleaseDatabase.Tx tx, Artifact artifact, ValidationResult validation) {
        return VulnerabilityGate.evaluate(validation.scan(),artifact.pluginId(),artifact.sha256(),artifact.environment(),policy(tx).revision(),validation.inventory(),
                tx.list("exception",VulnerabilityGate.ExceptionPermit.class),tx.now());
    }
    public Map<String,String> dependencies(ReleaseDatabase.Tx tx) { return tx.list("dependency",DependencyInput.class).stream().collect(Collectors.toUnmodifiableMap(DependencyInput::coordinate,DependencyInput::sha256)); }
    public ResourcePolicy policy(ReleaseDatabase.Tx tx) { return tx.get("policy","resources",ResourcePolicy.class).orElse(ResourcePolicy.defaults()); }
    private void checkDisk(ReleaseDatabase.Tx tx,ResourcePolicy policy,long incoming) {
        try {var disk=java.nio.file.Files.getFileStore(files.root());policy.checkSpace(disk.getTotalSpace(),disk.getUsableSpace(),StorageUsage.reserved(tx),incoming);}
        catch(IOException failure){throw new ProtocolException("storage-unavailable",failure);}
    }
    private ValidationJob requireWorker(ReleaseDatabase.Tx tx,String token,String jobId) {
        var job=tx.get("job",jobId,ValidationJob.class).orElseThrow(() -> new ProtocolException("job-not-found"));
        var worker=machines.authenticate(tx,token,MachineService.Kind.WORKER,job.environment());
        Checks.require(worker.id().equals(job.workerId()) && job.state().equals("RUNNING") && tx.now().isBefore(job.expiresAt()),"job-unavailable"); return job;
    }
}
