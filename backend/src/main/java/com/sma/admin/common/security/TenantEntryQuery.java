package com.sma.admin.common.security;

import java.util.Optional;

/** Host directory implemented by the tenant module. Values grant no user or platform permissions. */
public interface TenantEntryQuery {
    Optional<TenantEntry> resolve(String normalizedHost);
    Optional<SecurityState> securityState(long tenantId);

    record SecurityState(long tenantId, long generation, boolean acceptsLogin, boolean acceptsBusiness) {
        public SecurityState {
            if (tenantId <= 0 || generation < 1) throw new IllegalArgumentException("Invalid tenant security state");
        }
    }
}
