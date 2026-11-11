package com.sma.admin.common.security;

import java.util.Set;

/** Immutable configuration identity. Load ownership from persistence; never trust a caller-created reference. */
public record TenantConfigurationRef(long tenantId, String module, long configurationId,
                                     long configurationVersion, long credentialVersion) {
    public TenantConfigurationRef {
        if (tenantId <= 0 || configurationId <= 0 || configurationVersion <= 0 || credentialVersion <= 0
                || module == null || !Set.of("identity", "mail", "file").contains(module)) {
            throw new IllegalArgumentException("Invalid tenant configuration reference");
        }
    }

    public void requireCurrentTenant() { ActorContext.requireTenant(tenantId); }

    /** Safe metadata key; does not contain secret values. Add app/realm when caching an app-specific projection. */
    public String partitionKey() {
        return tenantId + ":" + module + ":" + configurationId + ":" + configurationVersion + ":" + credentialVersion;
    }
}
