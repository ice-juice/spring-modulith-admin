package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.security.TenantScope;

/** Explicitly opt into a task-owned localhost server; cleanup deletes only exact randomly namespaced test keys. */
@EnabledIfEnvironmentVariable(named="SMA_CACHE_REDIS_TEST_PORT", matches="[1-9][0-9]{3,4}")
class RedisCacheStoreTests {
    private static final String REGION = "config.parameters";
    private LettuceConnectionFactory firstFactory, secondFactory;
    private StringRedisTemplate firstTemplate;
    private RedisCacheStore first, second;
    private final Set<String> cleanup = new HashSet<>();
    private String namespace;

    @BeforeEach void connect() {
        int port = Integer.parseInt(System.getenv("SMA_CACHE_REDIS_TEST_PORT"));
        if (port < 10000 || port > 65535) throw new IllegalArgumentException("Redis tests require an isolated high localhost port");
        firstFactory = factory(port); secondFactory = factory(port);
        firstTemplate = new StringRedisTemplate(firstFactory);
        namespace = "p08test-" + UUID.randomUUID();
        first = new RedisCacheStore(firstTemplate, namespace);
        second = new RedisCacheStore(new StringRedisTemplate(secondFactory), namespace);
        try (var connection = firstTemplate.getConnectionFactory().getConnection()) { assertThat(connection.ping()).isEqualTo("PONG"); }
    }

    @AfterEach void close() {
        try { if (firstTemplate != null && !cleanup.isEmpty()) firstTemplate.delete(cleanup); }
        finally {
            if (firstFactory != null) firstFactory.destroy();
            if (secondFactory != null) secondFactory.destroy();
        }
    }

    @Test void twoIndependentClientsShareValuesAndObserveInvalidation() {
        var lookup = lookup(21, 1, String.class);
        String generation = track(first, lookup);
        assertThat(first.write(lookup, generation, value("shared"), Duration.ofSeconds(10))).isTrue();
        assertThat(text(second.read(lookup, generation))).isEqualTo("shared");
        second.invalidate(REGION, 21);
        assertThat(first.read(lookup, generation)).isNull();
        assertThat(first.generation(REGION, 21)).isNotEqualTo(generation);
    }

