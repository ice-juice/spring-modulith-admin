package com.sma.admin.common.security;

import java.security.Principal;

/** A verified application session, never an administrator or a request-body DTO. */
public record AppUserPrincipal(long subjectId, long tenantId, long realmId, long clientId,
                               String audience, long sessionId) implements Principal {
    public AppUserPrincipal {
        if (subjectId <= 0 || tenantId <= 0 || realmId <= 0 || clientId <= 0 || sessionId <= 0) {
            throw new IllegalArgumentException("Application identity identifiers must be positive");
        }
        if (audience == null || !audience.matches("[a-z][a-z0-9.-]{0,63}")) {
            throw new IllegalArgumentException("Invalid application audience");
        }
    }

    @Override public String getName() { return "subject:" + subjectId; }
}
