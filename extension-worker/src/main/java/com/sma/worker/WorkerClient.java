package com.sma.worker;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import com.sma.extension.protocol.*;

public final class WorkerClient {
    public record Job(String id,String artifactId,String environment,String state,String workerId,Instant createdAt,Instant expiresAt,long policyRevision,String failure) {}
    public record Assignment(Job job,ResourcePolicy policy,Map<String,String> dependencies,String artifactSha256,long bytes,String name,SourceBuildPlan.Assignment build) {}
    public record Result(String artifactId,String sha256,ArtifactManifest manifest,ScanReport scan,Set<String> inventory,String validationKind,Instant validatedAt) {}
    private record Reply(int code,String error,String message,Object data) {}
    private final WorkerSettings settings;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public WorkerClient(WorkerSettings settings) {this.settings=settings;}
    public Assignment claim() throws IOException,InterruptedException {return post("/workers/claim?environment="+settings.environment(),Map.of(),Assignment.class);}
    public byte[] download(Assignment job) throws IOException,InterruptedException {
        Checks.id(job.job().id());Checks.require(job.bytes()>0 && job.bytes()<=job.policy().jarBytes(),"invalid-job-size");
        var response=http.send(request("/workers/jobs/"+job.job().id()+"/artifact").GET().build(),HttpResponse.BodyHandlers.ofInputStream());
        try(var input=response.body()) {
            Checks.require(response.statusCode()==200,"worker-download-rejected");byte[] bytes=input.readNBytes(Math.toIntExact(job.bytes()+1));
            Checks.require(bytes.length==job.bytes() && Hashes.sha256(bytes).equals(job.artifactSha256()),"worker-download-hash-mismatch");return bytes;
        }
    }
    public void complete(String id,Result result) throws IOException,InterruptedException {Checks.id(id);post("/workers/jobs/"+id+"/complete",result,Object.class);}
    public byte[] source(Assignment job,SourceBuildPlan.Input file)throws IOException,InterruptedException {
        Checks.id(job.job().id());Checks.id(file.artifactId());Checks.require(file.bytes()<=job.policy().jarBytes(),"invalid-job-size");
        var response=http.send(request("/workers/jobs/"+job.job().id()+"/inputs/"+file.artifactId()).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
        try(var input=response.body()) {
            Checks.require(response.statusCode()==200,"worker-download-rejected");byte[] bytes=input.readNBytes(Math.toIntExact(file.bytes()+1));
            Checks.require(bytes.length==file.bytes() && Hashes.sha256(bytes).equals(file.sha256()),"build-input-hash-mismatch");return bytes;
        }
    }
    public record OutputTicket(String jobId,String artifactId,String sha256,long bytes,String state) {}
    public String uploadOutput(String jobId,SourceCompiler.Result result)throws IOException,InterruptedException {
        Checks.id(jobId);byte[] bytes=result.jar();
        var ticket=post("/workers/jobs/"+jobId+"/output",Map.of("sha256",result.sha256(),"bytes",bytes.length,"toolchainDigest",result.toolchainDigest()),OutputTicket.class);
        int chunkSize=1_048_576;
        for(int offset=0,index=0;offset<bytes.length;offset+=chunkSize,index++) {
            byte[] chunk=java.util.Arrays.copyOfRange(bytes,offset,Math.min(bytes.length,offset+chunkSize));
            var response=http.send(request("/workers/jobs/"+jobId+"/output/chunks/"+index).header("Content-Type","application/octet-stream")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(chunk)).build(),HttpResponse.BodyHandlers.ofInputStream());
            try(var input=response.body()) {Checks.require(response.statusCode()==200,"build-output-upload-rejected");var reply=ProtocolJson.read(input.readNBytes(ProtocolJson.MAX_DOCUMENT_BYTES+1),Reply.class);Checks.require(reply.code()==0,"build-output-upload-rejected");}
        }
        post("/workers/jobs/"+jobId+"/output/complete",Map.of(),Object.class);return ticket.artifactId();
    }
    public void fail(String id,String code) throws IOException,InterruptedException {Checks.id(id);Checks.id(code);post("/workers/jobs/"+id+"/fail",Map.of("code",code),Object.class);}
    private <T>T post(String path,Object body,Class<T> type) throws IOException,InterruptedException {
        var response=http.send(request(path).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(ProtocolJson.write(body))).build(),HttpResponse.BodyHandlers.ofInputStream());
        try(var input=response.body()) {
            Checks.require(response.statusCode()==200,"worker-request-rejected");
            var reply=ProtocolJson.read(input.readNBytes(ProtocolJson.MAX_DOCUMENT_BYTES+1),Reply.class);
            Checks.require(reply.code()==0,"worker-request-rejected");return reply.data()==null?null:ProtocolJson.read(ProtocolJson.write(reply.data()),type);
        }
    }
    private HttpRequest.Builder request(String path) {return HttpRequest.newBuilder(settings.securityUrl().resolve("/release/v1/machines"+path))
            .timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+settings.token());}
}
