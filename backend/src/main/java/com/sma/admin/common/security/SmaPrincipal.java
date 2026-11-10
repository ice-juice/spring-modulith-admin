package com.sma.admin.common.security;

import java.security.Principal;
import java.util.Objects;
import java.util.Set;

/** Immutable identity resolved by authentication. Never construct from request headers or body fields. */
public record SmaPrincipal(long userId, String username, long tenantId, String portal, Long departmentId,
        boolean superAdmin, DataAccess dataAccess, Set<String> permissions, String timeZone) implements Principal {
    public SmaPrincipal {
        if (userId <= 0 || tenantId <= 0) { throw new IllegalArgumentException("Identity ids must be positive"); }
        Objects.requireNonNull(username);
        Objects.requireNonNull(portal);
        Objects.requireNonNull(dataAccess);
        permissions = Set.copyOf(permissions);
    }
    @Override public String getName() { return username; }
}
