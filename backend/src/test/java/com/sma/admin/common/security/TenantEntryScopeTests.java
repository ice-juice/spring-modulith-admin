package com.sma.admin.common.security;

import static org.assertj.core.api.Assertions.*;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.support.Users;

class TenantEntryScopeTests {
    private static final TenantEntry A=new TenantEntry(72,100,"a.example.com","admin",1);
    private static final TenantEntry B=new TenantEntry(73,101,"b.example.com","admin",2);

    @Test void normalizesRegisteredDomainsButRejectsUrlsAndAmbiguousAuthorities() {
        assertThat(TenantHost.authority("Acme.Example.COM.:443")).isEqualTo("acme.example.com");
        assertThat(TenantHost.domain("例子.测试")).isEqualTo("xn--fsqu00a.xn--0zwm56d");
        assertThat(TenantHost.authority("[::1]:8080")).isEqualTo("[::1]");
        for (String value : new String[]{"https://a.example.com","a.example.com/path","evil@a.example.com","a.example.com,evil",
                "a.example.com\r\nX:bad"," a.example.com","*.example.com","a..com","a.example.com..","a.com:0",
                "a.com:65536","a.com:443:80","a.com:","a.com#x","127.1","127.0.0.999","127.000.0.1","[::1]bad","[::2]"}) {
            assertThatThrownBy(() -> TenantHost.authority(value)).as(value).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void anonymousEntryProvidesNoIdentityAndClearsEvenOnFailure() {
        assertThat(TenantEntryScope.optional()).isEmpty();
        assertThatThrownBy(() -> {
            try (var scope=TenantEntryScope.open(A)) {
                assertThat(TenantEntryScope.require()).isEqualTo(A);
                assertThatThrownBy(ActorContext::require).isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(CurrentUser::require).isInstanceOf(AccessDeniedException.class);
                throw new IllegalStateException("request failed");
            }
        }).hasMessage("request failed");
        assertThat(TenantEntryScope.optional()).isEmpty();
    }

    @Test void scopeCannotChangeTenantOrPortalUnderAnAuthenticatedIdentity() {
        Users.as(Users.principal(99,72,DataAccess.unrestricted()), () -> {
            assertThatThrownBy(() -> TenantEntryScope.open(B)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> TenantEntryScope.open(new TenantEntry(72,102,"a.example.com","other",1)))
                    .isInstanceOf(AccessDeniedException.class);
            try (var scope=TenantEntryScope.open(A)) {
                assertThatThrownBy(() -> TenantEntryScope.open(B)).isInstanceOf(AccessDeniedException.class);
                try (var nested=TenantEntryScope.open(A)) { assertThat(TenantEntryScope.require()).isEqualTo(A); }
                assertThat(TenantEntryScope.require()).isEqualTo(A);
            }
            return null;
        });
        assertThat(TenantEntryScope.optional()).isEmpty();
    }

    @Test void asynchronousWorkersDoNotInheritHttpEntry() throws Exception {
        try (var executor=Executors.newSingleThreadExecutor(); var scope=TenantEntryScope.open(A)) {
            assertThat(executor.submit(() -> TenantEntryScope.optional()).get()).isEmpty();
            assertThat(TenantEntryScope.require()).isEqualTo(A);
        }
    }

    @Test void nestedScopesMustBeClosedInOrderWithoutLeakingOrClearingTheWrongRequest() {
        var outer=TenantEntryScope.open(A);
        var inner=TenantEntryScope.open(A);
        assertThatThrownBy(outer::close).isInstanceOf(IllegalStateException.class);
        assertThat(TenantEntryScope.require()).isEqualTo(A);
        inner.close(); outer.close(); outer.close();
        assertThat(TenantEntryScope.optional()).isEmpty();
    }

    @Test void deferredAnonymousHttpWorkCannotFallBackToTenantOneAfterFilterScopeEnded() {
        var request=new org.springframework.mock.web.MockHttpServletRequest("GET","/api/tenant/entry");
        var previous=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
        try { assertThatThrownBy(CurrentUser::tenantId).isInstanceOf(AccessDeniedException.class); }
        finally { org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(previous); }
    }
}
