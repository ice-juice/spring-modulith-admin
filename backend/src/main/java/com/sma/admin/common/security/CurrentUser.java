package com.sma.admin.common.security;

import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

/** Authentication-owned context. Anonymous HTTP scope comes only from a verified directory entry. */
public final class CurrentUser {
    private CurrentUser() {}
    public static Optional<SmaPrincipal> optional() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        if (!(auth.getPrincipal() instanceof SmaPrincipal principal)) {
            throw new AccessDeniedException("Unsupported authentication principal");
        }
        return Optional.of(principal);
    }
    public static SmaPrincipal require() { return optional().filter(principal->!principal.machine()).orElseThrow(() -> new AccessDeniedException("Human authentication required")); }
    public static long tenantId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() &&
                (auth.getPrincipal() instanceof AppUserPrincipal || ActorContext.isJob(auth))) {
            return checkedTenant(ActorContext.tenantId());
        }
        return checkedTenant(optional().map(SmaPrincipal::tenantId).orElseGet(TenantScope::current));
    }
    private static long checkedTenant(long tenant) {
        TenantEntryScope.optional().ifPresent(entry -> {
            if(entry.tenantId()!=tenant) throw new AccessDeniedException("Identity and entry tenant mismatch");
        });
        return tenant;
    }
    public static long userId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        // created_by is an IAM identifier. Application ownership is stored separately as ActorRef.
        if (auth != null && auth.isAuthenticated() &&
                (auth.getPrincipal() instanceof AppUserPrincipal || ActorContext.isJob(auth))) return 0L;
        return optional().map(SmaPrincipal::userId).orElse(0L);
    }
    public static String portal() { return optional().map(SmaPrincipal::portal).orElseGet(() -> TenantEntryScope.optional().map(TenantEntry::portal).orElse("admin")); }
}
