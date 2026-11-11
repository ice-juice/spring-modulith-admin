package com.sma.admin.extension.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;
import com.sma.extension.protocol.ProtocolJson;

class ConsoleProxyTests {
    @TempDir Path temp;
    @Test void onlyExplicitConsoleRoutesCanBeForwarded() {
        for(String path:List.of("https://evil.test/commands","//evil.test/commands","/machines/consoles/sessions","/auth/login","/auth/recovery","/groups/../machines","/groups/%2e%2e/machines","/commands?target=x","/groups#x"))
            assertThat(ConsoleProxyService.allowed(new ConsoleProxyService.Request("POST",path,null))).as(path).isFalse();
        assertThat(ConsoleProxyService.allowed(new ConsoleProxyService.Request("GET","/groups/example.v1/plugins",null))).isTrue();
        assertThat(ConsoleProxyService.allowed(new ConsoleProxyService.Request("GET","/console/artifacts?plugin=example&environment=production&offset=0&limit=20",null))).isTrue();
        assertThat(ConsoleProxyService.allowed(new ConsoleProxyService.Request("PUT","/uploads/upload-one/files/0/chunks/1",new byte[1048577]))).isFalse();
        assertThat(ConsoleProxyService.allowed(new ConsoleProxyService.Request("GET","/groups",new byte[]{1}))).isFalse();
        assertThatThrownBy(()->new ConsoleProxyProperties(true,URI.create("http://release.test"),temp.resolve("token"))).hasMessage("console-https-configuration-required");
    }
    @Test void unauthenticatedMachinesAndUnprivilegedHumansCannotReachTransport() {
        var http=mock(HttpClient.class);var proxy=new ConsoleProxyService(new ConsoleProxyProperties(true,URI.create("https://release.test"),temp.resolve("token")),http);
        var request=new ConsoleProxyService.Request("GET","/groups",null);
        Users.as(SmaPrincipal.forApplication(1,"admin","example"),()->{assertThatThrownBy(()->proxy.forward(request,"Bearer test","zh-CN")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);return null;});
        Users.as(Users.principal(101,2,DataAccess.unrestricted()),()->{assertThatThrownBy(()->proxy.forward(request,"Bearer test","zh-CN")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);return null;});
        verifyNoInteractions(http);
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    @Test void derivesIdentityFromContextKeepsCredentialsSeparateAndDoesNotRetryCommands()throws Exception {
        Path token=temp.resolve("console.token");Files.writeString(token,"console-machine-token-01234567890123456789");
        if(Files.getFileStore(token).supportsFileAttributeView("posix"))Files.setPosixFilePermissions(token,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        else {var acl=Files.getFileAttributeView(token,java.nio.file.attribute.AclFileAttributeView.class);acl.setAcl(List.of(java.nio.file.attribute.AclEntry.newBuilder().setType(java.nio.file.attribute.AclEntryType.ALLOW).setPrincipal(acl.getOwner()).setPermissions(java.nio.file.attribute.AclEntryPermission.values()).build()));}
        var http=mock(HttpClient.class);List<HttpRequest> requests=new ArrayList<>();
        when(http.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call->{
            HttpRequest request=call.getArgument(0);requests.add(request);
            if(request.uri().getPath().endsWith("/sessions")){
                var response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(200);
                when(response.body()).thenReturn(ProtocolJson.write(Map.of("code",0,"message","ok","data",Map.of("token","release-session-token-01234567890123456789","expiresAt","2026-01-01T00:00:00Z"))));return CompletableFuture.completedFuture(response);
            }
            return CompletableFuture.failedFuture(new java.io.IOException("uncertain response"));
        });
        var proxy=new ConsoleProxyService(new ConsoleProxyProperties(true,URI.create("https://release.test"),token),http);
        var actor=new SmaPrincipal(9007199254740993L,"operator",27,"admin",null,false,DataAccess.own(),Set.of("extension:console:view"),"UTC");
        Users.as(actor,()->{assertThatThrownBy(()->proxy.forward(new ConsoleProxyService.Request("POST","/commands","{}".getBytes()),"Bearer host-session-secret","zh-CN")).isInstanceOf(BizException.class);return null;});
        assertThat(requests).hasSize(2);
        assertThat(requests.getFirst().headers().firstValue("Authorization")).contains("Bearer console-machine-token-01234567890123456789");
        assertThat(requests.getLast().headers().firstValue("Authorization")).contains("Bearer release-session-token-01234567890123456789");
        var subscriber=new ConsoleProxyService.LimitedBody(4096);requests.getFirst().bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription s){subscriber.onSubscribe(s);}public void onNext(ByteBuffer b){subscriber.onNext(List.of(b));}
            public void onError(Throwable t){subscriber.onError(t);}public void onComplete(){subscriber.onComplete();}
        });
        String exchange=new String(subscriber.getBody().toCompletableFuture().get());
        assertThat(exchange).contains("\"tenantId\":\"27\"","\"hostUserId\":\"9007199254740993\"").doesNotContain("host-session-secret");
    }
    @Test void oversizedResponsesCancelTheNetworkSubscription() {
        var subscription=mock(Flow.Subscription.class);var body=new ConsoleProxyService.LimitedBody(4);body.onSubscribe(subscription);
        body.onNext(List.of(ByteBuffer.wrap(new byte[5])));verify(subscription).cancel();assertThat(body.getBody().toCompletableFuture()).isCompletedExceptionally();
    }
    @Test void binaryUploadChunksAreNotWrappedInJsonOrBase64()throws Exception {
        byte[] chunk=new byte[1048576];new Random(1).nextBytes(chunk);
        var service=mock(ConsoleProxyService.class);
        when(service.forward(any(),any(),any())).thenReturn(new ConsoleProxyService.Reply(200,"{}".getBytes()));
        var request=new org.springframework.mock.web.MockHttpServletRequest();request.setContent(chunk);
        request.addHeader("X-Release-Method","PUT");request.addHeader("X-Release-Path","/uploads/upload-one/files/0/chunks/0");
        request.addHeader("Authorization","Bearer host-fixture");request.addHeader("Accept-Language","zh-CN");
        new com.sma.admin.extension.internal.web.ExtensionConsoleController(service).request(request);
        var captured=org.mockito.ArgumentCaptor.forClass(ConsoleProxyService.Request.class);
        verify(service).forward(captured.capture(),eq("Bearer host-fixture"),eq("zh-CN"));
        assertThat(captured.getValue().body()).isEqualTo(chunk);assertThat(ConsoleProxyService.allowed(captured.getValue())).isTrue();
    }
}
