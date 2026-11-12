package com.sma.admin.tenant.internal.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.common.security.PlatformTenantQuery;

@Component
@EnableConfigurationProperties(TenantPlatformProperties.class)
public class PlatformOperators implements PlatformTenantQuery {
    private final TenantPlatformProperties properties;
    public PlatformOperators(TenantPlatformProperties properties) { this.properties=properties; }
    @Override public long platformTenantId() { return properties.platformTenantId(); }
    /** Use in addition to the operation's @RequiresPermission, never as an alternative authorization path. */
    public void require() {
        var user=CurrentUser.require();
        if (user.tenantId()!=properties.platformTenantId() || !user.portal().equals("admin")
                || !properties.platformAdminUserIds().contains(user.userId())) {
            throw new AccessDeniedException("Platform operator required");
        }
    }
}
