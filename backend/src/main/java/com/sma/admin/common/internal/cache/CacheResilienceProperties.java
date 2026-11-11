package com.sma.admin.common.internal.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sma.cache.resilience")
public record CacheResilienceProperties(Integer maxConcurrentLoads, Integer maxConcurrentPerRegion,
        Duration lockWait, Integer failureThreshold, Duration retryDelay) {
    public CacheResilienceProperties {
        maxConcurrentLoads = maxConcurrentLoads == null ? 32 : maxConcurrentLoads;
        maxConcurrentPerRegion = maxConcurrentPerRegion == null ? 8 : maxConcurrentPerRegion;
        lockWait = lockWait == null ? Duration.ofMillis(100) : lockWait;
        failureThreshold = failureThreshold == null ? 3 : failureThreshold;
        retryDelay = retryDelay == null ? Duration.ofSeconds(1) : retryDelay;
        if (maxConcurrentLoads < 1 || maxConcurrentLoads > 4096 || maxConcurrentPerRegion < 1
                || maxConcurrentPerRegion > maxConcurrentLoads || lockWait.isNegative() || lockWait.compareTo(Duration.ofSeconds(1)) > 0
                || failureThreshold < 1 || failureThreshold > 20 || retryDelay.compareTo(Duration.ofMillis(10)) < 0
                || retryDelay.compareTo(Duration.ofSeconds(60)) > 0) throw new IllegalArgumentException("Invalid cache resilience bounds");
    }
    static CacheResilienceProperties defaults() { return new CacheResilienceProperties(null, null, null, null, null); }
}
