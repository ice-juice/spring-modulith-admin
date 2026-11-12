package com.sma.admin.identity.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import com.sun.net.httpserver.HttpServer;
import com.sma.admin.common.BizException;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@Timeout(20)
class GoogleHttpTransportTests {
    private static final class Peer implements AutoCloseable {
        final HttpServer server;final ExecutorService serverThreads=Executors.newVirtualThreadPerTaskExecutor();
        final AtomicInteger status=new AtomicInteger(200),delay=new AtomicInteger(),delayBody=new AtomicInteger(),redirected=new AtomicInteger();
        final AtomicReference<String> body=new AtomicReference<>("{\"keys\":[]}"),contentType=new AtomicReference<>("application/json"),received=new AtomicReference<>();
        volatile CountDownLatch entered,release;
        final GoogleHttpTransport transport;
        Peer(Duration deadline)throws Exception{
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(serverThreads);
            server.createContext("/",exchange->{
                if(exchange.getRequestURI().getPath().equals("/redirect-target"))redirected.incrementAndGet();
                received.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                try{
                    if(entered!=null){entered.countDown();release.await(6,TimeUnit.SECONDS);}
                    if(delay.get()>0)Thread.sleep(delay.get());
                    byte[] bytes=body.get().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type",contentType.get());
                    exchange.getResponseHeaders().set("Cache-Control","public, max-age=120");exchange.getResponseHeaders().set("Age","20");
                    exchange.getResponseHeaders().set("Location","/redirect-target");exchange.sendResponseHeaders(status.get(),bytes.length);
                    if(delayBody.get()>0)Thread.sleep(delayBody.get());exchange.getResponseBody().write(bytes);
                }catch(InterruptedException interrupted){Thread.currentThread().interrupt();}catch(java.io.IOException disconnected){/* Expected when the client rejects an oversized or timed-out response. */}
                finally{exchange.close();}
            });server.start();
            URI base=URI.create("http://127.0.0.1:"+server.getAddress().getPort());
            transport=new GoogleHttpTransport(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).build(),
                    base.resolve("/certs"),base.resolve("/token"),deadline,JsonMapper.builder().build());
        }
        @Override public void close(){if(release!=null)release.countDown();transport.close();server.stop(0);serverThreads.shutdownNow();}
    }
    @Test void localProtocolBindsFormFieldsAndOnlyReturnsIdToken()throws Exception{
        try(var peer=new Peer(Duration.ofSeconds(4))){
            assertThat(peer.transport.fetchKeys().lifetime()).isEqualTo(Duration.ofSeconds(100));
            peer.body.set("{\"id_token\":\"fixture.jwt.value\",\"access_token\":\"not-retained\",\"refresh_token\":\"not-retained\"}");
            assertThat(peer.transport.exchange("client-id","test-secret-value".toCharArray(),"4/code+special","v".repeat(43),"https://app.example.test/cb")).isEqualTo("fixture.jwt.value");
            assertThat(peer.received.get()).contains("grant_type=authorization_code","client_id=client-id","client_secret=test-secret-value","code=4%2Fcode%2Bspecial","code_verifier="+"v".repeat(43),"redirect_uri=https%3A%2F%2Fapp.example.test%2Fcb");
            peer.transport.exchange("native-client",null,"code","v".repeat(43),"com.example.app:/cb");assertThat(peer.received.get()).doesNotContain("client_secret");
        }
    }
    @Test void redirectsOversizedResponsesAndInvalidContentAreRejected()throws Exception{
        try(var peer=new Peer(Duration.ofSeconds(4))){
            peer.status.set(302);assertThatThrownBy(peer.transport::fetchKeys).isInstanceOf(BizException.class);assertThat(peer.redirected).hasValue(0);
            peer.status.set(200);peer.body.set("x".repeat(65537));assertThatThrownBy(peer.transport::fetchKeys).isInstanceOf(BizException.class);
            peer.body.set("{}");peer.contentType.set("text/html");assertThatThrownBy(peer.transport::fetchKeys).isInstanceOf(BizException.class);
            peer.contentType.set("application/json");assertThatThrownBy(()->peer.transport.exchange("client",null,"code","v".repeat(43),"https://app.example.test/cb")).isInstanceOf(BizException.class);
            peer.status.set(400);assertThatThrownBy(()->peer.transport.exchange("client",null,"code","v".repeat(43),"https://app.example.test/cb"))
                    .isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(com.sma.admin.identity.IdentityErrorCode.INVALID_GOOGLE_PROOF));
        }
    }
    @Test void totalDeadlineAndDatabaseTransactionGateAreEnforced()throws Exception{
        try(var peer=new Peer(Duration.ofMillis(200))){
            peer.delayBody.set(1200);long start=System.nanoTime();assertThatThrownBy(peer.transport::fetchKeys).isInstanceOf(BizException.class);
            assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(2));
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try{assertThatThrownBy(peer.transport::fetchKeys).isInstanceOf(IllegalStateException.class);}
            finally{TransactionSynchronizationManager.setActualTransactionActive(false);}
        }
        assertThat(GoogleHttpTransport.lifetime(HttpHeaders.of(Map.of("Cache-Control",List.of("max-age=999999")),(k,v)->true))).isEqualTo(Duration.ofHours(6));
        assertThat(GoogleHttpTransport.lifetime(HttpHeaders.of(Map.of("Cache-Control",List.of("no-store, max-age=300")),(k,v)->true))).isZero();
    }
    @Test void concurrencyBudgetRejectsAQueueInsteadOfStartingUnlimitedRequests()throws Exception{
        try(var peer=new Peer(Duration.ofSeconds(8));var callers=Executors.newVirtualThreadPerTaskExecutor()){
            peer.entered=new CountDownLatch(4);peer.release=new CountDownLatch(1);var pending=new ArrayList<Future<GoogleHttpTransport.JwksDocument>>();
            for(int i=0;i<4;i++)pending.add(callers.submit(peer.transport::fetchKeys));
            try{
                assertThat(peer.entered.await(4,TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(peer.transport::fetchKeys).isInstanceOf(BizException.class);
            }finally{peer.release.countDown();}
            for(var request:pending)assertThat(request.get(3,TimeUnit.SECONDS).body()).isEqualTo("{\"keys\":[]}");
        }
    }
    @Test void gracefulShutdownFinishesAcceptedRealRequestThenTerminatesItsClient()throws Exception{
        try(var peer=new Peer(Duration.ofSeconds(8));var callers=Executors.newSingleThreadExecutor()){
            peer.entered=new CountDownLatch(1);peer.release=new CountDownLatch(1);
            var pending=callers.submit(peer.transport::fetchKeys);
            try{
                assertThat(peer.entered.await(3,TimeUnit.SECONDS)).isTrue();
                peer.transport.shutdownTransport();peer.transport.shutdownTransport();
                assertThat(peer.transport.snapshot().running()).isOne();assertThat(peer.transport.transportTerminated()).isFalse();
                assertThatThrownBy(peer.transport::fetchKeys).isInstanceOf(BizException.class);
            }finally{peer.release.countDown();}
            assertThat(pending.get(3,TimeUnit.SECONDS).body()).isEqualTo("{\"keys\":[]}");
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(peer.transport::transportTerminated);
            assertThat(peer.transport.snapshot().quiescent()).isTrue();
        }
    }
}
