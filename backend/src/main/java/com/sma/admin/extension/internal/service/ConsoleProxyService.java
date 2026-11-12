package com.sma.admin.extension.internal.service;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.sma.admin.common.BizException;
import com.sma.admin.common.deployment.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.extension.ExtensionErrorCode;
import com.sma.extension.protocol.*;

/** No browser-selected destination, credential forwarding, redirects or automatic command retries. */
@Service
public class ConsoleProxyService implements DeploymentActivity,DeploymentTransport,AutoCloseable {
    public static final int MAX_BYTES=2*1024*1024;
    private final ConsoleProxyProperties properties;private final HttpClient http;
    private final Duration responseDeadline;private final int maximumForwards;
    private int active;private boolean stopping,forceClosed,clientShutdown;
    @Autowired public ConsoleProxyService(ConsoleProxyProperties properties) {
        this(properties,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    ConsoleProxyService(ConsoleProxyProperties properties,HttpClient http){this(properties,http,Duration.ofSeconds(25),1024);}
    /** Bounded local protocol fixtures; production retains the 25-second wait and fixed capacity. */
    ConsoleProxyService(ConsoleProxyProperties properties,HttpClient http,Duration responseDeadline,int maximumForwards){
        if(responseDeadline==null||responseDeadline.isNegative()||responseDeadline.isZero()||responseDeadline.compareTo(Duration.ofSeconds(25))>0
                ||maximumForwards<1||maximumForwards>1024)throw new IllegalArgumentException("Invalid console transport bounds");
        this.properties=properties;this.http=http;this.responseDeadline=responseDeadline;this.maximumForwards=maximumForwards;
    }
    @Override public String id(){return "extension.console-http";}
    @Override public synchronized DeploymentActivity.Snapshot snapshot(){return new DeploymentActivity.Snapshot(0,active);}
    @Override public void shutdownTransport(){
        DeploymentEvidence.requireHost();synchronized(this){stopping=true;}shutdownIfIdle();
    }
    @Override public boolean transportTerminated(){
        synchronized(this){if(!stopping||active!=0||!clientShutdown)return false;}
        return http.isTerminated();
    }
    private synchronized Forward beginForward(){
        if(stopping||active>=maximumForwards)throw new BizException(ExtensionErrorCode.CONSOLE_UNAVAILABLE);
        active++;return new Forward();
    }
    private final class Forward implements AutoCloseable {
        private boolean released;
        @Override public void close(){
            synchronized(ConsoleProxyService.this){if(released)return;released=true;active--;}
            shutdownIfIdle();
        }
    }
    private void shutdownIfIdle(){
        synchronized(this){if(!stopping||active!=0||clientShutdown)return;clientShutdown=true;}
        http.shutdown();
    }
    @Override @PreDestroy public void close(){
        synchronized(this){stopping=true;forceClosed=true;clientShutdown=true;}
        http.shutdownNow(); // Caller/Future completion and real client termination remain separately observed.
    }
    public record Request(String method,String path,byte[] body) {
        @Override public String toString(){return "ConsoleRequest[redacted]";}
    }
    public record Reply(int status,byte[] bytes) {}
    private record Envelope(int code,String error,String message,Object data) {}
    private record Ticket(String token,String expiresAt) {}

    public Reply forward(Request input,String hostAuthorization,String language) {
        var actor=CurrentUser.require();
        if(!actor.permissions().contains("extension:console:view"))throw new org.springframework.security.access.AccessDeniedException("Console permission required");
        if(!properties.enabled())throw new BizException(ExtensionErrorCode.CONSOLE_UNCONFIGURED);
        if(!allowed(input))throw new BizException(ExtensionErrorCode.CONSOLE_PATH_DENIED);
        if(hostAuthorization==null || !hostAuthorization.startsWith("Bearer ") || hostAuthorization.length()>512)throw new BizException(ExtensionErrorCode.CONSOLE_PATH_DENIED);
        try(var forward=beginForward()) {
            // Identity is always taken from the authenticated host context, never the request payload.
            byte[] exchange=ProtocolJson.write(Map.of("subject",Map.of("tenantId",Long.toString(actor.tenantId()),"portal",actor.portal(),"hostUserId",Long.toString(actor.userId())),
                    "hostSessionHash",Hashes.sha256(hostAuthorization.getBytes(StandardCharsets.UTF_8))));
            Reply identity=send("POST","/machines/consoles/sessions",exchange,readToken(),language);
            if(identity.status()!=200)return identity;
            var envelope=ProtocolJson.read(identity.bytes(),Envelope.class);
            if(envelope.code()!=0)return identity;
            var ticket=ProtocolJson.read(ProtocolJson.write(envelope.data()),Ticket.class);
            Checks.require(ticket.token()!=null && ticket.token().matches("[A-Za-z0-9_-]{32,128}"),"invalid-console-ticket");
            return send(input.method(),input.path(),input.body(),ticket.token(),language);
        } catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new BizException(ExtensionErrorCode.CONSOLE_UNAVAILABLE);}
        catch(IOException|ProtocolException failure){throw new BizException(ExtensionErrorCode.CONSOLE_UNAVAILABLE);}
    }
    public static boolean allowed(Request request) {
        if(request==null || request.body()!=null && request.body().length>MAX_BYTES || !allowedRoute(request.method(),request.path()))return false;
        return switch(request.method()) {
            case "GET" -> request.body()==null || request.body().length==0;
            case "PUT" -> request.body()!=null && request.body().length>0 && request.body().length<=1048576;
            case "POST" -> true;
            default -> false;
        };
    }
    /** The same route allowlist is used for admission classification and actual forwarding. No body is consumed here. */
    public static boolean allowedRoute(String method,String target) {
        if(target==null || target.length()>2048)return false;
        // Reject escapes, traversal, fragments, authority syntax and arbitrary query encodings.
        if(!target.matches("/[A-Za-z0-9/._-]+(?:\\?[A-Za-z0-9_=&.,-]+)?"))return false;
        String path=target.split("\\?",2)[0],id="[A-Za-z0-9][A-Za-z0-9_.-]{0,159}";
        if("GET".equals(method))return (
                path.matches("/(policy|groups|accounts|accounts/me/notices|security/audit|security/public-keys|security/machines|console/(access|identity|bindings|artifacts|jobs|storage|grants))")
                || path.matches("/groups/"+id+"/plugins") || path.matches("/plugins/"+id+"/artifacts")
                || path.matches("/(actions|console/actions)/"+id) || path.matches("/uploads/"+id)
                || path.matches("/deployments(?:/"+id+"(?:/(history|guard))?)?"));
        if("POST".equals(method))return !target.contains("?") && (Set.of("/challenges","/authorizations","/commands","/auth/logout","/console/enrollment/begin","/console/enrollment/complete","/console/recovery").contains(path)
                || path.matches("/uploads/"+id+"/complete") || path.matches("/accounts/"+id+"/enrollment-ticket") || path.matches("/security/machines/"+id+"/token"));
        return "PUT".equals(method) && !target.contains("?") && path.matches("/uploads/"+id+"/files/[0-9]{1,2}/chunks/[0-9]{1,6}");
    }
    private Reply send(String method,String path,byte[] body,String token,String language)throws IOException,InterruptedException {
        synchronized(this){if(forceClosed)throw new IOException("Console transport unavailable");}
        URI target=properties.securityUrl().resolve("/release/v1"+path);
        var request=HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(20)).header("Authorization","Bearer "+token)
                .header("Accept","application/json").header("Accept-Language","en-US".equals(language)?"en-US":"zh-CN")
                .header("Content-Type","PUT".equals(method)?"application/octet-stream":"application/json")
                .method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body)).build();
        // Async transport has an absolute wall-clock deadline covering the response body as well.
        CompletableFuture<HttpResponse<byte[]>> future;
        try{future=http.sendAsync(request,info->new LimitedBody(MAX_BYTES));}
        catch(IllegalStateException|RejectedExecutionException stopped){throw new IOException("Console transport unavailable");}
        try {
            var response=future.get(responseDeadline.toMillis(),TimeUnit.MILLISECONDS);
            if(response.statusCode()>=300 && response.statusCode()<400)throw new IOException("Redirect refused");
            ProtocolJson.validateValue(response.body());return new Reply(response.statusCode(),response.body());
        } catch(TimeoutException|ExecutionException failure){future.cancel(true);throw new IOException("Console transport unavailable");}
        catch(InterruptedException interrupted){future.cancel(true);throw interrupted;}
        catch(CancellationException cancelled){throw new IOException("Console transport unavailable");}
    }
    private String readToken()throws IOException {
        Path file=properties.tokenFile().toAbsolutePath().normalize();
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file) || !file.toRealPath().equals(file))throw new IOException("Invalid console credential file");
        if(Files.getFileStore(file).supportsFileAttributeView("posix")) {
            if(Files.getPosixFilePermissions(file,LinkOption.NOFOLLOW_LINKS).stream().anyMatch(p->p.name().startsWith("GROUP_") || p.name().startsWith("OTHERS_")))throw new IOException("Console credential permissions too wide");
        } else {
            var acl=Files.getFileAttributeView(file,java.nio.file.attribute.AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
            if(acl==null)throw new IOException("Console credential ACL unavailable");
            for(var entry:acl.getAcl())if(entry.type()==java.nio.file.attribute.AclEntryType.ALLOW && entry.permissions().contains(java.nio.file.attribute.AclEntryPermission.READ_DATA)) {
                String name=entry.principal().getName().toLowerCase(Locale.ROOT);
                if(!entry.principal().equals(acl.getOwner()) && !name.endsWith("\\system") && !name.endsWith("\\administrators"))throw new IOException("Console credential permissions too wide");
            }
        }
        byte[] bytes;try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){bytes=in.readNBytes(257);}
        try {
            String token=new String(bytes,StandardCharsets.US_ASCII).strip();
            if(bytes.length>256 || !token.matches("[A-Za-z0-9_-]{32,128}"))throw new IOException("Invalid console credential");return token;
        }finally{Arrays.fill(bytes,(byte)0);}
    }
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;private final ByteArrayOutputStream output=new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();private Flow.Subscription subscription;
        LimitedBody(int limit){this.limit=limit;}
        @Override public CompletionStage<byte[]> getBody(){return result;}
        @Override public void onSubscribe(Flow.Subscription subscription){this.subscription=subscription;subscription.request(1);}
        @Override public void onNext(List<ByteBuffer> buffers){
            for(var buffer:buffers){if(buffer.remaining()>limit-output.size()){subscription.cancel();result.completeExceptionally(new IOException("Response too large"));return;}
                byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);output.writeBytes(bytes);}
            subscription.request(1);
        }
        @Override public void onError(Throwable error){result.completeExceptionally(error);}
        @Override public void onComplete(){result.complete(output.toByteArray());}
    }
}
