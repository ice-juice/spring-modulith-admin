package com.sma.admin.support;

import java.util.Set;
import java.util.function.Supplier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.sma.admin.common.security.*;

public final class Users {
    private Users() {}
    public static SmaPrincipal principal(long id, long tenant, DataAccess access) {
        return new SmaPrincipal(id, "tester-" + id, tenant, "admin", 10L, false, access, Set.of(), "UTC");
    }
    public static <T> T as(SmaPrincipal user, Supplier<T> action) {
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null,
                user.permissions().stream().map(SimpleGrantedAuthority::new).toList()));
        SecurityContextHolder.setContext(context);
        try { return action.get(); } finally { SecurityContextHolder.setContext(previous); }
    }
}
