package com.sma.admin.common.security;

import java.util.function.Supplier;

/** Explicit trusted background scope; anonymous HTTP tenancy comes only from a resolved directory entry. */
public final class TenantScope {
    private static final ThreadLocal<Long> TENANT = new ThreadLocal<>();
    private TenantScope() {}
    public static long current() {
        var entry=TenantEntryScope.optional();
        if(entry.isPresent()) return entry.get().tenantId();
        if(TenantEntryScope.isHttpRequest()) throw new org.springframework.security.access.AccessDeniedException("HTTP tenant entry required");
        if(TENANT.get()!=null) return TENANT.get();
        if(org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
                instanceof org.springframework.web.context.request.ServletRequestAttributes attributes) {
            var request=attributes.getRequest();
            if(request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/"))
                throw new org.springframework.security.access.AccessDeniedException("Asynchronous HTTP work requires an explicit trusted tenant");
        }
        return 1L;
    }
    public static <T> T within(long tenantId, Supplier<T> action) {
        if (tenantId <= 0) throw new IllegalArgumentException("Tenant must be positive");
        TenantEntryScope.optional().ifPresent(entry -> {
            if(entry.tenantId()!=tenantId) throw new org.springframework.security.access.AccessDeniedException("Cannot switch HTTP tenant");
        });
        Long previous = TENANT.get();
        TENANT.set(tenantId);
        try { return action.get(); }
        finally { if (previous == null) TENANT.remove(); else TENANT.set(previous); }
    }
}
