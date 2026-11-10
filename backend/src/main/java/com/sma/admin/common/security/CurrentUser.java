package com.sma.admin.common.security;

import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

/** Authentication-owned context. Background/bootstrap work without authentication uses the default tenant (1). */
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
    public static SmaPrincipal require() { return optional().orElseThrow(() -> new AccessDeniedException("Authentication required")); }
    public static long tenantId() { return optional().map(SmaPrincipal::tenantId).orElse(1L); }
    public static long userId() { return optional().map(SmaPrincipal::userId).orElse(0L); }
    public static String portal() { return optional().map(SmaPrincipal::portal).orElse("admin"); }
}
