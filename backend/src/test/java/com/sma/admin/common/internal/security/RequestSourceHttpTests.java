package com.sma.admin.common.internal.security;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.test.context.TestPropertySource;
import com.sma.admin.common.security.RequestSource;
import com.sma.admin.common.security.RequestSourceQuery;
import com.sma.admin.support.IntegrationTest;

/** Real servlet chain with a test-only observation filter; no production diagnostic endpoint or forwarded identity. */
@IntegrationTest @SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties={"server.address=127.0.0.1","sma.security.source.trusted-proxies=127.0.0.1/32",
        "sma.security.source.maximum-hops=3","sma.security.source.maximum-header-characters=128"})
@Import(RequestSourceHttpTests.Observation.class)
class RequestSourceHttpTests {
    record Seen(RequestSource before,RequestSource after,String remote,int status){}
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class Observation {
        @Bean ConcurrentHashMap<String,Seen> observations(){return new ConcurrentHashMap<>();}
        @Bean FilterRegistrationBean<jakarta.servlet.Filter> observationFilter(RequestSourceQuery source,ConcurrentHashMap<String,Seen> observations){
            jakarta.servlet.Filter filter=(request,response,chain)->{
                var http=(jakarta.servlet.http.HttpServletRequest)request;
                String key=http.getHeader("X-Test-Observation");var before=source.current().orElse(null);
                try{chain.doFilter(request,response);}finally{
                    if(key!=null && key.matches("source-[a-z-]{1,32}"))observations.put(key,new Seen(before,source.current().orElse(null),
                            request.getRemoteAddr(),((jakarta.servlet.http.HttpServletResponse)response).getStatus()));
                }
            };
            var registration=new FilterRegistrationBean<>(filter);registration.setOrder(Ordered.HIGHEST_PRECEDENCE+35);return registration;
        }
    }
    @LocalServerPort int port;
    @Autowired ConcurrentHashMap<String,Seen> observations;
    @Autowired RequestSourceQuery source;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private HttpResponse<String> call(String key,String path,String...headers)throws Exception{
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(10)).header("X-Test-Observation",key);
        for(int i=0;i<headers.length;i+=2)request.header(headers[i],headers[i+1]);
        return client.send(request.GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    private Seen seen(String key)throws Exception{
        long deadline=System.nanoTime()+Duration.ofSeconds(2).toNanos();Seen value;
        while((value=observations.get(key))==null && System.nanoTime()<deadline)Thread.sleep(10);
        assertThat(value).isNotNull();assertThat(value.before()).isEqualTo(value.after());assertThat(value.remote()).isEqualTo("127.0.0.1");
        assertThat(source.current()).isEmpty();return value;
    }
    @Test void realContainerPreservesPeerAndResponseWhileSourceMetadataRemainsBounded()throws Exception{
        assertThat(call("source-valid","/api/tenant/entry","X-Forwarded-For","203.0.113.9,198.51.100.7").statusCode()).isEqualTo(200);
        assertThat(seen("source-valid").before()).isEqualTo(new RequestSource("127.0.0.1","198.51.100.7",RequestSource.Basis.TRUSTED_CHAIN,1));
        for(var row:new String[][]{{"source-missing"},{"source-invalid","X-Forwarded-For","unknown"},
                {"source-large","X-Forwarded-For","1".repeat(129)},
                {"source-mixed","X-Forwarded-For","198.51.100.7","Forwarded","for=198.51.100.7"},
                {"source-repeated","X-Forwarded-For","198.51.100.7","X-Forwarded-For","203.0.113.9"}}){
            var response=call(row[0],"/api/tenant/entry",java.util.Arrays.copyOfRange(row,1,row.length));
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).doesNotContain("198.51.100.7","203.0.113.9","TRUSTED_CHAIN","INVALID_CHAIN");
            var observation=seen(row[0]).before();assertThat(observation.clientAddress()).isEqualTo("127.0.0.1");
            assertThat(observation.basis()).isEqualTo(row.length==1?RequestSource.Basis.MISSING_CHAIN:RequestSource.Basis.INVALID_CHAIN);
        }
    }
    @Test void authorizationStillRejectsWithoutIdentityAndForwardedTenantHeadersDoNotGrantEntry()throws Exception{
        var response=call("source-denied","/api/iam/users","X-Forwarded-For","198.51.100.9","X-Tenant-Id","2","X-Forwarded-Host","tenant-evil.test","X-Forwarded-Proto","https");
        assertThat(response.statusCode()).isEqualTo(401);assertThat(response.body()).contains("common.error.unauthorized");
        assertThat(seen("source-denied").before().clientAddress()).isEqualTo("198.51.100.9");
        // Subsequent request gets its own direct evidence, not the previous forwarding chain.
        assertThat(call("source-next","/api/tenant/entry").statusCode()).isEqualTo(200);
        assertThat(seen("source-next").before().basis()).isEqualTo(RequestSource.Basis.MISSING_CHAIN);
    }
}
