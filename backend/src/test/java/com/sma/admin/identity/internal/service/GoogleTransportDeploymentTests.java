package com.sma.admin.identity.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.BizException;
import com.sma.admin.common.deployment.DeploymentActivity;
import com.sma.admin.identity.IdentityErrorCode;
import com.sma.admin.support.MaintenanceCallerAssertions;
import tools.jackson.databind.json.JsonMapper;

class GoogleTransportDeploymentTests {
    private HttpClient client(){var client=mock(HttpClient.class);when(client.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);when(client.cookieHandler()).thenReturn(Optional.empty());return client;}
    private GoogleHttpTransport transport(HttpClient client,Duration deadline){return new GoogleHttpTransport(client,URI.create("https://fixture.test/keys"),URI.create("https://fixture.test/token"),deadline,JsonMapper.builder().build());}
    @SuppressWarnings("unchecked") private HttpResponse<byte[]> response(){
        HttpResponse<byte[]> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type",List.of("application/json")),(a,b)->true));
        when(response.body()).thenReturn("{\"keys\":[]}".getBytes(StandardCharsets.UTF_8));return response;
    }
    @Test void cancelledCallRetainsRealThreadAndClientTerminationRequiresSeparateEvidence()throws Exception{
        var client=client();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var shuttingDown=new AtomicBoolean();
        var contextFailure=new AtomicReference<Throwable>();var inherited=new InheritableThreadLocal<String>();inherited.set("caller");
        var response=response();
        doAnswer(i->{
            try{assertThat(inherited.get()).isNull();assertThat(org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).isNull();
                assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()).isNull();}
            catch(Throwable error){contextFailure.set(error);}
            entered.countDown();awaitIgnoringInterrupts(release);return response;
        }).when(client).send(any(HttpRequest.class),any());
        doAnswer(i->{shuttingDown.set(true);return null;}).when(client).shutdown();
        try(var transport=transport(client,Duration.ofMillis(250));var caller=Executors.newSingleThreadExecutor()){
            var result=caller.submit(transport::fetchKeys);
            try{
                assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(()->result.get(3,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).cause()
                        .isInstanceOfSatisfying(BizException.class,error->assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.PROVIDER_UNAVAILABLE));
                assertThat(transport.snapshot()).isEqualTo(new DeploymentActivity.Snapshot(0,1));
                transport.shutdownTransport();assertThat(shuttingDown).isFalse();assertThat(transport.transportTerminated()).isFalse();
                assertThatThrownBy(transport::fetchKeys).isInstanceOf(BizException.class);
            }finally{release.countDown();}
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3)).until(shuttingDown::get);
            assertThat(transport.snapshot().quiescent()).isTrue();assertThat(transport.transportTerminated()).isFalse();
            when(client.isTerminated()).thenReturn(true);
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3)).until(transport::transportTerminated);
            assertThat(contextFailure.get()).isNull();verify(client,times(1)).send(any(HttpRequest.class),any());
        }finally{release.countDown();inherited.remove();}
    }
    @Test void shutdownRequiresHostAndTransactionGuardPrecedesSubmission()throws Exception{
        var client=client();doReturn(response()).when(client).send(any(HttpRequest.class),any());
        try(var transport=transport(client,Duration.ofSeconds(2))){
            MaintenanceCallerAssertions.hostOnly(transport::shutdownTransport);verify(client,never()).shutdown();
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
            try{assertThatThrownBy(transport::fetchKeys).isInstanceOf(IllegalStateException.class);verify(client,never()).send(any(),any());}
            finally{org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);}
            assertThat(transport.fetchKeys().body()).contains("keys");transport.shutdownTransport();
            assertThatThrownBy(transport::fetchKeys).isInstanceOf(BizException.class);
        }
    }
    @Test void realSlowResponseCancellationDisconnectsSocketAndTerminatesJdkClient()throws Exception{
        try(var listener=new ServerSocket(0,5,InetAddress.getByName("127.0.0.1"));var peer=Executors.newSingleThreadExecutor()){
            listener.setSoTimeout(5000);var headersSent=new AtomicBoolean();
            var disconnected=peer.submit(()->{
                try(var socket=listener.accept()){
                    socket.setSoTimeout(5000);var input=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));
                    String line;while((line=input.readLine())!=null&&!line.isEmpty()){}
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 100\r\n\r\n{".getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();headersSent.set(true);
                    try{return input.read()==-1;}catch(SocketException reset){return true;}
                }
            });
            URI address=URI.create("http://127.0.0.1:"+listener.getLocalPort());
            var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NEVER).build();
            try(var transport=new GoogleHttpTransport(client,address,address,Duration.ofSeconds(1),JsonMapper.builder().build())){
                assertThatThrownBy(transport::fetchKeys).isInstanceOf(BizException.class);assertThat(headersSent).isTrue();
                transport.shutdownTransport();assertThat(disconnected.get(5,TimeUnit.SECONDS)).isTrue();
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(transport::transportTerminated);
                assertThat(transport.snapshot().quiescent()).isTrue();assertThat(client.isTerminated()).isTrue();
            }
        }
    }
    private static void awaitIgnoringInterrupts(CountDownLatch latch){
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(latch.getCount()!=0&&System.nanoTime()<until)try{latch.await(10,TimeUnit.MILLISECONDS);}catch(InterruptedException ignored){}
        if(latch.getCount()!=0)throw new AssertionError("Fixture not released");
    }
}
