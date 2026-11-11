package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.internal.service.CacheRevisionStore;
import com.sma.admin.common.security.TenantScope;
import com.sma.admin.config.internal.persistence.*;
import com.sma.admin.config.internal.service.ParameterService;
import com.sma.admin.support.IntegrationTest;

/** Actual MySQL transaction versions driving two independent Redis clients/observers; not an LB acceptance test. */
@IntegrationTest
@EnabledIfEnvironmentVariable(named="SMA_CACHE_REDIS_TEST_PORT", matches="[1-9][0-9]{3,4}")
class RedisDurableRevisionIntegrationTests {
    private static final String REGION = "config.parameters";
    @Autowired CacheRevisionStore revisions;
    @Autowired CacheRegistry registry;
    @Autowired ParameterService parameters;
    @Autowired ParameterMapper parametersMapper;
    @Autowired PlatformTransactionManager transactions;
    private final MutableClock clock = new MutableClock();
    private final List<LettuceConnectionFactory> factories = new ArrayList<>();
    private final Set<String> cleanup = new HashSet<>();
    private StringRedisTemplate template;
    private RedisCacheStore storeA, storeB;

    @BeforeEach void connect() {
        int port = Integer.parseInt(System.getenv("SMA_CACHE_REDIS_TEST_PORT"));
        if (port < 10000 || port > 65535) throw new IllegalArgumentException("Tests require an isolated high localhost port");
        String namespace = "p08durable-" + UUID.randomUUID();
        template = new StringRedisTemplate(factory(port));
        storeA = new RedisCacheStore(template, namespace);
        storeB = new RedisCacheStore(new StringRedisTemplate(factory(port)), namespace);
    }
    @AfterEach void close() {
        try { if (template != null && !cleanup.isEmpty()) template.delete(cleanup); }
        finally { factories.forEach(LettuceConnectionFactory::destroy); }
    }

    @Test void committedDataInvalidatesSharedRedisEvenWhenOnlyTheOtherNodePolls() {
        TenantScope.within(7351, () -> {
            long id = source();
            var a = node(storeA, new AtomicBoolean()); var b = node(storeB, new AtomicBoolean());
            assertThat(read(a)).isEqualTo("old"); assertThat(read(b)).isEqualTo("old");
            var lookup = CacheLookup.resolve(registry, REGION, "key", String.class);
            String old = track(lookup);
            storeA.write(lookup, old, new RedisCacheStore.Value("old".getBytes(StandardCharsets.UTF_8), false), Duration.ofSeconds(30));
            assertThat(new String(storeB.read(lookup, old).payload(), StandardCharsets.UTF_8)).isEqualTo("old");
            parameters.save(id, input("new")); // Only the application's own facade gets this commit callback.
            clock.advance(2); b.reconcile();
            assertThat(storeA.read(lookup, old)).isNull();
            assertThat(read(b)).isEqualTo("new");
            assertThat(read(a)).isEqualTo("new");
            assertThat(storeA.write(lookup, old, new RedisCacheStore.Value(new byte[0], true), Duration.ofSeconds(5))).isFalse();
            return null;
        });
    }

    @Test void rollbackPreservesGenerationAndTargetFailureIsRepairedFromCommittedAuthority() {
        TenantScope.within(7352, () -> {
            long id = source(); var failed = new AtomicBoolean(); var node = node(storeA, failed);
            assertThat(read(node)).isEqualTo("old");
            var lookup = CacheLookup.resolve(registry, REGION, "key", String.class);
            String old = track(lookup); long version = revisions.current(REGION);
            new TransactionTemplate(transactions).execute(status -> {
                parameters.save(id, input("rollback")); node.invalidate(REGION); status.setRollbackOnly(); return null;
            });
            clock.advance(2); node.reconcile();
            assertThat(revisions.current(REGION)).isEqualTo(version);
            assertThat(storeA.generation(REGION, 7352)).isEqualTo(old);
            assertThat(read(node)).isEqualTo("old");
            failed.set(true);
            assertThatCode(() -> new TransactionTemplate(transactions).execute(status -> {
                parameters.save(id, input("committed")); node.invalidate(REGION); return null;
            })).doesNotThrowAnyException();
            assertThat(read(node)).isEqualTo("committed"); // Bypass while the target is unavailable.
            failed.set(false); node.reconcile();
            assertThat(storeA.generation(REGION, 7352)).isNotEqualTo(old);
            assertThat(read(node)).isEqualTo("committed");
            return null;
        });
    }

    private DurableManagedCache node(RedisCacheStore store, AtomicBoolean failed) {
        var properties = new CacheProperties(null, null, null, null, null, 0.0, null);
        var local = new LocalManagedCache(registry, properties, clock);
        CacheInvalidationTarget target = (region, tenant, version) -> {
            if (failed.get()) throw new IllegalStateException("Injected target unavailability");
            store.synchronize(region, tenant, version);
            local.applyRevision(region, tenant, version);
        };
        return new DurableManagedCache(local, target, revisions, registry, properties, clock);
    }
    private long source() {
        parameters.saveGroup(null, new ParameterService.GroupInput("cachetest", "Cache test", null, 0, Map.of()));
        return parameters.save(null, input("old")).id();
    }
    private ParameterService.Input input(String value) {
        return new ParameterService.Input("cachetest.redis", "cachetest", "Redis test", null, "STRING", value, false, false, 0, Map.of());
    }
    private String read(DurableManagedCache node) {
        return node.get(REGION, "key", String.class, () -> parametersMapper.selectOne(
                new LambdaQueryWrapper<ParameterEntity>().eq(ParameterEntity::getParamKey, "cachetest.redis")).getParamValue());
    }
    private String track(CacheLookup lookup) {
        String generation = storeA.generation(REGION, lookup.tenant());
        cleanup.add(storeA.epochKey(REGION, lookup.tenant())); cleanup.add(storeA.revisionKey(REGION, lookup.tenant()));
        return generation;
    }
    private LettuceConnectionFactory factory(int port) {
        var factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", port),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(2)).shutdownTimeout(Duration.ZERO).build());
        factory.afterPropertiesSet(); factory.start(); factories.add(factory); return factory;
    }
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-11T00:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
