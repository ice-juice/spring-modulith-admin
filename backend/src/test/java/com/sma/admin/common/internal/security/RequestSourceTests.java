package com.sma.admin.common.internal.security;

import static org.assertj.core.api.Assertions.*;
import static com.sma.admin.common.security.RequestSource.Basis.*;
import java.util.Set;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.sma.admin.common.security.*;

class RequestSourceTests {
    private RequestSourceResolver resolver(String...networks){return new RequestSourceResolver(new RequestSourceProperties(Set.of(networks),null,null));}
    private MockHttpServletRequest request(String peer,String chain){
        var request=new MockHttpServletRequest("POST","/api/auth/login");request.setRemoteAddr(peer);
        if(chain!=null)request.addHeader("X-Forwarded-For",chain);return request;
    }
    @Test void numericLiteralsAndCidrAreStrictBoundedAndNeverResolveHostnames(){
        for(String value:new String[]{"example.com","127.1","0177.0.0.1","2130706433","0x7f.0.0.1","1.2.3.256","1.2.3.4:80","[::1]","fe80::1%eth0","::ffff:192.000.2.1"," 127.0.0.1","1.2.3.4\r\n"})
            assertThatThrownBy(()->SourceIp.parse(value)).as(value).isInstanceOf(IllegalArgumentException.class);
        assertThat(SourceIp.parse("::FFFF:192.0.2.10").text()).isEqualTo("192.0.2.10");
        assertThat(SourceIp.network("2001:DB8::/32").contains(SourceIp.parse("2001:db8:ffff::1"))).isTrue();
        assertThat(SourceIp.network("2001:db8::/32").contains(SourceIp.parse("2001:db9::1"))).isFalse();
        assertThat(SourceIp.network("192.0.2.0/24").contains(SourceIp.parse("192.0.3.0"))).isFalse();
        assertThat(SourceIp.network("::ffff:192.0.2.0/120").canonical()).isEqualTo("192.0.2.0/24");
        for(String value:new String[]{"0.0.0.0/0","::/0","192.0.2.4/24","2001:db8::1/32","192.0.2.0/33","::/129","::ffff:192.0.2.0/24","192.0.2.0/024","127.0.0.1/","127.0.0.1/32/32"})
            assertThatThrownBy(()->SourceIp.network(value)).as(value).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void untrustedPeerCannotInjectClientOrCauseForwardingHeaderEnumeration(){
        var request=new MockHttpServletRequest(){@Override public java.util.Enumeration<String> getHeaders(String name){throw new AssertionError("untrusted chain must not be enumerated");}};
        request.setRemoteAddr("198.51.100.8");request.addHeader("X-Forwarded-For","203.0.113.1");request.addHeader("Forwarded","for=spoofed");
        assertThat(resolver("192.0.2.0/24").resolve(request)).isEqualTo(new RequestSource("198.51.100.8","198.51.100.8",UNTRUSTED_FORWARDING,0));
        assertThat(resolver().resolve(request("198.51.100.8",null)).basis()).isEqualTo(DIRECT);
        assertThat(resolver().resolve(request("hostname.invalid",null))).isEqualTo(new RequestSource("","",UNKNOWN_PEER,0));
    }
    @Test void rightToLeftTrustStopsAtFirstUntrustedAddressAndIgnoresSpoofedLeftPrefix(){
        var source=resolver("192.0.2.0/24","2001:db8::/32");
        assertThat(source.resolve(request("192.0.2.9","203.0.113.66, 198.51.100.8, 2001:db8::3")))
                .isEqualTo(new RequestSource("192.0.2.9","198.51.100.8",TRUSTED_CHAIN,2));
        assertThat(source.resolve(request("::ffff:192.0.2.9","198.51.100.8")))
                .isEqualTo(new RequestSource("192.0.2.9","198.51.100.8",TRUSTED_CHAIN,1));
        assertThat(source.resolve(request("2001:db8::9","2001:db9::1")).clientAddress()).isEqualTo("2001:db9:0:0:0:0:0:1");
        assertThat(source.resolve(request("192.0.2.9",null)).basis()).isEqualTo(MISSING_CHAIN);
        assertThat(source.resolve(request("192.0.2.9","192.0.2.7,2001:db8::3")).basis()).isEqualTo(INVALID_CHAIN);
    }
    @Test void malformedAmbiguousAndOversizedChainsFallBackWithoutLeakingInput(){
        var source=resolver("192.0.2.0/24");
        for(String value:new String[]{""," ",",198.51.100.8","198.51.100.8,","unknown","_hidden","example.com","\"198.51.100.8\"","[2001:db9::1]:80",
                "198.51.100.8\r\nX-Secret: test","198.51.100.8\u0000","1".repeat(2049),"198.51.100.8,".repeat(17)}){
            assertThat(source.resolve(request("192.0.2.9",value))).isEqualTo(new RequestSource("192.0.2.9","192.0.2.9",INVALID_CHAIN,0));
        }
        var repeated=request("192.0.2.9","198.51.100.8");repeated.addHeader("X-Forwarded-For","203.0.113.8");
        assertThat(source.resolve(repeated).basis()).isEqualTo(INVALID_CHAIN);
        var mixed=request("192.0.2.9","198.51.100.8");mixed.addHeader("Forwarded","for=198.51.100.8");
        assertThat(source.resolve(mixed).basis()).isEqualTo(INVALID_CHAIN);
        assertThat(source.resolve(request("192.0.2.9"," \t198.51.100.8\t ")).basis()).isEqualTo(TRUSTED_CHAIN);
        var limited=new RequestSourceResolver(new RequestSourceProperties(Set.of("192.0.2.0/24"),1,64));
        assertThat(limited.resolve(request("192.0.2.9","198.51.100.8")).basis()).isEqualTo(TRUSTED_CHAIN);
        assertThat(limited.resolve(request("192.0.2.9","198.51.100.8,192.0.2.8")).basis()).isEqualTo(INVALID_CHAIN);
    }
    @Test void filterDoesNotRewriteRequestOrGrantIdentityAndAlwaysReleasesSource()throws Exception{
        var scope=new RequestSourceScope();var filter=new RequestSourceFilter(new RequestSourceProperties(Set.of("192.0.2.0/24"),null,null),scope);
        var request=request("192.0.2.9","198.51.100.8");request.addHeader("X-Tenant-Id","99");request.addHeader("Authorization","Bearer secret");
        request.addParameter("password","private");request.setContent("secret-body".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response=new MockHttpServletResponse();
        filter.doFilter(request,response,(r,s)->{
            assertThat(r).isSameAs(request);assertThat(r.getRemoteAddr()).isEqualTo("192.0.2.9");
            assertThat(scope.current().orElseThrow().clientAddress()).isEqualTo("198.51.100.8");
            assertThat(scope.current().orElseThrow().toString()).doesNotContain("secret","private","99");
            assertThat(TenantEntryScope.optional()).isEmpty();assertThatThrownBy(ActorContext::require).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            ((jakarta.servlet.http.HttpServletResponse)s).setStatus(403);
        });
        assertThat(response.getStatus()).isEqualTo(403);assertThat(scope.current()).isEmpty();
        assertThatThrownBy(()->filter.doFilter(request,new MockHttpServletResponse(),(r,s)->{throw new java.io.IOException("failed downstream");}))
                .isInstanceOf(java.io.IOException.class);
        assertThat(scope.current()).isEmpty();
        filter.doFilter(request("203.0.113.1",null),new MockHttpServletResponse(),(r,s)->assertThat(scope.current().orElseThrow().clientAddress()).isEqualTo("203.0.113.1"));
        assertThat(scope.current()).isEmpty();
    }
    @Test void threadScopeCannotBeInheritedNestedOrClosedFromAnotherThread()throws Exception{
        var scope=new RequestSourceScope();var value=new RequestSource("192.0.2.1","192.0.2.1",DIRECT,0);
        try(var binding=scope.open(value);var worker=Executors.newSingleThreadExecutor()){
            assertThatThrownBy(()->scope.open(value)).isInstanceOf(IllegalStateException.class);
            worker.submit(()->{
                assertThat(scope.current()).isEmpty();assertThatThrownBy(binding::close).isInstanceOf(IllegalStateException.class);
                try(var other=scope.open(new RequestSource("192.0.2.2","192.0.2.2",DIRECT,0))){assertThat(scope.current().orElseThrow().peerAddress()).isEqualTo("192.0.2.2");}
                assertThat(scope.current()).isEmpty();
            }).get();
            assertThat(scope.current()).contains(value);
        }
        assertThat(scope.current()).isEmpty();
    }
    @Test void rejectionDuringTenantResolutionStillHasOnlyNetworkEvidence()throws Exception{
        var scope=new RequestSourceScope();var filter=new RequestSourceFilter(new RequestSourceProperties(null,null,null),scope);
        var entries=org.mockito.Mockito.mock(TenantEntryQuery.class);
        org.mockito.Mockito.when(entries.resolve("unknown.example.test")).thenAnswer(call->{assertThat(scope.current()).isPresent();return java.util.Optional.empty();});
        var tenant=new TenantRequestFilter(entries,new TenantIngressProperties(Set.of()),(request,response,error)->response.setStatus(error.status()));
        var request=request("192.0.2.1",null);request.addHeader("Host","unknown.example.test");var response=new MockHttpServletResponse();
        filter.doFilter(request,response,(r,s)->tenant.doFilter(r,s,(a,b)->fail("unknown host must remain rejected")));
        assertThat(response.getStatus()).isEqualTo(404);assertThat(scope.current()).isEmpty();assertThat(TenantEntryScope.optional()).isEmpty();
    }
}
