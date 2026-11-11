package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.admin.common.cache.ManagedCache;
import com.sma.admin.common.security.TenantScope;
import com.sma.admin.config.internal.service.ParameterService;
import com.sma.admin.monitor.internal.service.CacheService;
import com.sma.admin.support.IntegrationTest;

@IntegrationTest @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named="SMA_CACHE_REDIS_TEST_PORT", matches="[1-9][0-9]{3,4}")
class RedisProviderIntegrationTests {
    private static final String NAMESPACE = "p08app-" + UUID.randomUUID();
    @DynamicPropertySource static void redis(DynamicPropertyRegistry properties) {
        int port = Integer.parseInt(System.getenv("SMA_CACHE_REDIS_TEST_PORT"));
        if (port < 10000 || port > 65535) throw new IllegalArgumentException("Require a dedicated high localhost port");
        properties.add("sma.cache.provider", () -> "redis"); properties.add("sma.cache.namespace", () -> NAMESPACE);
        properties.add("spring.data.redis.host", () -> "127.0.0.1"); properties.add("spring.data.redis.port", () -> port);
        properties.add("spring.data.redis.timeout", () -> "500ms");
    }
    @Autowired ParameterService parameters;
    @Autowired ManagedCache cache;
    @Autowired CacheService management;
    @Autowired StringRedisTemplate template;
    @Autowired PlatformTransactionManager transactions;
    @Autowired CacheBackendHealth health;

    @Test void fullApplicationUsesRedisWithoutChangingParameterOrLegacyManagementContracts() {
        TenantScope.within(7401, () -> {
            parameters.saveGroup(null, new ParameterService.GroupInput("cachetest", "Cache test", null, 0, Map.of()));
            var row = parameters.save(null, input("original"));
            assertThat(parameters.stringValue("cachetest.redis", "missing")).isEqualTo("original");
            assertThat(cache.snapshot("config.parameters").provider()).isEqualTo("redis");
            assertThat(cache.snapshot("config.parameters").size()).isEqualTo(1);
            assertThat(management.list()).anySatisfy(view -> { assertThat(view.id()).isEqualTo("config.parameters"); assertThat(view.size()).isEqualTo(1); });
            new TransactionTemplate(transactions).execute(status -> {
                parameters.save(row.id(), input("rolled-back"));
                assertThat(parameters.stringValue("cachetest.redis", "missing")).isEqualTo("rolled-back");
                status.setRollbackOnly(); return null;
            });
            assertThat(parameters.stringValue("cachetest.redis", "missing")).isEqualTo("original");
            parameters.save(row.id(), input("committed"));
            assertThat(parameters.stringValue("cachetest.redis", "missing")).isEqualTo("committed");
            com.sma.admin.support.Users.as(new com.sma.admin.common.security.SmaPrincipal(101,"cache-manager",7401,"admin",null,false,
                    com.sma.admin.common.security.DataAccess.unrestricted(),Set.of("monitor:cache:clear","monitor:cache:view"),"UTC"),()->{
                management.clear("config.parameters");return null;
            });
            assertThat(cache.snapshot("config.parameters").size()).isZero();
            assertThat(health.health().getDetails()).containsEntry("provider", "redis").containsEntry("state", "AVAILABLE");
            return null;
        });
    }
    @AfterEach void removeOnlyThisContextsKeys() {
        ((DurableManagedCache) cache).close(); // Stop this test context's reconciliation before exact-key cleanup.
        var store = new RedisCacheStore(template, NAMESPACE);
        template.delete(List.of(store.epochKey("config.parameters", 1), store.epochKey("config.parameters", 7401)));
    }
    private ParameterService.Input input(String value) {
        return new ParameterService.Input("cachetest.redis", "cachetest", "Redis app", null, "STRING", value, false, false, 0, Map.of());
    }
}
