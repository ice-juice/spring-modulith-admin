package com.sma.admin.tenant.internal.service;

import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.config.ConfigurationPlatformAccess;

@Component
public class TenantConfigurationPlatformAccess implements ConfigurationPlatformAccess {
    private final PlatformOperators operators;
    private final TenantPlatformProperties properties;
    public TenantConfigurationPlatformAccess(PlatformOperators operators, TenantPlatformProperties properties) {
        this.operators = operators; this.properties = properties;
    }
    @Override public boolean isPlatformTenant() { return CurrentUser.tenantId() == properties.platformTenantId(); }
    @Override public void requireOperator(String permission) {
        operators.require();
        if (!Set.of("config:shared:view", "config:shared:edit", "config:shared:publish").contains(permission)
                || !CurrentUser.require().permissions().contains(permission)) throw new AccessDeniedException("Shared configuration operation denied");
    }
}
