package com.sma.admin.common.internal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class BrowserRequestFilterTests {
    private final BrowserRequestFilter filter = new BrowserRequestFilter(new BrowserRequestProperties(Set.of("https://console.example")),
            (request, response, error) -> response.setStatus(error.status()));

    @Test void deniesCrossSiteAndOpaqueOriginsEvenWithForgedProxyHeaders() throws Exception {
        for (String origin : new String[]{"https://evil.example", "null", "https://console.example.evil", "https://console.example@evil.example"}) {
            var request = request("POST", "/api/iam/users");
            request.addHeader("Origin", origin); request.addHeader("X-Forwarded-Host", "console.example");
            assertThat(execute(request)).isEqualTo(403);
        }
        var request = request("DELETE", "/api/iam/users/1");
        request.addHeader("Sec-Fetch-Site", "cross-site");
        assertThat(execute(request)).isEqualTo(403);
    }
    @Test void allowsExactOriginsNativeBearerAndMultipartWithoutChangingBodies() throws Exception {
        var same = request("POST", "/api/config/parameters"); same.addHeader("Origin", "http://localhost:80");
        assertThat(execute(same)).isEqualTo(204);
        var known = request("PUT", "/api/config/parameters/1"); known.addHeader("Origin", "https://console.example");
        known.addHeader("Sec-Fetch-Site", "cross-site"); assertThat(execute(known)).isEqualTo(204);
        assertThat(execute(request("POST", "/api/auth/login"))).isEqualTo(204);
        var upload = request("POST", "/api/file/files"); upload.setContentType("multipart/form-data; boundary=test");
        assertThat(execute(upload)).isEqualTo(204);
    }
    @Test void rejectsSimpleFormTypesButPreservesSignedMachineProtocolAndSafeReads() throws Exception {
        for (String type : new String[]{"text/plain", "application/x-www-form-urlencoded;charset=UTF-8"}) {
            var request = request("POST", "/api/auth/login"); request.setContentType(type);
            assertThat(execute(request)).isEqualTo(415);
            request.setRequestURI("/open/echo"); assertThat(execute(request)).isEqualTo(204);
        }
        var read = request("GET", "/api/config/parameters"); read.addHeader("Origin", "https://evil.example");
        assertThat(execute(read)).isEqualTo(204); // Authentication remains the security chain's responsibility.
    }
    @Test void rejectsWildcardOrPathConfiguration() {
        for (String origin : new String[]{"*", "https://example/path", "https://example/", "null"})
            assertThatThrownBy(() -> new BrowserRequestProperties(Set.of(origin))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void capsActualJsonBytesIncludingMissingOrFalseContentLengthAndPreservesNativeBytes()throws Exception{
        var bounded=new BrowserRequestFilter(new BrowserRequestProperties(Set.of(),1024,1),(req,res,error)->res.setStatus(error.status()));
        for(long length:new long[]{-1,0,2048}){
            var request=new MockHttpServletRequest("POST","/api/auth/login"){
                @Override public long getContentLengthLong(){return length;}
            };
            request.setContentType("application/json");request.setContent(new byte[1025]);var response=new MockHttpServletResponse();
            bounded.doFilter(request,response,(req,res)->{req.getInputStream().readAllBytes();throw new AssertionError("Oversized JSON reached the consumer");});
            assertThat(response.getStatus()).isEqualTo(413);
        }
        byte[] original="{ \"message\": \"原始字节\" }\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var request=request("POST","/api/extension/consoles/requests");request.setContent(original);
        bounded.doFilter(request,new MockHttpServletResponse(),(req,res)->assertThat(req.getInputStream().readAllBytes()).isEqualTo(original));
        assertThat(new BrowserRequestProperties(null).maximumJsonBytes()).isEqualTo(2*1024*1024);
        assertThatThrownBy(()->new BrowserRequestProperties(Set.of(),0,1)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void jsonAdmissionIsBoundedBeforeMvcAndReleasedEvenWhenTheHandlerFails()throws Exception{
        var bounded=new BrowserRequestFilter(new BrowserRequestProperties(Set.of(),1024,1),(req,res,error)->res.setStatus(error.status()));
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
            var first=pool.submit(()->{
                try{bounded.doFilter(request("POST","/api/auth/login"),new MockHttpServletResponse(),(req,res)->{
                    req.getInputStream().readAllBytes();entered.countDown();try{if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Test timeout");}
                    catch(InterruptedException failure){Thread.currentThread().interrupt();throw new AssertionError(failure);}
                    throw new java.io.IOException("injected downstream failure");
                });throw new AssertionError("Expected handler failure");}catch(java.io.IOException expected){}return null;
            });
            try{
                assertThat(entered.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var response=new MockHttpServletResponse();
                bounded.doFilter(request("POST","/api/auth/login"),response,(req,res)->{req.getInputStream().readAllBytes();throw new AssertionError("Concurrent JSON admitted");});
                assertThat(response.getStatus()).isEqualTo(503);assertThat(response.getHeader("Retry-After")).isEqualTo("1");
                // A verified human established downstream gets its own lane despite anonymous saturation.
                bounded.doFilter(request("POST","/api/iam/users"),new MockHttpServletResponse(),(req,res)->{
                    com.sma.admin.support.Users.as(com.sma.admin.support.Users.principal(1,1,com.sma.admin.common.security.DataAccess.own()),()->{
                        try{req.getInputStream().readAllBytes();}catch(java.io.IOException failure){throw new AssertionError(failure);}return null;
                    });
                });
            }finally{release.countDown();}
            first.get(10,java.util.concurrent.TimeUnit.SECONDS);
        }
        bounded.doFilter(request("POST","/api/auth/login"),new MockHttpServletResponse(),(req,res)->req.getInputStream().readAllBytes());
    }
    @Test void authenticationRejectionDoesNotReadTheBodyOrClaimAParsingSlot()throws Exception{
        var request=new MockHttpServletRequest("POST","/api/iam/users"){
            @Override public jakarta.servlet.ServletInputStream getInputStream(){throw new AssertionError("Body read before authentication rejection");}
        };
        request.setContentType("application/json");request.addHeader("Authorization","Bearer forged");
        var response=new MockHttpServletResponse();filter.doFilter(request,response,(req,res)->((jakarta.servlet.http.HttpServletResponse)res).setStatus(401));
        assertThat(response.getStatus()).isEqualTo(401);
    }
    private MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method, path); request.setContentType("application/json"); return request;
    }
    private int execute(MockHttpServletRequest request) throws Exception {
        var response = new MockHttpServletResponse(); var reached = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> { reached.set(true); ((jakarta.servlet.http.HttpServletResponse)res).setStatus(204); });
        assertThat(reached.get()).isEqualTo(response.getStatus() == 204);
        return response.getStatus();
    }
}
