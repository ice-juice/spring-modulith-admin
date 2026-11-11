package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.service.CacheRevisionStore;
import com.sma.admin.common.security.TenantScope;

@EnabledIfEnvironmentVariable(named="SMA_CACHE_REDIS_TEST_PORT", matches="[1-9][0-9]{3,4}")
class RedisManagedCacheTests {
    private static final String REGION = "config.parameters";
    private final List<LettuceConnectionFactory> factories = new ArrayList<>();
    private final Set<String> cleanup = new HashSet<>();
    private String namespace;
    private StringRedisTemplate template;
    private RedisCacheStore storeA, storeB;
    private final CacheRevisionStore revisions = mock(CacheRevisionStore.class);
    private final AtomicLong version = new AtomicLong();
    private final MutableClock clock = new MutableClock();

    @BeforeEach void connect() {
        int port = Integer.parseInt(System.getenv("SMA_CACHE_REDIS_TEST_PORT"));
        if (port < 10000 || port > 65535) throw new IllegalArgumentException("Require a dedicated high localhost port");
        namespace = "p08provider-" + UUID.randomUUID();
        template = new StringRedisTemplate(factory(port));
        storeA = new RedisCacheStore(template, namespace);
        storeB = new RedisCacheStore(new StringRedisTemplate(factory(port)), namespace);
        when(revisions.state(REGION)).thenAnswer(call -> new CacheRevisionStore.State(version.get(),0,0,false,null));
        when(revisions.advance(REGION)).thenAnswer(call -> version.incrementAndGet());
    }
    @AfterEach void close() {
        try { if (template != null && !cleanup.isEmpty()) template.delete(cleanup); }
        finally { factories.forEach(LettuceConnectionFactory::destroy); }
    }

    @Test void independentProvidersShareDataAndRepairRemoteAuthorityChanges() {
        var a = node(storeA, CachePolicy.defaults()); var b = node(storeB, CachePolicy.defaults());
        track(1);
        assertThat(a.get(REGION, "key", String.class, () -> "shared")).isEqualTo("shared");
        assertThat(b.get(REGION, "key", String.class, () -> "wrong")).isEqualTo("shared");
        a.invalidate(REGION); clock.advance(2);
        assertThat(b.get(REGION, "key", String.class, () -> "new")).isEqualTo("new");
        assertThat(a.get(REGION, "key", String.class, () -> "wrong")).isEqualTo("new");
        assertThat(b.snapshot(REGION).size()).isEqualTo(1);
    }

    @Test void tenantIsolationNegativeValuesAndAdmissionPredicatesArePreserved() {
        var a = node(storeA, CachePolicy.defaults()); track(1); track(2);
        assertThat(a.get(REGION, "missing", String.class, () -> null)).isNull();
        assertThat(a.get(REGION, "missing", String.class, () -> "wrong")).isNull();
        assertThat(a.get(REGION, "secret", String.class, () -> "private", value -> false)).isEqualTo("private");
        assertThat(a.snapshot(REGION).size()).isEqualTo(1);
        TenantScope.within(2, () -> {
            assertThat(a.get(REGION, "missing", String.class, () -> "tenant-two")).isEqualTo("tenant-two"); return null;
        });
        assertThat(a.get(REGION, "missing", String.class, () -> "wrong")).isNull();
    }

    @Test void quotaIsAtomicAcrossClientsAndClearingReleasesCapacityWithoutScan() {
        var policy = new CachePolicy(Duration.ofMinutes(5), Duration.ofSeconds(15), 1, 4096, 1024);
        var a = node(storeA, policy); var b = node(storeB, policy); track(1);
        assertThat(a.get(REGION, "first", String.class, () -> "kept")).isEqualTo("kept");
        var source = new AtomicInteger();
        assertThat(b.get(REGION, "second", Integer.class, source::incrementAndGet)).isEqualTo(1);
        assertThat(b.get(REGION, "second", Integer.class, source::incrementAndGet)).isEqualTo(2);
        assertThat(b.snapshot(REGION).size()).isEqualTo(1);
        a.invalidate(REGION); clock.advance(2);
        assertThat(b.get(REGION, "second", Integer.class, source::incrementAndGet)).isEqualTo(3);
        assertThat(b.get(REGION, "second", Integer.class, source::incrementAndGet)).isEqualTo(3);
    }

