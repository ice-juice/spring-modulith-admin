package com.sma.admin.common.security;

/** Verified directory snapshot. This is an anonymous entry scope, never an authenticated principal. */
public record TenantEntry(long tenantId, long domainId, String host, String portal, long securityGeneration) {
    public TenantEntry {
        if (tenantId <= 0 || domainId <= 0 || securityGeneration < 1 || host == null
                || !TenantHost.authority(host).equals(host) || portal == null || !portal.matches("[a-z][a-z0-9-]{0,31}")) {
            throw new IllegalArgumentException("Invalid tenant entry");
        }
    }
}
