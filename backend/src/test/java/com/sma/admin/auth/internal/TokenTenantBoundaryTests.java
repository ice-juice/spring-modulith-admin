package com.sma.admin.auth.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.sma.admin.auth.internal.service.SessionService;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;

class TokenTenantBoundaryTests {
    @Test void retiredParameterPathNoLongerBypassesBearerValidation() throws Exception {
        var sessions=mock(SessionService.class);
        when(sessions.resolve("expired")).thenReturn(java.util.Optional.empty());
        var filter=new TokenAuthenticationFilter(sessions,(request,response,error)->response.setStatus(error.status()));
        try(var scope=TenantEntryScope.open(new TenantEntry(72,100,"a.example.com","admin",1))) {
            var request=new MockHttpServletRequest("GET","/api/config/parameters/public");request.addHeader("Authorization","Bearer expired");
            var response=new MockHttpServletResponse();
            filter.doFilter(request,response,(r,s)->fail("Retired public route bypassed authentication"));
            assertThat(response.getStatus()).isEqualTo(401);verify(sessions).resolve("expired");
        }
    }

    @Test void tenantPublicWhitelistAllowsOnlyExactReadsAndIgnoresStaleBearer() throws Exception {
        var sessions=mock(SessionService.class);
        var filter=new TokenAuthenticationFilter(sessions,(request,response,error)->response.setStatus(error.status()));
        try(var scope=TenantEntryScope.open(new TenantEntry(72,100,"a.example.com","admin",1))) {
            for(String path:java.util.List.of("/api/tenant/entry","/api/tenant/brand-images/123/LOGO_LIGHT")) {
                for(String method:java.util.List.of("GET","HEAD")) {
                    var request=new MockHttpServletRequest(method,path);request.addHeader("Authorization","Bearer old");
                    assertThat(TokenAuthenticationFilter.publicTenantRead(request)).isTrue();
                    filter.doFilter(request,new MockHttpServletResponse(),(r,s)->assertThat(CurrentUser.tenantId()).isEqualTo(72));
                }
                assertThat(TokenAuthenticationFilter.publicTenantRead(new MockHttpServletRequest("POST",path))).isFalse();
            }
            for(String path:java.util.List.of("/api/tenant/brands/draft","/api/tenant/brand-images/123/LOGO_LIGHT/extra","/api/tenant/entry/extra"))
                assertThat(TokenAuthenticationFilter.publicTenantRead(new MockHttpServletRequest("GET",path))).isFalse();
        }
        verifyNoInteractions(sessions);
    }

    @Test void identitiesEstablishedAfterHostResolutionAreRejectedBeforeAnyMapperOrController() throws Exception {
        var sessions=mock(SessionService.class);
        var filter=new TokenAuthenticationFilter(sessions,(request,response,error)->response.setStatus(error.status()));
        try(var entry=TenantEntryScope.open(new TenantEntry(72,100,"a.example.com","admin",1))) {
            Users.as(Users.principal(7,73,DataAccess.unrestricted()),()->{
                var response=new MockHttpServletResponse();
                try { filter.doFilter(new MockHttpServletRequest("GET","/api/mail/profiles"),response,(r,s)->fail("foreign identity reached controller")); }
                catch(Exception failure) { throw new AssertionError(failure); }
                assertThat(response.getStatus()).isEqualTo(403);return null;
            });
            Users.as(Users.principal(7,72,DataAccess.unrestricted()),()->{
                var response=new MockHttpServletResponse();
                try { filter.doFilter(new MockHttpServletRequest("GET","/api/mail/profiles"),response,(r,s)->s.getWriter().write("ok"));
                    assertThat(response.getContentAsString()).isEqualTo("ok"); }
                catch(Exception failure) { throw new AssertionError(failure); }
                return null;
            });
        }
        verifyNoInteractions(sessions);
    }
    @Test void anonymousLoginIgnoresStaleBearerWithoutLosingVerifiedEntry() throws Exception {
        var sessions=mock(SessionService.class);
        var filter=new TokenAuthenticationFilter(sessions,(request,response,error)->response.setStatus(error.status()));
        try(var scope=TenantEntryScope.open(new TenantEntry(72,100,"a.example.com","admin",1))) {
            var request=new MockHttpServletRequest("POST","/api/auth/login");request.addHeader("Authorization","Bearer old");
            filter.doFilter(request,new MockHttpServletResponse(),(r,s)->assertThat(CurrentUser.tenantId()).isEqualTo(72));
        }
        verifyNoInteractions(sessions);
    }
}
