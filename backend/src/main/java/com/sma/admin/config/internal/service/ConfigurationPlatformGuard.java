package com.sma.admin.config.internal.service;

import java.util.List;
import org.springframework.stereotype.Component;
import com.sma.admin.config.ConfigurationPlatformAccess;

/** Exactly one reviewed host authority; missing or ambiguous adapters fail at startup. */
@Component
public class ConfigurationPlatformGuard {
    private final ConfigurationPlatformAccess access;
    public ConfigurationPlatformGuard(List<ConfigurationPlatformAccess> providers) {
        if (providers.size() != 1) throw new IllegalStateException("Shared configuration requires exactly one host authority");
        this.access = providers.getFirst();
    }
    public boolean isPlatformTenant() { return access.isPlatformTenant(); }
    public void require(String permission) { access.requireOperator(permission); }
}
