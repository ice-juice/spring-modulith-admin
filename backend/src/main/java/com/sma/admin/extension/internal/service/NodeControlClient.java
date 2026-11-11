package com.sma.admin.extension.internal.service;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.sma.extension.protocol.*;

/** HTTPS, no redirects, bounded responses. Workload token never reaches public plugin API or logs. */
@Service
public class NodeControlClient {
    public record ArtifactDelivery(String artifactId,String sha256,long bytes,SignedEnvelope proof,SignedEnvelope preparation) {}
    public record NodePackage(String state,String preparationId,SignedEnvelope preparation,SignedEnvelope activation,SignedEnvelope lease,List<ArtifactDelivery> artifacts) {}
    public record Ready(String deploymentId,String preparationId,long revision,String releaseSha256,Set<String> artifactHashes) {}
    private record Reply(int code,String error,String message,Object data) {}
    private final ExtensionProperties properties;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public NodeControlClient(ExtensionProperties properties) { this.properties=properties; }
    public NodePackage desired(String deployment,ExecutionDocuments.Retention retained) throws IOException,InterruptedException {
        Checks.id(deployment);
        return post("/release/v1/machines/nodes/"+deployment+"/desired?environment="+properties.environment(),retained,NodePackage.class);
    }
    public NodePackage serving(String deployment,ExecutionDocuments.Retention retained)throws IOException,InterruptedException {
        Checks.id(deployment);return post("/release/v1/machines/nodes/"+deployment+"/desired?environment="+properties.environment()+"&activeOnly=true",retained,NodePackage.class);
    }
    public void ready(Ready ready) throws IOException,InterruptedException { post("/release/v1/machines/nodes/ready?environment="+properties.environment(),ready,Object.class); }
    public SignedEnvelope revocations() throws IOException,InterruptedException {
        return post("/release/v1/machines/nodes/revocations?environment="+properties.environment(),java.util.Map.of(),SignedEnvelope.class);
    }
    public SignedEnvelope policy()throws IOException,InterruptedException {
        return post("/release/v1/machines/nodes/policy?environment="+properties.environment(),java.util.Map.of(),SignedEnvelope.class);
    }
    public void acknowledgeKeys(java.util.Map<String,String> fingerprints)throws IOException,InterruptedException {
        post("/release/v1/machines/nodes/trust-ack?environment="+properties.environment(),fingerprints,Object.class);
    }
    public void observe(CanaryObservation observation)throws IOException,InterruptedException {
        post("/release/v1/machines/nodes/observations?environment="+properties.environment(),observation,Object.class);
    }
    private <T> T post(String path,Object body,Class<T> type) throws IOException,InterruptedException {
        var request=base(path).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(ProtocolJson.write(body))).build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
        try(var input=response.body()) {
            Checks.require(response.statusCode()==200,"control-service-rejected");
            byte[] bytes=input.readNBytes(ProtocolJson.MAX_DOCUMENT_BYTES+1);
            var reply=ProtocolJson.read(bytes,Reply.class);Checks.require(reply.code()==0,"control-service-rejected");
            return ProtocolJson.read(ProtocolJson.write(reply.data()),type);
        }
    }
    public byte[] download(String deployment,ArtifactDelivery artifact,ResourcePolicy policy) throws IOException,InterruptedException {
        Checks.id(deployment);Checks.id(artifact.artifactId());Checks.hash(artifact.sha256());
        Checks.require(artifact.bytes()>0 && artifact.bytes()<=policy.jarBytes(),"file-quota-exceeded");
        // A content hash is always rechecked, even for a cache hit. The runtime additionally snapshots all bytes.
        Path root=properties.cacheDirectory().toAbsolutePath().normalize();Files.createDirectories(root);
        Checks.require(!Files.isSymbolicLink(root) && root.toRealPath().equals(root),"unsafe-cache-directory");
        Path cached=root.resolve(artifact.sha256()+".jar");
        if(Files.isRegularFile(cached,LinkOption.NOFOLLOW_LINKS)) {
            Checks.require(Files.size(cached)==artifact.bytes(),"cached-artifact-size-mismatch");
            byte[] bytes=Files.readAllBytes(cached);Checks.require(Hashes.sha256(bytes).equals(artifact.sha256()),"cached-artifact-hash-mismatch");return bytes;
        }
        Checks.require(!Files.exists(cached,LinkOption.NOFOLLOW_LINKS),"unsafe-cache-path");
        var disk=Files.getFileStore(root);policy.checkSpace(disk.getTotalSpace(),disk.getUsableSpace(),0,artifact.bytes());
        var request=base("/release/v1/machines/nodes/"+deployment+"/artifacts/"+artifact.artifactId()+"?environment="+properties.environment()).GET().build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
        byte[] bytes;
        try(var input=response.body()) { Checks.require(response.statusCode()==200,"artifact-download-rejected");bytes=input.readNBytes(Math.toIntExact(artifact.bytes()+1)); }
        Checks.require(bytes.length==artifact.bytes() && Hashes.sha256(bytes).equals(artifact.sha256()),"download-hash-mismatch");
        Path temp=Files.createTempFile(root,"download-",".partial");
        try { Files.write(temp,bytes);Files.move(temp,cached); } finally { Files.deleteIfExists(temp); }
        return bytes;
    }
    private HttpRequest.Builder base(String path)throws IOException {
        Checks.require(properties.enabled(),"extension-disabled");
        return HttpRequest.newBuilder(properties.securityUrl().resolve(path)).timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+nodeToken());
    }
    String nodeToken()throws IOException {
        if(properties.nodeTokenFile()==null)return properties.nodeToken();
        Path file=properties.nodeTokenFile().toAbsolutePath().normalize();
        Checks.require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) && file.toRealPath().equals(file),"invalid-node-token-file");
        byte[] bytes;try(var input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){bytes=input.readNBytes(257);}
        Checks.require(bytes.length<=256,"invalid-node-token-file");
        String token=new String(bytes,java.nio.charset.StandardCharsets.US_ASCII).strip();java.util.Arrays.fill(bytes,(byte)0);
        Checks.require(token.matches("[A-Za-z0-9_-]{32,128}"),"invalid-node-token-file");return token;
    }
}
