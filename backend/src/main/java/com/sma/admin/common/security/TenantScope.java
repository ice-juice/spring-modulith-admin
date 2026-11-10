package com.sma.admin.common.security;

import java.util.function.Supplier;

/** Explicit context for trusted background events; HTTP tenancy always comes from authentication. */
public final class TenantScope {
    private static final ThreadLocal<Long> TENANT = new ThreadLocal<>();
    private TenantScope() {}
    public static long current() { return TENANT.get() == null ? 1L : TENANT.get(); }
    public static <T> T within(long tenantId, Supplier<T> action) {
        if (tenantId <= 0) throw new IllegalArgumentException("Tenant must be positive");
        Long previous = TENANT.get();
        TENANT.set(tenantId);
        try { return action.get(); }
        finally { if (previous == null) TENANT.remove(); else TENANT.set(previous); }
    }
}
