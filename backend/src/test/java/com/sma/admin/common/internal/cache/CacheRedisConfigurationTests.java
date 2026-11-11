package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.service.CacheRevisionStore;

class CacheRedisConfigurationTests {
    @Test void localAndNoneDoNotInstantiateRedisConnectionsOrBindRedisSecrets() {
        var runner = new ApplicationContextRunner().withUserConfiguration(CacheRedisConfiguration.class);
        runner.withPropertyValues("sma.cache.provider=local", "spring.data.redis.port=invalid")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(RedisConnectionFactory.class));
        runner.withPropertyValues("sma.cache.provider=none")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(RedisConnectionFactory.class));
    }
    @Test void endpointListsCannotEmbedCredentialsPathsOrAmbiguousTopologies() {
        assertThatThrownBy(() -> new CacheRedisProperties.Cluster(List.of("user:secret@redis:6379"), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CacheRedisProperties.Cluster(List.of("redis:6379/path"), null)).isInstanceOf(IllegalArgumentException.class);
        var sentinel = new CacheRedisProperties.Sentinel("master", List.of("127.0.0.1:26379"), "u", "sentinel-secret");
        var cluster = new CacheRedisProperties.Cluster(List.of("127.0.0.1:6379"), null);
        assertThatThrownBy(() -> new CacheRedisProperties(null, null, null, null, null, null, null, null, sentinel, cluster)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CacheRedisProperties(null, null, 1, null, null, null, null, null, null, cluster)).isInstanceOf(IllegalArgumentException.class);
        assertThat(sentinel.toString()).doesNotContain("sentinel-secret");
    }
    @Test void credentialsAreRedactedAndTimeoutsCannotBeUnbounded() {
        var properties = new CacheRedisProperties(null, null, null, "private-user", "private-password", null, null, null, null, null);
        assertThat(properties.toString()).doesNotContain("private-user", "private-password");
        assertThatThrownBy(() -> new CacheRedisProperties(null, null, null, null, null, Duration.ZERO, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void sharedRedisRequiresAnExplicitServerMemoryCeiling() {
        assertThatThrownBy(() -> CacheRedisConfiguration.requireBoundedMemory(new java.util.Properties())).isInstanceOf(IllegalStateException.class);
        var memory = new java.util.Properties(); memory.setProperty("maxmemory", "67108864");
        assertThatCode(() -> CacheRedisConfiguration.requireBoundedMemory(memory)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new CacheProperties(CacheProperties.Provider.REDIS, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("deployment-specific");
    }

    @Test @EnabledIfEnvironmentVariable(named="SMA_CACHE_REDIS_TEST_PORT", matches="[1-9][0-9]{3,4}")
    void redisProviderIsActuallyAssembledFromConfigurationAndWritesSharedData() {
        String namespace = "p08binding-" + java.util.UUID.randomUUID();
        int port = Integer.parseInt(System.getenv("SMA_CACHE_REDIS_TEST_PORT"));
        if (port < 10000 || port > 65535) throw new IllegalArgumentException("Tests require an isolated high localhost port");
        new ApplicationContextRunner().withUserConfiguration(CacheConfiguration.class, CacheRedisConfiguration.class)
                .withBean(CacheRevisionStore.class, () -> {
                    var store=org.mockito.Mockito.mock(CacheRevisionStore.class);
                    org.mockito.Mockito.when(store.state(org.mockito.ArgumentMatchers.anyString())).thenReturn(CacheRevisionStore.State.initial());
                    return store;
                })
                .withBean(CacheCatalog.class, com.sma.admin.config.internal.service.ConfigCacheCatalog::new)
                .withPropertyValues("sma.cache.provider=redis", "sma.cache.namespace=" + namespace, "spring.data.redis.port=" + port)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ManagedCache.class).hasSingleBean(RedisConnectionFactory.class);
                    var cache = context.getBean(ManagedCache.class); var template = context.getBean(StringRedisTemplate.class);
                    var store = new RedisCacheStore(template, namespace);
                    try {
                        assertThat(cache.get("config.parameters", "key", String.class, () -> "bound")).isEqualTo("bound");
                        assertThat(cache.get("config.parameters", "key", String.class, () -> "wrong")).isEqualTo("bound");
                        assertThat(template.hasKey(store.epochKey("config.parameters", 1))).isTrue();
                    } finally { template.delete(store.epochKey("config.parameters", 1)); }
                });
    }
}
