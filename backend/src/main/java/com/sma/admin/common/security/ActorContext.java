package com.sma.admin.common.security;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolves only authenticated, recognized identities. Never falls back to tenant 1.
 * HTTP callers cannot supply ActorRef to gain identity. Trusted workers use withinJob
 * only after loading an owned, persistent work item, not from unverified callback fields.
 */
public final class ActorContext {
    private ActorContext() {}

    public static ActorRef require() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!authenticated(authentication)) throw denied();
        Object principal = authentication.getPrincipal();
        if (principal instanceof AppUserPrincipal user) {
            return new ActorRef(ActorRef.Kind.APP_USER, user.tenantId(), Long.toString(user.subjectId()),
                    user.realmId(), user.clientId());
        }
        if (principal instanceof SmaPrincipal user) {
            return new ActorRef(user.machine() ? ActorRef.Kind.APPLICATION : ActorRef.Kind.ADMIN_USER,
                    user.tenantId(), user.machine() ? user.username() : Long.toString(user.userId()), null, null);
        }
        if (principal instanceof JobPrincipal job) {
            return new ActorRef(ActorRef.Kind.SYSTEM_JOB, job.tenantId(), job.workId(), null, null);
        }
        throw denied();
    }

    public static long tenantId() { return require().tenantId(); }

    public static void requireTenant(long expectedTenant) {
        if (expectedTenant <= 0 || tenantId() != expectedTenant) throw denied();
    }

    public static boolean isAdminUser(Authentication authentication) {
        return authenticated(authentication) && authentication.getPrincipal() instanceof SmaPrincipal user
                && !user.machine();
    }

    public static boolean isApplicationUser(Authentication authentication, String audience) {
        return authenticated(authentication) && authentication.getPrincipal() instanceof AppUserPrincipal user
                && user.audience().equals(audience);
    }
    public static boolean isApplicationPrincipal(Authentication authentication) {
        return authenticated(authentication) && authentication.getPrincipal() instanceof AppUserPrincipal;
    }

    public static boolean isMachine(Authentication authentication) {
        return authenticated(authentication) && authentication.getPrincipal() instanceof SmaPrincipal user
                && user.machine();
    }

    public static AppUserPrincipal requireApplicationUser(String audience) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isApplicationUser(authentication, audience)) throw denied();
        return (AppUserPrincipal) authentication.getPrincipal();
    }

    /** Capture tenant/work identity from trusted persistence before scheduling; no inherited ThreadLocal. */
    public static <T> T withinJob(long tenantId, String workId, Supplier<T> action) {
        var ref = new ActorRef(ActorRef.Kind.SYSTEM_JOB, tenantId, workId, null, null);
        Objects.requireNonNull(action);
        var previous = SecurityContextHolder.getContext();
        var active = previous.getAuthentication();
        // Never let a logged-in caller turn this helper into a cross-tenant switch.
        if (authenticated(active)) requireTenant(tenantId);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new JobPrincipal(ref.tenantId(), ref.subject()), null, List.of()));
        SecurityContextHolder.setContext(context);
        try { return TenantScope.within(tenantId, action); }
        finally { SecurityContextHolder.setContext(previous); }
    }

    static boolean isJob(Authentication authentication) {
        return authenticated(authentication) && authentication.getPrincipal() instanceof JobPrincipal;
    }

    private record JobPrincipal(long tenantId, String workId) {}

    private static boolean authenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private static AccessDeniedException denied() { return new AccessDeniedException("Actor scope denied"); }
}
