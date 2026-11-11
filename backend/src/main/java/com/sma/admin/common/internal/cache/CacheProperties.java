package com.sma.admin.common.internal.cache;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import com.sma.admin.common.cache.CachePolicy;

@ConfigurationProperties("sma.cache")
public record CacheProperties(Provider provider, String namespace, DeploymentMode deploymentMode,
        Long maxBytes, Integer maxScopes, Double ttlJitter, Map<String, RegionOverride> regions, RegionOverride defaults) {
    public enum Provider { LOCAL, NONE, REDIS }
    public enum DeploymentMode { SINGLE, MULTI }
    public record RegionOverride(Duration ttl, Duration negativeTtl, Long maxEntries, Long maxBytes, Integer maxEntryBytes) {
        CachePolicy apply(CachePolicy base) {
            return apply(base, base);
        }
        CachePolicy apply(CachePolicy fallback, CachePolicy base) {
            var result = new CachePolicy(ttl == null ? fallback.ttl() : ttl,
                    negativeTtl == null ? fallback.negativeTtl() : negativeTtl,
                    maxEntries == null ? fallback.maxEntries() : maxEntries,
                    maxBytes == null ? fallback.maxBytes() : maxBytes,
                    maxEntryBytes == null ? fallback.maxEntryBytes() : maxEntryBytes);
            if (result.ttl().compareTo(base.ttl()) > 0 || result.negativeTtl().compareTo(base.negativeTtl()) > 0
                    || result.maxEntries() > base.maxEntries() || result.maxBytes() > base.maxBytes()
                    || result.maxEntryBytes() > base.maxEntryBytes()) {
                throw new IllegalArgumentException("Cache overrides cannot exceed registered policy bounds");
            }
            return result;
        }
    }

    public CacheProperties(Provider provider, String namespace, DeploymentMode deploymentMode, Long maxBytes,
            Integer maxScopes, Double ttlJitter, Map<String, RegionOverride> regions) {
        this(provider, namespace, deploymentMode, maxBytes, maxScopes, ttlJitter, regions, null);
    }
    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public CacheProperties {
        provider = provider == null ? Provider.LOCAL : provider;
        namespace = namespace == null ? "sma" : namespace;
        deploymentMode = deploymentMode == null ? DeploymentMode.SINGLE : deploymentMode;
        maxBytes = maxBytes == null ? 64L * 1024 * 1024 : maxBytes;
        maxScopes = maxScopes == null ? 10_000 : maxScopes;
        ttlJitter = ttlJitter == null ? 0.1 : ttlJitter;
        regions = regions == null ? Map.of() : Map.copyOf(regions);
        if (provider == Provider.REDIS && "sma".equals(namespace))
            throw new IllegalArgumentException("Redis requires a deployment-specific sma.cache.namespace");
        if (!namespace.matches("[a-zA-Z0-9_-]{1,64}") || maxBytes < 1024 || maxBytes > 1024L * 1024 * 1024
                || maxScopes < 1 || maxScopes > 100_000 || !Double.isFinite(ttlJitter) || ttlJitter < 0 || ttlJitter > 0.5) {
            throw new IllegalArgumentException("Invalid cache configuration");
        }
    }

    CachePolicy policy(String region, CachePolicy base) {
        var fallback = defaults == null ? base : defaults.apply(base);
        var override = regions.get(region);
        return override == null ? fallback : override.apply(fallback, base);
    }
}
