package com.sma.admin.common.internal.cache;

import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.sma.admin.common.cache.CacheCatalog;
import com.sma.admin.common.cache.ManagedCache;
import com.sma.admin.common.internal.service.CacheRevisionStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({CacheProperties.class, CacheResilienceProperties.class})
class CacheConfiguration {
    @Bean CacheRegistrationOwnership cacheRegistrationOwnership(){return new CacheRegistrationOwnership();}
    @Bean CacheBackendHealth governedCacheHealthIndicator(ManagedCache cache, CacheProperties properties,
            ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meters) {
        var health = new CacheBackendHealth(cache, properties); meters.ifAvailable(health::bind); return health;
    }
    @Bean CacheRegistry cacheRegistry(List<CacheCatalog> catalogs,CacheRegistrationOwnership ownership) { return new CacheRegistry(catalogs,ownership); }
    @Bean ManagedCache managedCache(CacheRegistry registry, CacheProperties properties, CacheRevisionStore revisions, CacheResilienceProperties resilience,
            ObjectProvider<StringRedisTemplate> redisTemplate) {
        var clock = Clock.systemUTC();
        if (properties.deploymentMode() != CacheProperties.DeploymentMode.SINGLE)
            throw new IllegalArgumentException("Multi-instance application deployment requires the full P08 cluster acceptance gate");
        if (properties.provider() == CacheProperties.Provider.REDIS) {
            var redis = new RedisManagedCache(registry, properties, resilience, new RedisCacheStore(redisTemplate.getObject(), properties.namespace()));
            return new DurableManagedCache(redis, redis, revisions, registry, properties, clock, resilience);
        }
        var local = new LocalManagedCache(registry, properties, clock, resilience);
        return new DurableManagedCache(local, local, revisions, registry, properties, clock, resilience);
    }
}