    @Test void staleLoadsCannotRefillAfterAnotherClientClears() throws Exception {
        var lookup = lookup(22, 1, String.class);
        String old = track(first, lookup);
        var loading = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> fill = executor.submit(() -> {
                loading.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test gate timed out");
                return first.write(lookup, old, value("stale"), Duration.ofSeconds(10));
            });
            try {
                assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
                second.invalidate(REGION, 22);
            } finally { release.countDown(); }
            assertThat(fill.get(5, TimeUnit.SECONDS)).isFalse();
        }
        String current = track(second, lookup);
        assertThat(second.read(lookup, current)).isNull();
        assertThat(second.write(lookup, current, value("fresh"), Duration.ofSeconds(10))).isTrue();
        assertThat(text(first.read(lookup, current))).isEqualTo("fresh");
    }

    @Test void evictionOfEpochDoesNotResurrectOldDataAndScriptsUseOneClusterSlot() {
        var lookup = lookup(23, 1, String.class);
        String old = track(first, lookup);
        first.write(lookup, old, value("old"), Duration.ofSeconds(10));
        String epoch = first.epochKey(REGION, 23), data = first.dataKey(lookup, old);
        assertThat(data).startsWith("d:"); // v2 keeps all fields and quota metadata in the one hash/cluster slot.
        assertThat(tag(epoch)).isNotEqualTo(tag(first.epochKey(REGION, 24)));
        firstTemplate.delete(epoch); // Simulate eviction/failover loss of only the generation metadata.
        String current = track(second, lookup);
        assertThat(current).isNotEqualTo(old);
        assertThat(first.read(lookup, old)).isNull();
        assertThat(first.read(lookup, current)).isNull();
        assertThat(first.write(lookup, old, value("stale"), Duration.ofSeconds(10))).isFalse();
    }

    @Test void isolatesTenantsNamespacesSchemasAndDeclaredTypes() {
        var lookup = lookup(25, 1, String.class);
        String generation = track(first, lookup);
        first.write(lookup, generation, value("private"), Duration.ofSeconds(10));
        var otherTenant = lookup(26, 1, String.class);
        assertThat(first.read(otherTenant, track(first, otherTenant))).isNull();
        assertThat(first.read(lookup(25, 2, String.class), generation)).isNull();
        assertThat(first.read(lookup(25, 1, Long.class), generation)).isNull();
        var otherNamespace = new RedisCacheStore(firstTemplate, namespace + "-other");
        assertThat(otherNamespace.read(lookup, track(otherNamespace, lookup))).isNull();
        assertThat(first.dataKey(lookup, generation)).doesNotContain("raw-user-key");
    }

    @Test void bothPositiveAndNegativeEntriesExpireWithoutReadRenewal() {
        var lookup = lookup(27, 1, String.class);
        String generation = track(first, lookup);
        first.write(lookup, generation, new RedisCacheStore.Value(new byte[0], true), Duration.ofMillis(250));
        assertThat(second.read(lookup, generation).absent()).isTrue();
        await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(20))
                .untilAsserted(() -> assertThat(second.read(lookup, generation)).isNull());
        first.write(lookup, generation, value("short"), Duration.ofMillis(250));
        assertThat(text(second.read(lookup, generation))).isEqualTo("short");
        await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(20))
                .untilAsserted(() -> assertThat(second.read(lookup, generation)).isNull());
    }

    @Test void corruptEntryCanBeRemovedWithoutDeletingOtherData() {
        var lookup = lookup(28, 1, String.class);
        String generation = track(first, lookup);
        firstTemplate.opsForHash().put(first.epochKey(REGION, lookup.tenant()), first.dataKey(lookup, generation),
                (System.currentTimeMillis() + 10000) + ":malformed");
        assertThatThrownBy(() -> second.read(lookup, generation)).isInstanceOf(IllegalArgumentException.class);
        second.evictCorrupt(lookup, generation);
        assertThat(first.read(lookup, generation)).isNull();
        assertThat(first.generation(REGION, 28)).isEqualTo(generation);
    }

    @Test void durableRevisionsAreIdempotentAndNeverRegressEvenBeyondLuaIntegerPrecision() {
        var lookup = lookup(29, 1, String.class);
        String current = first.synchronize(REGION, 29, 9_007_199_254_740_993L);
        cleanup.add(first.revisionKey(REGION, 29));
        track(first, lookup);
        first.write(lookup, current, value("fresh"), Duration.ofSeconds(10));
        assertThat(second.synchronize(REGION, 29, 9_007_199_254_740_992L)).isEqualTo(current);
        assertThat(second.synchronize(REGION, 29, 9_007_199_254_740_993L)).isEqualTo(current);
        assertThat(text(second.read(lookup, current))).isEqualTo("fresh");
        String next = second.synchronize(REGION, 29, 9_007_199_254_740_994L);
        assertThat(next).isNotEqualTo(current);
        assertThat(first.read(lookup, current)).isNull();
        firstTemplate.opsForHash().delete(first.epochKey(REGION, 29), "_generation");
        String recovered = first.synchronize(REGION, 29, 1);
        assertThat(recovered).isNotEqualTo(next);
        assertThat(firstTemplate.opsForHash().get(first.revisionKey(REGION, 29), "_revision")).isEqualTo("0009007199254740994");
    }

    @Test void concurrentWritersCannotOversubscribeTheSharedTenantRegionQuota() throws Exception {
        var one = lookup(30, 1, String.class);
        var two = new CacheLookup(one.descriptor(), one.tenant(), one.portal(), one.user(), CacheLookup.hash("second"), one.valueType());
        String generation = track(first, one);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> { start.await(); return first.write(one, generation, value("one"), Duration.ofSeconds(10), 1, 4096); });
            var b = executor.submit(() -> { start.await(); return second.write(two, generation, value("two"), Duration.ofSeconds(10), 1, 4096); });
            start.countDown();
            assertThat(List.of(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(first.snapshot(REGION, 30).size()).isEqualTo(1);
    }

    @Test void byteQuotaRejectsOversizedReplacementWithoutLosingTheExistingValue() {
        var lookup = lookup(31, 1, String.class); String generation = track(first, lookup);
        assertThat(first.write(lookup, generation, value("small"), Duration.ofSeconds(10), 10, 512)).isTrue();
        assertThat(second.write(lookup, generation, value("x".repeat(1000)), Duration.ofSeconds(10), 10, 512)).isFalse();
        assertThat(text(first.read(lookup, generation))).isEqualTo("small");
        assertThat(first.snapshot(REGION, 31).bytes()).isLessThanOrEqualTo(512);
    }

    private String track(RedisCacheStore store, CacheLookup lookup) {
        String generation = store.generation(lookup.descriptor().id(), lookup.tenant());
        cleanup.add(store.epochKey(lookup.descriptor().id(), lookup.tenant()));
        return generation;
    }
    private CacheLookup lookup(long tenant, int schema, Class<?> type) {
        var descriptor = new CacheDescriptor(REGION, "config", "config.cache.parameters", "basic-config", "parameter-read",
                CacheDescriptor.Kind.DATA, CacheDescriptor.Scope.TENANT, schema, CachePolicy.defaults(),
                Set.of(CacheDescriptor.Operation.CLEAR), Set.of("test-write"), "monitor:cache:clear");
        return TenantScope.within(tenant, () -> CacheLookup.resolve(new CacheRegistry(List.of(() -> List.of(descriptor))), REGION, "raw-user-key", type));
    }
    private LettuceConnectionFactory factory(int port) {
        var config = LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(2)).shutdownTimeout(Duration.ZERO).build();
        var factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", port), config);
        factory.afterPropertiesSet(); factory.start(); return factory;
    }
    private static RedisCacheStore.Value value(String text) { return new RedisCacheStore.Value(text.getBytes(StandardCharsets.UTF_8), false); }
    private static String text(RedisCacheStore.Value value) { return new String(value.payload(), StandardCharsets.UTF_8); }
    private static String tag(String key) { return key.substring(key.indexOf('{') + 1, key.indexOf('}')); }
}
