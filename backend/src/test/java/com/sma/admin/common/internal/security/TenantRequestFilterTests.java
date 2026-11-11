package com.sma.admin.common.internal.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.common.ErrorResponseWriter;
import com.sma.admin.common.security.*;

class TenantRequestFilterTests {
    private static final TenantEntry ENTRY=new TenantEntry(72,100,"acme.example.com","admin",3);
    private static final ErrorResponseWriter ERRORS=(request,response,error) -> response.setStatus(error.status());
    private final TenantEntryQuery entries=mock(TenantEntryQuery.class);
    private TenantRequestFilter filter(Set<String> proxies) { return new TenantRequestFilter(entries,new TenantIngressProperties(proxies),ERRORS); }
    private static MockHttpServletRequest request(String host) {
        var request=new MockHttpServletRequest("GET","/api/auth/captcha"); request.addHeader("Host",host); return request;
    }

    @Test void resolvesOnlyRegisteredHostBeforeAnonymousTenantDataAndNeverGrantsIdentity() throws Exception {
        when(entries.resolve("acme.example.com")).thenAnswer(call -> {
            assertThatThrownBy(CurrentUser::tenantId).isInstanceOf(AccessDeniedException.class);
            return Optional.of(ENTRY);
        });
        var request=request("Acme.Example.com.:8443"); request.addHeader("X-Tenant-Id","99"); request.addParameter("tenantId","99");
        filter(Set.of()).doFilter(request,new MockHttpServletResponse(),(r,s) -> {
            assertThat(CurrentUser.tenantId()).isEqualTo(72);
            assertThatThrownBy(CurrentUser::require).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> TenantScope.within(99,() -> 1)).isInstanceOf(AccessDeniedException.class);
            assertThat(r.getServerName()).isEqualTo("acme.example.com"); assertThat(r.getServerPort()).isEqualTo(8443);
        });
        assertThat(TenantEntryScope.optional()).isEmpty(); assertThat(TenantEntryScope.isHttpRequest()).isFalse();
    }

    @Test void unknownMalformedOrRepeatedHostsDoNotReachAuthentication() throws Exception {
        when(entries.resolve(anyString())).thenReturn(Optional.empty());
        for(String host:new String[]{"unknown.example.com","good.example.com,evil.example.com","a.example.com:0"}) {
            var response=new MockHttpServletResponse();
            filter(Set.of()).doFilter(request(host),response,(r,s) -> fail("must reject host"));
            assertThat(response.getStatus()).isIn(400,404); assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            assertThat(TenantEntryScope.isHttpRequest()).isFalse();
        }
        var request=request("acme.example.com"); request.addHeader("Host","evil.example.com");
        var response=new MockHttpServletResponse();filter(Set.of()).doFilter(request,response,(r,s) -> fail("duplicate Host"));
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test void forwardingRequiresExactTrustedPeerAndCanonicalOriginReachesBrowserGuard() throws Exception {
        when(entries.resolve("acme.example.com")).thenReturn(Optional.of(ENTRY));
        var request=request("internal.example.com:8080"); request.setRemoteAddr("192.0.2.1");
        request.addHeader("X-Forwarded-Host","acme.example.com"); request.addHeader("X-Forwarded-Proto","https");
        request.setMethod("POST");request.addHeader("Origin","https://acme.example.com");request.setContentType("application/json");
        var browser=new BrowserRequestFilter(new BrowserRequestProperties(Set.of()),ERRORS);
        var response=new MockHttpServletResponse();
        filter(Set.of("192.0.2.1")).doFilter(request,response,(r,s) -> browser.doFilter(r,s,(external,reply) -> {
            assertThat(external.getScheme()).isEqualTo("https"); assertThat(external.getServerPort()).isEqualTo(443);
            assertThat(CurrentUser.tenantId()).isEqualTo(72);reply.getWriter().write("ok");
        }));
        assertThat(response.getContentAsString()).isEqualTo("ok");
        request.setRemoteAddr("192.0.2.2");var untrusted=new MockHttpServletResponse();
        filter(Set.of("192.0.2.1")).doFilter(request,untrusted,(r,s) -> fail("forwarded host from untrusted peer"));
        assertThat(untrusted.getStatus()).isEqualTo(404);verify(entries).resolve("internal.example.com");
    }

    @Test void crossTenantOriginIsRejectedAndExceptionsCannotLeakEntryToNextRequest() throws Exception {
        when(entries.resolve("acme.example.com")).thenReturn(Optional.of(ENTRY));
        var request=request("acme.example.com");request.setMethod("POST");request.addHeader("Origin","https://other.example.com");
        var response=new MockHttpServletResponse();var browser=new BrowserRequestFilter(new BrowserRequestProperties(Set.of()),ERRORS);
        filter(Set.of()).doFilter(request,response,(r,s) -> browser.doFilter(r,s,(a,b) -> fail("cross-tenant origin")));
        assertThat(response.getStatus()).isEqualTo(403);
        assertThatThrownBy(() -> filter(Set.of()).doFilter(request("acme.example.com"),new MockHttpServletResponse(),(r,s) -> {
            throw new java.io.IOException("downstream failure");
        })).isInstanceOf(java.io.IOException.class);
        assertThat(TenantEntryScope.optional()).isEmpty();assertThat(TenantEntryScope.isHttpRequest()).isFalse();
        assertThatThrownBy(() -> new TenantIngressProperties(Set.of("proxy.example.com"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> filter(Set.of()).setEnvironment(new org.springframework.mock.env.MockEnvironment()
                .withProperty("server.forward-headers-strategy","framework"))).isInstanceOf(IllegalStateException.class);
    }
}