    @Test void restoringAnOlderRedisHashCannotOverrideTheObservedDatabaseVersion() {
        var a = node(storeA, CachePolicy.defaults()); track(1);
        a.get(REGION, "key", String.class, () -> "old");
        String redisKey = storeA.epochKey(REGION, 1);
        var oldSnapshot = template.opsForHash().entries(redisKey);
        a.invalidate(REGION);
        assertThat(a.get(REGION, "key", String.class, () -> "new")).isEqualTo("new");
        template.delete(redisKey); template.opsForHash().putAll(redisKey, oldSnapshot); // Simulate stale replica/snapshot recovery.
        assertThat(a.get(REGION, "key", String.class, () -> "authoritative")).isEqualTo("authoritative");
    }

    @Test void malformedSerializedValuesAreEvictedAndReloadedRatherThanReturnedAsMissOrError() {
        var a = node(storeA, CachePolicy.defaults()); track(1);
        a.get(REGION, "key", String.class, () -> "initial");
        String generation = storeA.generation(REGION, 1);
        var lookup = CacheLookup.resolve(registry(CachePolicy.defaults()), REGION, "key", String.class);
        template.opsForHash().put(storeA.epochKey(REGION, 1), storeA.dataKey(lookup, generation),
                (System.currentTimeMillis() + 10000) + ":V" + Base64.getEncoder().encodeToString("invalid json".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(a.get(REGION, "key", String.class, () -> "recovered")).isEqualTo("recovered");
        assertThat(a.snapshot(REGION).rejectedWrites()).isPositive();
    }

    @Test void realConnectionLossBypassesWithoutLocalFallbackAndRecoveryFencesOldData() throws Exception {
        int targetPort = Integer.parseInt(System.getenv("SMA_CACHE_REDIS_TEST_PORT"));
        try (var proxy = new RedisFaultProxy(targetPort)) {
            var registry = registry(CachePolicy.defaults());
            var properties = new CacheProperties(CacheProperties.Provider.REDIS, namespace, null, null, null, 0.0, null);
            var resilience = new CacheResilienceProperties(null, null, null, 1, Duration.ofMillis(50));
            var store = new RedisCacheStore(new StringRedisTemplate(factory(proxy.port())), namespace);
            var provider = new RedisManagedCache(registry, properties, resilience, store);
            var a = new DurableManagedCache(provider, provider, revisions, registry, properties, clock, resilience);
            track(1);
            assertThat(a.get(REGION, "key", String.class, () -> "old")).isEqualTo("old");
            proxy.disconnect();
            assertThat(a.get(REGION, "key", String.class, () -> "source-one")).isEqualTo("source-one");
            assertThat(a.get(REGION, "key", String.class, () -> "source-two")).isEqualTo("source-two");
            var health = new CacheBackendHealth(a, properties);
            assertThat(health.health().getStatus()).isEqualTo(org.springframework.boot.health.contributor.Status.UP);
            assertThat(health.health().getDetails()).containsEntry("state", "BYPASS");
            var meters = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
            try {
                health.bind(meters);
                assertThat(meters.get("sma.cache.available").gauge().value()).isZero();
                assertThat(meters.get("sma.cache.backend.errors").functionCounter().count()).isPositive();
            } finally { meters.close(); }
            assertThatCode(() -> a.invalidate(REGION)).doesNotThrowAnyException();
            assertThat(provider.status()).isEqualTo(CacheCircuit.Status.OPEN);
            proxy.reconnect();
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
                assertThat(a.get(REGION, "key", String.class, () -> "recovered")).isEqualTo("recovered");
                assertThat(provider.status()).isEqualTo(CacheCircuit.Status.CLOSED);
            });
            assertThat(a.get(REGION, "key", String.class, () -> "wrong")).isEqualTo("recovered");
        }
    }

    private DurableManagedCache node(RedisCacheStore store, CachePolicy policy) {
        var registry = registry(policy);
        var properties = new CacheProperties(CacheProperties.Provider.REDIS, namespace, null, null, null, 0.0, null);
        var resilience = CacheResilienceProperties.defaults();
        var redis = new RedisManagedCache(registry, properties, resilience, store);
        return new DurableManagedCache(redis, redis, revisions, registry, properties, clock, resilience);
    }
    private CacheRegistry registry(CachePolicy policy) {
        return new CacheRegistry(List.of(() -> List.of(new CacheDescriptor(REGION, "config", "config.cache.parameters", "basic-config", "parameter-read",
                CacheDescriptor.Kind.DATA, CacheDescriptor.Scope.TENANT, 1, policy, Set.of(CacheDescriptor.Operation.CLEAR), Set.of("save"), "monitor:cache:clear"))));
    }
    private void track(long tenant) { cleanup.add(storeA.epochKey(REGION, tenant)); }
    private LettuceConnectionFactory factory(int port) {
        var factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", port),
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).shutdownTimeout(Duration.ZERO).build());
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
