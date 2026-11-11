package com.sma.admin.tenant.internal.service;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import com.sma.admin.common.security.TenantHost;

/** Deployment owns platform operators. A tenant administrator cannot grant these identifiers through IAM. */
@ConfigurationProperties("sma.tenant")
public record TenantPlatformProperties(Long platformTenantId, Set<Long> platformAdminUserIds, Set<String> bootstrapHosts) {
    public TenantPlatformProperties {
        platformTenantId = platformTenantId == null ? 1L : platformTenantId;
        platformAdminUserIds = platformAdminUserIds == null ? Set.of() : Set.copyOf(platformAdminUserIds);
        bootstrapHosts = bootstrapHosts == null ? Set.of() : Set.copyOf(bootstrapHosts);
        if (platformTenantId <= 0 || platformAdminUserIds.size() > 100 || platformAdminUserIds.stream().anyMatch(id -> id <= 0)
                || bootstrapHosts.size() > 20) throw new IllegalArgumentException("Invalid tenant platform configuration");
        for (var host : bootstrapHosts) if (!TenantHost.authority(host).equals(host)) throw new IllegalArgumentException("Bootstrap hosts must be normalized and omit ports");
    }
}
