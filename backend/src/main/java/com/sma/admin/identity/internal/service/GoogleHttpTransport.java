package com.sma.admin.identity.internal.service;

import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import jakarta.annotation.PreDestroy;
import com.sma.admin.common.BizException;
import com.sma.admin.common.deployment.*;
import com.sma.admin.identity.IdentityErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/** Fixed Google endpoints, no redirects/cookies, bounded requests, response bytes and total duration. */
@Component
public class GoogleHttpTransport implements DeploymentActivity,DeploymentTransport,AutoCloseable {
    private static final URI JWKS=URI.create("https://www.googleapis.com/oauth2/v3/certs");
    private static final URI TOKEN=URI.create("https://oauth2.googleapis.com/token");
    private static final Pattern MAX_AGE=Pattern.compile("(?:^|,)\\s*max-age=(?:\")?(\\d+)(?:\")?(?:\\s*,|$)");
    private final HttpClient client;private final URI jwks;private final URI token;private final Duration deadline;
    private final JsonMapper json;private final ExecutorService workers;
    private final DeploymentTaskTracker activity=new DeploymentTaskTracker(4);
    public record JwksDocument(String body,Duration lifetime) {}
    @Autowired public GoogleHttpTransport(JsonMapper json){
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build(),JWKS,TOKEN,Duration.ofSeconds(12),json);
    }
    // Package-private seam for local protocol fixtures. There is deliberately no configurable production endpoint.
    GoogleHttpTransport(HttpClient client,URI jwks,URI token,Duration deadline,JsonMapper json){
        this.client=client;this.jwks=jwks;this.token=token;this.deadline=deadline;this.json=json;
        if(client.followRedirects()!=HttpClient.Redirect.NEVER || client.cookieHandler().isPresent())throw new IllegalArgumentException("Unsafe Google HTTP client");
        workers=new TransportExecutor(client);
    }
    @Override public String id(){return "identity.google-http";}
    @Override public DeploymentActivity.Snapshot snapshot(){return activity.snapshot();}
    @Override public void shutdownTransport(){DeploymentEvidence.requireHost();activity.stopAccepting();workers.shutdown();}
    @Override public boolean transportTerminated(){return workers.isTerminated()&&activity.snapshot().quiescent()&&client.isTerminated();}
    private static final class TransportExecutor extends ThreadPoolExecutor {
        private final HttpClient client;
        TransportExecutor(HttpClient client){
            super(4,4,0,TimeUnit.SECONDS,new SynchronousQueue<>(),Thread.ofPlatform().daemon().inheritInheritableThreadLocals(false)
                    .name("sma-google-http-",0).factory(),new ThreadPoolExecutor.AbortPolicy());this.client=client;
        }
        // Do not close the client while an already-accepted call still needs it.
        @Override protected void terminated(){try{client.shutdown();}finally{super.terminated();}}
    }
    public JwksDocument fetchKeys(){
        var response=call(HttpRequest.newBuilder(jwks).timeout(deadline).header("Accept","application/json").GET().build());
        if(response.statusCode()!=200)throw unavailable();requireJson(response);
        return new JwksDocument(new String(response.body(),StandardCharsets.UTF_8),lifetime(response.headers()));
    }
    public String exchange(String clientId,char[] secret,String code,String verifier,String callback){
        var fields=new LinkedHashMap<String,String>();fields.put("grant_type","authorization_code");fields.put("client_id",clientId);
        fields.put("code",code);fields.put("code_verifier",verifier);fields.put("redirect_uri",callback);
        if(secret!=null)fields.put("client_secret",new String(secret));
        String body=fields.entrySet().stream().map(e->encode(e.getKey())+"="+encode(e.getValue())).collect(java.util.stream.Collectors.joining("&"));
        var response=call(HttpRequest.newBuilder(token).timeout(deadline).header("Accept","application/json")
                .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build());
        if(response.statusCode()==400 || response.statusCode()==401)throw new BizException(IdentityErrorCode.INVALID_GOOGLE_PROOF);
        if(response.statusCode()!=200)throw unavailable();requireJson(response);
        try{
            var document=json.readTree(response.body());var value=document.path("id_token");
            if(!value.isString() || value.asString().length()>16384 || value.asString().isBlank())throw unavailable();
            // Google access/refresh tokens are intentionally not retained by login.
            return value.asString();
        }catch(BizException failure){throw failure;}catch(RuntimeException malformed){throw unavailable();}
    }
    private HttpResponse<byte[]> call(HttpRequest request){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Google network I/O must not hold a database transaction");
        Future<HttpResponse<byte[]>> pending;
        try{pending=activity.submit(workers,()->client.send(request,info->new LimitedBody(65536)));}
        catch(RejectedExecutionException busy){throw unavailable();}
        try{return pending.get(deadline.toMillis(),TimeUnit.MILLISECONDS);}
        catch(InterruptedException interrupted){pending.cancel(true);Thread.currentThread().interrupt();throw unavailable();}
        catch(ExecutionException|TimeoutException failure){pending.cancel(true);throw unavailable();}
        catch(CancellationException cancelled){throw unavailable();}
    }
    private void requireJson(HttpResponse<?> response){
        String type=response.headers().firstValue("Content-Type").orElse("").split(";",2)[0].trim();
        if(!type.equalsIgnoreCase("application/json"))throw unavailable();
    }
    static Duration lifetime(HttpHeaders headers){
        String control=String.join(",",headers.allValues("Cache-Control")).toLowerCase(Locale.ROOT);
        if(control.contains("no-store") || control.contains("no-cache"))return Duration.ZERO;
        long seconds=300;var match=MAX_AGE.matcher(control);
        try{
            if(match.find())seconds=Long.parseLong(match.group(1));
            long age=Long.parseLong(headers.firstValue("Age").orElse("0"));
            return Duration.ofSeconds(Math.max(0,Math.min(21600,seconds)-Math.max(0,age)));
        }catch(NumberFormatException malformed){return Duration.ZERO;}
    }
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private BizException unavailable(){return new BizException(IdentityErrorCode.PROVIDER_UNAVAILABLE);}
    @Override @PreDestroy public void close(){activity.close();workers.shutdownNow();client.shutdownNow();}

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int maximum;private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();private Flow.Subscription subscription;
        LimitedBody(int maximum){this.maximum=maximum;}
        @Override public CompletionStage<byte[]> getBody(){return result;}
        @Override public void onSubscribe(Flow.Subscription subscription){this.subscription=subscription;subscription.request(1);}
        @Override public void onNext(List<ByteBuffer> buffers){
            for(var buffer:buffers){
                if(buffer.remaining()>maximum-bytes.size()){subscription.cancel();result.completeExceptionally(new IllegalStateException("Google response exceeds budget"));return;}
                byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable failure){result.completeExceptionally(failure);}
        @Override public void onComplete(){result.complete(bytes.toByteArray());}
    }
}
