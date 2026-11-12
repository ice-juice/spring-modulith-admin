package com.sma.admin.tenant.internal.service;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deployment-owned boundary for destinations a platform operator may register. */
@ConfigurationProperties("sma.tenant.storage")
public record TenantStorageProperties(Set<String> allowedHosts, boolean allowInsecureLoopback) {
    public TenantStorageProperties {
        allowedHosts = allowedHosts == null ? Set.of() : Set.copyOf(allowedHosts);
        if (allowedHosts.size() > 64 || allowedHosts.stream().anyMatch(host -> host == null
                || !host.matches("[a-z0-9][a-z0-9.-]{0,252}") || host.contains("..")))
            throw new IllegalArgumentException("Invalid storage host allowlist");
    }
}
