package com.sma.admin.common.security;

import java.util.Objects;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Request-local scope set only after directory resolution; it never populates SecurityContextHolder. */
public final class TenantEntryScope {
    private static final ThreadLocal<Scope> ENTRY = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> HTTP = new ThreadLocal<>();
    private TenantEntryScope() {}

    public static Optional<TenantEntry> optional() { return Optional.ofNullable(ENTRY.get()).map(scope -> scope.entered); }
    public static TenantEntry require() { return optional().orElseThrow(() -> new AccessDeniedException("Tenant entry required")); }
    public static boolean isHttpRequest() { return Boolean.TRUE.equals(HTTP.get()); }

    /** Marks the directory-resolution phase, before an anonymous tenant is known. */
    public static RequestScope beginRequest() {
        if (isHttpRequest() || ENTRY.get()!=null) throw new IllegalStateException("Nested tenant request");
        HTTP.set(true); return new RequestScope(Thread.currentThread());
    }

    public static final class RequestScope implements AutoCloseable {
        private final Thread owner;
        private boolean closed;
        private RequestScope(Thread owner) { this.owner=owner; }
        @Override public void close() {
            if(closed) return;
            if(Thread.currentThread()!=owner) throw new IllegalStateException("Tenant request ownership mismatch");
            ENTRY.remove(); HTTP.remove(); closed=true;
        }
    }

    public static Scope open(TenantEntry entry) {
        Objects.requireNonNull(entry);
        var existing = ENTRY.get();
        if (existing != null && !existing.entered.equals(entry)) throw new AccessDeniedException("Cannot switch an active tenant entry");
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            ActorContext.requireTenant(entry.tenantId());
            if (authentication.getPrincipal() instanceof SmaPrincipal principal && !principal.portal().equals(entry.portal())) {
                throw new AccessDeniedException("Entry portal mismatch");
            }
        }
        var scope=new Scope(entry, existing, Thread.currentThread());
        ENTRY.set(scope);
        return scope;
    }

    public static final class Scope implements AutoCloseable {
        private final TenantEntry entered;
        private final Scope previous;
        private final Thread owner;
        private boolean closed;
        private Scope(TenantEntry entered, Scope previous, Thread owner) {
            this.entered=entered; this.previous=previous; this.owner=owner;
        }
        @Override public void close() {
            if (closed) return;
            if (Thread.currentThread()!=owner || ENTRY.get()!=this) throw new IllegalStateException("Tenant entry scope ownership mismatch");
            if (previous==null) ENTRY.remove(); else ENTRY.set(previous);
            closed=true;
        }
    }
}
