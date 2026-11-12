package com.sma.admin.tenant.internal.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Per-node hard ceilings; individual resource poolMax is additionally bounded by the directory. */
@ConfigurationProperties("sma.tenant.storage.pool")
public record StoragePoolProperties(Integer maxPools, Integer maxConnections,
                                    Integer maxConnectionsPerHost, Duration idleTtl) {
    public StoragePoolProperties {
        maxPools = maxPools == null ? 8 : maxPools;
        maxConnections = maxConnections == null ? 32 : maxConnections;
        maxConnectionsPerHost = maxConnectionsPerHost == null ? 16 : maxConnectionsPerHost;
        idleTtl = idleTtl == null ? Duration.ofMinutes(5) : idleTtl;
        if (maxPools < 1 || maxPools > 32 || maxConnections < 1 || maxConnections > 128
                || maxConnectionsPerHost < 1 || maxConnectionsPerHost > maxConnections
                || idleTtl.compareTo(Duration.ofSeconds(30)) < 0 || idleTtl.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("Invalid business storage pool budget");
    }
}
