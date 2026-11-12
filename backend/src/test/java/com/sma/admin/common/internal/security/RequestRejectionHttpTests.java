package com.sma.admin.common.internal.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.net.URI;
import java.net.Socket;
import java.net.http.*;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;
import com.sma.admin.openapi.internal.service.ApplicationService;
import tools.jackson.databind.json.JsonMapper;

/** Production error/authorization chain; only the final collector sink is replaced to inspect capture timing. */
@IntegrationTest @AutoConfigureMockMvc @SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties={"server.address=127.0.0.1","sma.security.browser.maximum-json-bytes=4096",
        "sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "sma.security.requests.minimum-write-interval=100ms"})
class RequestRejectionHttpTests {
    @LocalServerPort int port;@Autowired MockMvc mvc;@Autowired SecuritySignalCapture capture;
    @MockitoBean SecuritySignals signals;
    @Autowired ApplicationService apps; @Autowired JsonMapper json;
    private final List<SecuritySignal> captured=new CopyOnWriteArrayList<>();
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    @BeforeEach void collect(){captured.clear();doAnswer(call->{captured.add(capture.capture(call.getArgument(0)));return SecuritySignals.Admission.QUEUED_VOLATILE;}).when(signals).observe(any());}
    private HttpResponse<String> request(String method,String path,String body,String...headers)throws Exception{
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(10));
        for(int i=0;i<headers.length;i+=2)request.header(headers[i],headers[i+1]);
        return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private SecuritySignal one(SecuritySignal.Fact fact){assertThat(captured).hasSize(1);var result=captured.getFirst();assertThat(result.fact()).isEqualTo(fact);captured.clear();return result;}
    @Test void realServletAuthenticationBrowserAndMalformedInputKeepOriginalResponses()throws Exception{
        var denied=request("GET","/api/iam/users?token=do-not-copy",null,"X-Tenant-Id","99","X-Forwarded-For","203.0.113.88");
        assertThat(denied.statusCode()).isEqualTo(401);assertThat(denied.body()).contains("common.error.unauthorized");
        var auth=one(SecuritySignal.Fact.AUTHENTICATION_REJECTED);assertThat(auth.attribution().scope()).isEqualTo(SecuritySignal.Scope.PLATFORM);
        assertThat(auth.metadata().http()).isEqualTo(new SecuritySignal.Http(SecuritySignal.Method.GET,SecuritySignal.Route.UNRESOLVED,null,401));
        assertThat(auth.source().peerAddress()).isEqualTo("127.0.0.1");assertThat(auth.source().clientAddress()).isEqualTo("127.0.0.1");
        var origin=request("POST","/api/auth/login","{}","Content-Type","application/json","Origin","https://evil.test");
        assertThat(origin.statusCode()).isEqualTo(403);one(SecuritySignal.Fact.BROWSER_ORIGIN_REJECTED);
        var media=request("POST","/api/auth/login","password=private","Content-Type","text/plain");
        assertThat(media.statusCode()).isEqualTo(415);one(SecuritySignal.Fact.REQUEST_INPUT_REJECTED);
        var malformed=request("POST","/api/auth/login","{private","Content-Type","application/json");
        assertThat(malformed.statusCode()).isEqualTo(400);var badJson=one(SecuritySignal.Fact.REQUEST_INPUT_REJECTED);
        assertThat(badJson.metadata().http()).isEqualTo(new SecuritySignal.Http(SecuritySignal.Method.POST,SecuritySignal.Route.MATCHED,"/api/auth/login",400));
        var tooLarge=request("POST","/api/auth/login","x".repeat(4097),"Content-Type","application/json");
        assertThat(tooLarge.statusCode()).isEqualTo(413);one(SecuritySignal.Fact.REQUEST_INPUT_REJECTED);
        var login=request("POST","/api/auth/login",json.writeValueAsString(Map.of("username","missing-security-user","password","private-password","portal","admin","remember",false)),"Content-Type","application/json");
        assertThat(login.statusCode()).as("login error response: %s",login.body()).isEqualTo(200);assertThat(login.body()).contains("auth.error.");
        var rejectedLogin=one(SecuritySignal.Fact.AUTHENTICATION_REJECTED);
        assertThat(rejectedLogin.metadata().http()).isEqualTo(new SecuritySignal.Http(SecuritySignal.Method.POST,SecuritySignal.Route.MATCHED,"/api/auth/login",200));
        assertThat(rejectedLogin.toString()).doesNotContain("missing-security-user","private-password");
        assertThat(request("GET","/api/tenant/entry",null).statusCode()).isEqualTo(200);assertThat(captured).isEmpty();
        assertThat(auth.toString()).doesNotContain("do-not-copy");
        assertThat(auth.attribution().tenantId()).isNull();
    }
    @Test void realUnknownHostIsPlatformEntryRejectionAndCollectorFailureCannotTurnItInto500()throws Exception{
        String response=unknownHost();assertThat(response).startsWith("HTTP/1.1 404");assertThat(response).contains("common.error.not-found");
        var event=one(SecuritySignal.Fact.TENANT_ENTRY_REJECTED);assertThat(event.attribution().tenantId()).isNull();
        doThrow(new IllegalStateException("collector private failure")).when(signals).observe(any());
        String failed=unknownHost();assertThat(failed).startsWith("HTTP/1.1 404");assertThat(failed).doesNotContain("collector private failure");
        var original=request("GET","/api/iam/users",null);assertThat(original.statusCode()).isEqualTo(401);assertThat(original.body()).contains("common.error.unauthorized");
    }
    private String unknownHost()throws Exception{
        try(var socket=new Socket("127.0.0.1",port)){
            socket.setSoTimeout(10000);socket.getOutputStream().write(("GET /api/tenant/entry HTTP/1.1\r\nHost: unknown-security.test\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    @Test void realSignedRequestReplayIsObservedWithoutInferringTenantFromUnacceptedCredentials()throws Exception{
        var app=Users.as(Users.principal(101,9941,DataAccess.unrestricted()),()->apps.create(new ApplicationService.Input("Signal replay",1,null,true,true)));
        String body=json.writeValueAsString(Map.of("appId",app.appId(),"appSecret",app.appSecret()));
        var issued=signed("POST","/open/token",body,app,null,UUID.randomUUID().toString());
        assertThat(issued.statusCode()).isEqualTo(200);
        String token=json.readTree(issued.body()).path("data").path("token").asString();assertThat(token).isNotBlank();
        String nonce=UUID.randomUUID().toString();
        assertThat(signed("GET","/open/me","",app,token,nonce).statusCode()).isEqualTo(200);assertThat(captured).isEmpty();
        var replay=signed("GET","/open/me","",app,token,nonce);
        assertThat(replay.statusCode()).isEqualTo(409);assertThat(replay.body()).contains("openapi.error.replay");
        var event=one(SecuritySignal.Fact.SIGNATURE_REPLAY_REJECTED);
        assertThat(event.attribution().scope()).isEqualTo(SecuritySignal.Scope.PLATFORM);assertThat(event.attribution().tenantId()).isNull();
        assertThat(event.toString()).doesNotContain(app.appSecret(),token,nonce);
    }
    private HttpResponse<String> signed(String method,String path,String body,ApplicationService.Credentials app,String token,String nonce)throws Exception{
        String stamp=Long.toString(Instant.now().getEpochSecond());
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(app.appSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String signature=HexFormat.of().formatHex(mac.doFinal((method+"\n"+path+"\n\n"+stamp+"\n"+nonce+"\n"+hash).getBytes(StandardCharsets.UTF_8)));
        var headers=new ArrayList<>(List.of("Content-Type","application/json","X-App-Id",app.appId(),"X-Timestamp",stamp,"X-Nonce",nonce,"X-Signature",signature));
        if(token!=null){headers.add("Authorization");headers.add("Bearer "+token);}
        return request(method,path,body,headers.toArray(String[]::new));
    }
    private SmaPrincipal actor(Set<String> permissions){return new SmaPrincipal(994001,"rejection-fixture",9940,"admin",null,false,DataAccess.unrestricted(),permissions,"UTC");}
    @Test void authenticatedMvcDenialCapturesPrincipalBeforeCleanupAndOrdinaryMissingDataDoesNotEmit()throws Exception{
        var user=actor(Set.of());
        var denied=mvc.perform(get("/api/iam/users").with(Users.request(user))).andReturn().getResponse();
        assertThat(denied.getStatus()).isEqualTo(403);var event=one(SecuritySignal.Fact.AUTHORIZATION_REJECTED);
        assertThat(event.attribution()).isEqualTo(new SecuritySignal.Attribution(SecuritySignal.Scope.TENANT,SecuritySignal.Identity.ADMIN_USER,9940L,"994001","admin",null,null,null));
        var missing=mvc.perform(get("/api/file/files/9223372036854775001").with(Users.request(user))).andReturn().getResponse();
        assertThat(missing.getStatus()).isEqualTo(200);assertThat(missing.getContentAsString()).contains("common.error.data-not-found");assertThat(captured).isEmpty();
        var unmapped=mvc.perform(get("/api/no-such-security-route").with(Users.request(user))).andReturn().getResponse();
        assertThat(unmapped.getStatus()).isEqualTo(404);var unknown=one(SecuritySignal.Fact.UNMAPPED_REQUEST_OBSERVED);
        assertThat(unknown.metadata().http()).isEqualTo(new SecuritySignal.Http(SecuritySignal.Method.GET,SecuritySignal.Route.UNMAPPED,null,404));
        assertThat(unknown.toString()).doesNotContain("no-such-security-route");
    }
    @Test void collectionRejectionDoesNotRetryAndIdempotentCompletionIsNotAnAttack()throws Exception{
        var user=actor(Set.of("iam:post:create","iam:post:view"));String key=UUID.randomUUID().toString();
        String input="{\"name\":\"Signal protocol\",\"code\":\"SIG_"+UUID.randomUUID().toString().replace("-","")+"\",\"enabled\":true,\"sort\":0}";
        var first=mvc.perform(post("/api/iam/posts").contentType("application/json").content(input).header("Idempotency-Key",key).with(Users.request(user))).andReturn().getResponse();
        assertThat(first.getStatus()).isEqualTo(200);assertThat(first.getContentAsString()).contains("\"code\":0");assertThat(captured).isEmpty();
        Thread.sleep(150);
        var repeated=mvc.perform(post("/api/iam/posts").contentType("application/json").content(input).header("Idempotency-Key",key).with(Users.request(user))).andReturn().getResponse();
        assertThat(repeated.getStatus()).isEqualTo(409);assertThat(repeated.getContentAsString()).contains("common.request.completed");assertThat(captured).isEmpty();
        doReturn(SecuritySignals.Admission.REJECTED).when(signals).observe(any());clearInvocations(signals);
        assertThat(request("GET","/api/iam/users",null).statusCode()).isEqualTo(401);verify(signals,times(1)).observe(SecuritySignal.Fact.AUTHENTICATION_REJECTED);
    }
}
