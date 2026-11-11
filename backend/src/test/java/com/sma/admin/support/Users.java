package com.sma.admin.support;

import java.util.Set;
import java.util.function.Supplier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.sma.admin.common.security.*;

public final class Users {
    private Users() {}
    private static final ThreadLocal<java.util.function.Consumer<SmaPrincipal>> TENANT_FIXTURES = new ThreadLocal<>();
    static void tenantFixtures(java.util.function.Consumer<SmaPrincipal> fixture) {
        if(fixture==null) TENANT_FIXTURES.remove(); else TENANT_FIXTURES.set(fixture);
    }
    /** Alternative to .with(authentication(...)): prepares the actor's real test domain and authenticated context. */
    public static org.springframework.test.web.servlet.request.RequestPostProcessor request(SmaPrincipal user) {
        var fixture=TENANT_FIXTURES.get();
        if(fixture==null) throw new IllegalStateException("Tenant HTTP fixtures require an integration test context");
        fixture.accept(user);
        var authentication=UsernamePasswordAuthenticationToken.authenticated(user,null,
                user.permissions().stream().map(SimpleGrantedAuthority::new).toList());
        return request -> {
            if(request.getHeader("Host")==null) {
                request.addHeader("Host",TenantTestFixtures.host(user));request.setServerName(TenantTestFixtures.host(user));
            }
            return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(authentication).postProcessRequest(request);
        };
    }
    public static SmaPrincipal principal(long id, long tenant, DataAccess access) {
        return new SmaPrincipal(id, "tester-" + id, tenant, "admin", 10L, false, access, Set.of(), "UTC");
    }
    public static <T> T as(SmaPrincipal user, Supplier<T> action) {
        var fixture=TENANT_FIXTURES.get(); if(fixture!=null) fixture.accept(user);
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null,
                user.permissions().stream().map(SimpleGrantedAuthority::new).toList()));
        SecurityContextHolder.setContext(context);
        org.springframework.security.test.context.TestSecurityContextHolder.setContext(context);
        try { return action.get(); }
        finally { org.springframework.security.test.context.TestSecurityContextHolder.setContext(previous); }
    }
}
