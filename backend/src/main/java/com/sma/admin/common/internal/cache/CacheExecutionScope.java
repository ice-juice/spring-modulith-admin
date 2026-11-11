package com.sma.admin.common.internal.cache;

import java.util.function.Supplier;
import com.sma.admin.common.security.TenantScope;
import org.springframework.security.core.context.SecurityContextHolder;

/** A scope captured by the cache facade, never an HTTP tenant parameter. Restore the caller after callbacks. */
final class CacheExecutionScope {
    private CacheExecutionScope() {}
    static <T> T within(long tenant, Supplier<T> action) {
        var previous = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
        try { return TenantScope.within(tenant, action); }
        finally { SecurityContextHolder.setContext(previous); }
    }
}
