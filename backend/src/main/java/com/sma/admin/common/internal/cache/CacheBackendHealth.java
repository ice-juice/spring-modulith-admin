package com.sma.admin.common.internal.cache;

import com.sma.admin.common.cache.ManagedCache;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/** Optional cache failure is visible as BYPASS; database/readiness contributors decide application availability. */
final class CacheBackendHealth implements HealthIndicator {
    private final DurableManagedCache cache;
    private final String provider;
    CacheBackendHealth(ManagedCache cache, CacheProperties properties) {
        this.cache = (DurableManagedCache) cache;
        provider = properties.provider().name().toLowerCase(java.util.Locale.ROOT);
    }
    @Override public Health health() {
        var state = cache.runtime();
        return Health.up().withDetail("provider", provider).withDetail("state", state.state()).withDetail("circuit", state.circuit())
                .withDetail("sourceRejections", state.sourceRejections()).withDetail("backendErrors", state.backendErrors())
                .withDetail("invalidationFailures", state.invalidationFailures()).build();
    }
    void bind(MeterRegistry meters) {
        Gauge.builder("sma.cache.available", cache, value -> "BYPASS".equals(value.runtime().state()) ? 0 : 1).tag("provider", provider).register(meters);
        Gauge.builder("sma.cache.invalidation.failures", cache, value -> value.runtime().invalidationFailures()).tag("provider", provider).register(meters);
        FunctionCounter.builder("sma.cache.source.rejections", cache, value -> value.runtime().sourceRejections()).tag("provider", provider).register(meters);
        FunctionCounter.builder("sma.cache.backend.errors", cache, value -> value.runtime().backendErrors()).tag("provider", provider).register(meters);
    }
}
