package com.sma.admin.extension.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.*;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import com.sma.admin.common.BizException;
import com.sma.admin.common.deployment.DeploymentActivity;
import com.sma.admin.common.security.*;
import com.sma.admin.extension.ExtensionErrorCode;
import com.sma.admin.support.*;
import com.sma.extension.protocol.ProtocolJson;

@Timeout(25)
class ConsoleTransportDeploymentTests {
    @TempDir static Path tlsFiles;
    @TempDir Path files;
    static SSLContext serverTls,clientTls;
    static final String MACHINE="fixture-console-machine-01234567890123456789",TICKET="fixture-console-session-01234567890123456789";
    static final SmaPrincipal ACTOR=new SmaPrincipal(1234,"console",27,"admin",null,false,DataAccess.own(),Set.of("extension:console:view"),"UTC");
    static final ConsoleProxyService.Request COMMAND=new ConsoleProxyService.Request("POST","/commands","{}".getBytes(StandardCharsets.UTF_8));
    @BeforeAll static void certificates()throws Exception{
        Path store=tlsFiles.resolve("peer.p12");String password="fixture-password";
        String executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool").toString();
        var process=new ProcessBuilder(executable,"-genkeypair","-alias","peer","-keyalg","RSA","-keysize","2048","-validity","2",
                "-dname","CN=127.0.0.1","-ext","SAN=IP:127.0.0.1","-storetype","PKCS12","-keystore",store.toString(),"-storepass",password,"-keypass",password,"-noprompt")
                .redirectErrorStream(true).redirectOutput(tlsFiles.resolve("keytool.log").toFile()).start();
        try{assertThat(process.waitFor(15,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).isZero();}
        finally{if(process.isAlive()){process.destroyForcibly();process.waitFor(3,TimeUnit.SECONDS);}}
        var keys=KeyStore.getInstance("PKCS12");try(var input=Files.newInputStream(store)){keys.load(input,password.toCharArray());}
        var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,password.toCharArray());
        var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(keys);
        serverTls=SSLContext.getInstance("TLS");serverTls.init(km.getKeyManagers(),null,null);
        clientTls=SSLContext.getInstance("TLS");clientTls.init(null,tm.getTrustManagers(),null);
    }
    private Path token()throws Exception{
        Path token=Files.writeString(files.resolve("console.token"),MACHINE);
        if(Files.getFileStore(token).supportsFileAttributeView("posix"))Files.setPosixFilePermissions(token,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        else{var acl=Files.getFileAttributeView(token,java.nio.file.attribute.AclFileAttributeView.class);acl.setAcl(List.of(java.nio.file.attribute.AclEntry.newBuilder()
                .setType(java.nio.file.attribute.AclEntryType.ALLOW).setPrincipal(acl.getOwner()).setPermissions(java.nio.file.attribute.AclEntryPermission.values()).build()));}
        return token;
    }
    private ConsoleProxyService proxy(HttpClient client,Duration wait,int capacity)throws Exception{return new ConsoleProxyService(new ConsoleProxyProperties(true,URI.create("https://fixture.test"),token()),client,wait,capacity);}
    private static byte[] identity(){return ProtocolJson.write(Map.of("code",0,"data",Map.of("token",TICKET,"expiresAt","2026-11-13T00:00:00Z")));}
    @SuppressWarnings("unchecked") private static HttpResponse<byte[]> response(byte[] body){HttpResponse<byte[]> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn(body);return response;}
    private static ConsoleProxyService.Reply forward(ConsoleProxyService proxy){return Users.as(ACTOR,()->proxy.forward(COMMAND,"Bearer host-only-fixture","zh-CN"));}

    @Test void gracefulCloseKeepsBothStagesOfAcceptedCallsAndRejectsNewWorkAtCapacity()throws Exception{
        var client=mock(HttpClient.class);var pending=new CompletableFuture<HttpResponse<byte[]>>();var entered=new CountDownLatch(2);var commands=new AtomicInteger();
        doAnswer(call->{HttpRequest request=call.getArgument(0);if(request.uri().getPath().endsWith("/sessions")){entered.countDown();return pending;}
            commands.incrementAndGet();return CompletableFuture.completedFuture(response("{}".getBytes(StandardCharsets.UTF_8)));}).when(client).sendAsync(any(HttpRequest.class),any());
        try(var proxy=proxy(client,Duration.ofSeconds(4),2);var callers=Executors.newFixedThreadPool(2)){
            var first=callers.submit(()->forward(proxy));var second=callers.submit(()->forward(proxy));
            try{
                assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();assertThat(proxy.snapshot()).isEqualTo(new DeploymentActivity.Snapshot(0,2));
                assertThatThrownBy(()->forward(proxy)).isInstanceOf(BizException.class);proxy.shutdownTransport();proxy.shutdownTransport();
                assertThatThrownBy(()->forward(proxy)).isInstanceOf(BizException.class);verify(client,never()).shutdown();
                assertThat(proxy.transportTerminated()).isFalse();
            }finally{pending.complete(response(identity()));}
            assertThat(first.get(2,TimeUnit.SECONDS).status()).isEqualTo(200);assertThat(second.get(2,TimeUnit.SECONDS).status()).isEqualTo(200);
            assertThat(commands).hasValue(2);verify(client,times(4)).sendAsync(any(HttpRequest.class),any());verify(client).shutdown();
            assertThat(proxy.snapshot().quiescent()).isTrue();assertThat(proxy.transportTerminated()).isFalse();
            when(client.isTerminated()).thenReturn(true);assertThat(proxy.transportTerminated()).isTrue();
        }finally{pending.cancel(true);}
    }
    @Test void forcedCloseDoesNotClearRunningForwardOrLetItSendTheNextStage()throws Exception{
        var client=mock(HttpClient.class);var pending=new CompletableFuture<HttpResponse<byte[]>>();var entered=new CountDownLatch(1);
        doAnswer(i->{entered.countDown();return pending;}).when(client).sendAsync(any(HttpRequest.class),any());
        try(var proxy=proxy(client,Duration.ofSeconds(4),2);var caller=Executors.newSingleThreadExecutor()){
            MaintenanceCallerAssertions.hostOnly(proxy::shutdownTransport);verifyNoInteractions(client);
            var result=caller.submit(()->forward(proxy));
            try{assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();proxy.close();when(client.isTerminated()).thenReturn(true);
                assertThat(proxy.snapshot().running()).isOne();assertThat(proxy.transportTerminated()).isFalse();}
            finally{pending.complete(response(identity()));}
            assertThatThrownBy(()->result.get(2,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).cause()
                    .isInstanceOfSatisfying(BizException.class,error->assertThat(error.getErrorCode()).isEqualTo(ExtensionErrorCode.CONSOLE_UNAVAILABLE));
            verify(client,times(1)).sendAsync(any(HttpRequest.class),any());assertThat(proxy.transportTerminated()).isTrue();
        }finally{pending.cancel(true);}
    }
    @Test void uncertainWriteTimesOutOnceAndLibraryStillNeedsTermination()throws Exception{
        var client=mock(HttpClient.class);var command=new CompletableFuture<HttpResponse<byte[]>>();var calls=new AtomicInteger();
        doAnswer(i->calls.incrementAndGet()==1?CompletableFuture.completedFuture(response(identity())):command).when(client).sendAsync(any(HttpRequest.class),any());
        try(var proxy=proxy(client,Duration.ofMillis(100),1)){
            assertThatThrownBy(()->forward(proxy)).isInstanceOf(BizException.class);assertThat(command.isCancelled()).isTrue();assertThat(calls).hasValue(2);
            assertThat(proxy.snapshot().quiescent()).isTrue();proxy.shutdownTransport();assertThat(proxy.transportTerminated()).isFalse();
            when(client.isTerminated()).thenReturn(true);assertThat(proxy.transportTerminated()).isTrue();assertThat(calls).hasValue(2);
        }
    }
    private final class Peer implements AutoCloseable {
        final HttpsServer server;final ExecutorService workers=Executors.newCachedThreadPool();
        final AtomicInteger identities=new AtomicInteger(),commands=new AtomicInteger();
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        final boolean holdIdentity,slowCommand;
        final ConsoleProxyService proxy;final HttpClient client;
        Peer(boolean holdIdentity,boolean slowCommand,Duration wait,boolean trusted)throws Exception{
            this.holdIdentity=holdIdentity;this.slowCommand=slowCommand;
            server=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setHttpsConfigurator(new HttpsConfigurator(serverTls));server.setExecutor(workers);
            server.createContext("/release/v1/",exchange->{
                try{
                    byte[] input=exchange.getRequestBody().readAllBytes();boolean login=exchange.getRequestURI().getPath().endsWith("/sessions");
                    if(login){identities.incrementAndGet();assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer "+MACHINE);
                        assertThat(new String(input,StandardCharsets.UTF_8)).doesNotContain("host-only-fixture");
                        if(this.holdIdentity){entered.countDown();release.await(8,TimeUnit.SECONDS);}}
                    else{commands.incrementAndGet();assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer "+TICKET);assertThat(input).isEqualTo(COMMAND.body());}
                    byte[] body=login?identity():"{}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");
                    if(!login&&this.slowCommand){exchange.sendResponseHeaders(200,100);exchange.getResponseBody().write('{');exchange.getResponseBody().flush();entered.countDown();release.await(8,TimeUnit.SECONDS);}
                    else{exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);}
                }catch(InterruptedException interrupted){Thread.currentThread().interrupt();}catch(IOException cancelled){}
                finally{exchange.close();}
            });server.start();
            var builder=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER);
            if(trusted)builder.sslContext(clientTls);client=builder.build();
            proxy=new ConsoleProxyService(new ConsoleProxyProperties(true,URI.create("https://127.0.0.1:"+server.getAddress().getPort()),token()),client,wait,2);
        }
        @Override public void close(){release.countDown();proxy.close();server.stop(0);workers.shutdownNow();}
    }
    @Test void realHttpsKeepsAcceptedIdentityAndCommandThenTerminatesClient()throws Exception{
        try(var peer=new Peer(true,false,Duration.ofSeconds(5),true);var caller=Executors.newSingleThreadExecutor()){
            var result=caller.submit(()->forward(peer.proxy));
            try{assertThat(peer.entered.await(3,TimeUnit.SECONDS)).isTrue();peer.proxy.shutdownTransport();assertThat(peer.proxy.snapshot().running()).isOne();
                assertThatThrownBy(()->forward(peer.proxy)).isInstanceOf(BizException.class);assertThat(peer.proxy.transportTerminated()).isFalse();}
            finally{peer.release.countDown();}
            assertThat(result.get(3,TimeUnit.SECONDS).status()).isEqualTo(200);
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(peer.proxy::transportTerminated);
            assertThat(peer.identities).hasValue(1);assertThat(peer.commands).hasValue(1);assertThat(peer.client.isTerminated()).isTrue();
        }
    }
    @Test void realHttpsUnknownCommandIsNotResentAndUntrustedCertificateIsRejected()throws Exception{
        try(var peer=new Peer(false,true,Duration.ofSeconds(2),true)){
            assertThatThrownBy(()->forward(peer.proxy)).isInstanceOf(BizException.class);assertThat(peer.entered.await(1,TimeUnit.SECONDS)).isTrue();
            peer.proxy.shutdownTransport();peer.release.countDown();
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(peer.proxy::transportTerminated);
            assertThat(peer.identities).hasValue(1);assertThat(peer.commands).hasValue(1);assertThat(peer.proxy.snapshot().quiescent()).isTrue();
        }
        try(var peer=new Peer(false,false,Duration.ofSeconds(3),false)){
            assertThatThrownBy(()->forward(peer.proxy)).isInstanceOf(BizException.class);peer.proxy.shutdownTransport();
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(peer.proxy::transportTerminated);
            assertThat(peer.identities).hasValue(0);assertThat(peer.commands).hasValue(0);
        }
    }
}
