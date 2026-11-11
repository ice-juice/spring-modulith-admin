package com.sma.admin.common.security;

import java.util.Objects;

/** Stable audit/ownership reference. An instance is data, not proof of authentication. */
public record ActorRef(Kind kind, long tenantId, String subject, Long realmId, Long clientId) {
    public enum Kind { ADMIN_USER, APP_USER, APPLICATION, SYSTEM_JOB }

    public ActorRef {
        Objects.requireNonNull(kind, "Actor kind is required");
        if (tenantId <= 0 || subject == null || subject.isBlank() || subject.length() > 128) {
            throw new IllegalArgumentException("Invalid actor reference");
        }
        if (kind == Kind.APP_USER) {
            if (realmId == null || realmId <= 0 || clientId == null || clientId <= 0) {
                throw new IllegalArgumentException("Application user requires realm and client");
            }
        } else if (realmId != null || clientId != null) {
            throw new IllegalArgumentException("Only application users carry realm and client");
        }
    }
}
