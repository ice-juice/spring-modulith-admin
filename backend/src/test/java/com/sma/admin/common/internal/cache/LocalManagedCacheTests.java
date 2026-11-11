package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;

class LocalManagedCacheTests {
    private static final String REGION = "config.parameters";
    private final MutableClock clock = new MutableClock();
    public record Value(List<String> names) {}

    @Test void expiresWithoutSlidingAndNegativeEntriesDoNotCaptureFallbacks() {
        var cache = cache(CacheDescriptor.Scope.TENANT, CachePolicy.defaults(), CacheProperties.Provider.LOCAL);
        var loads = new AtomicInteger();
        assertThat(cache.get(REGION, "missing", String.class, () -> { loads.incrementAndGet(); return null; })).isNull();
        assertThat(cache.get(REGION, "missing", String.class, () -> "new")).isNull();
        clock.advance(16);
        assertThat(cache.get(REGION, "missing", String.class, () -> "new")).isEqualTo("new");
        clock.advance(299);
        assertThat(cache.get(REGION, "missing", String.class, () -> "wrong")).isEqualTo("new");
        clock.advance(2);
        assertThat(cache.get(REGION, "missing", String.class, () -> "latest")).isEqualTo("latest");
        assertThat(loads).hasValue(1);
    }

    @Test void clearingOneTenantPreservesAnotherTenantsEntriesAndCounters() {
        var cache = cache(CacheDescriptor.Scope.TENANT, CachePolicy.defaults(), CacheProperties.Provider.LOCAL);
        TenantScope.within(7, () -> cache.get(REGION, "key", String.class, () -> "seven"));
        TenantScope.within(8, () -> cache.get(REGION, "key", String.class, () -> "eight"));
        TenantScope.within(7, () -> { cache.invalidate(REGION); assertThat(cache.snapshot(REGION).size()).isZero(); return null; });
        TenantScope.within(8, () -> {
            assertThat(cache.get(REGION, "key", String.class, () -> "wrong")).isEqualTo("eight");
            assertThat(cache.snapshot(REGION).hits()).isEqualTo(1); return null;
        });
    }

    @Test void isolatesUserAndPortalAndRequiresHumanContextForUserRegions() {
        var cache = cache(CacheDescriptor.Scope.TENANT_PORTAL_USER, CachePolicy.defaults(), CacheProperties.Provider.LOCAL);
        assertThatThrownBy(() -> cache.get(REGION, "key", String.class, () -> "no identity"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        Users.as(principal(1, "admin"), () -> cache.get(REGION, "key", String.class, () -> "one-admin"));
        Users.as(principal(2, "admin"), () -> assertValue(cache, "two-admin"));
        Users.as(principal(1, "customer"), () -> assertValue(cache, "one-customer"));
        Users.as(principal(1, "admin"), () -> {
            assertThat(cache.get(REGION, "key", String.class, () -> "wrong")).isEqualTo("one-admin"); return null;
        });
    }

    @Test void payloadsAreDetachedFromBothLoaderAndReaderMutableObjects() {
        var cache = cache(CacheDescriptor.Scope.TENANT, CachePolicy.defaults(), CacheProperties.Provider.LOCAL);
        var original = new Value(new ArrayList<>(List.of("a")));
        cache.get(REGION, "dto", Value.class, () -> original);
        original.names().add("loader mutation");
        var first = cache.get(REGION, "dto", Value.class, () -> null);
        assertThat(first.names()).containsExactly("a");
        first.names().add("reader mutation");
        assertThat(cache.get(REGION, "dto", Value.class, () -> null).names()).containsExactly("a");
    }

    @Test void fullOrOversizedCacheStillReturnsSourceResultsWithoutCrossTenantClear() {
        var policy = new CachePolicy(Duration.ofMinutes(5), Duration.ofSeconds(5), 1, 2048, 32);
        var cache = cache(CacheDescriptor.Scope.TENANT, policy, CacheProperties.Provider.LOCAL);
        cache.get(REGION, "first", String.class, () -> "kept");
        assertThat(cache.get(REGION, "second", String.class, () -> "source")).isEqualTo("source");
        assertThat(cache.snapshot(REGION).size()).isEqualTo(1);
        assertThat(cache.get(REGION, "first", String.class, () -> "wrong")).isEqualTo("kept");
        cache.invalidate(REGION);
        assertThat(cache.get(REGION, "large", String.class, () -> "x".repeat(100))).hasSize(100);
        assertThat(cache.snapshot(REGION).size()).isZero();
        assertThat(cache.snapshot(REGION).rejectedWrites()).isEqualTo(2);
        assertThat(cache.get(REGION, "third", String.class, () -> "fits")).isEqualTo("fits");
        assertThat(cache.snapshot(REGION).size()).isEqualTo(1);
    }

    @Test void sourceErrorsAreNotNegativeCachedAndNoneAlwaysLoads() {
        var cache = cache(CacheDescriptor.Scope.TENANT, CachePolicy.defaults(), CacheProperties.Provider.LOCAL);
        assertThatThrownBy(() -> cache.get(REGION, "key", String.class, () -> { throw new IllegalStateException("source failed"); }))
                .isInstanceOf(IllegalStateException.class).hasMessage("source failed");
        assertThat(cache.get(REGION, "key", String.class, () -> "recovered")).isEqualTo("recovered");
        var none = cache(CacheDescriptor.Scope.TENANT, CachePolicy.defaults(), CacheProperties.Provider.NONE);
        assertThat(none.get(REGION, "key", String.class, () -> "first")).isEqualTo("first");
        assertThat(none.get(REGION, "key", String.class, () -> "second")).isEqualTo("second");
        assertThat(none.snapshot(REGION).size()).isZero();
        assertThat(none.snapshot(REGION).bypasses()).isEqualTo(2);
    }

    @Test void monotonicExpiryStillBoundsLifetimeWhenWallClockMovesBackwards() {
        var policy = new CachePolicy(Duration.ofMillis(100), Duration.ZERO, 10, 4096, 1024);
        var cache = cache(CacheDescriptor.Scope.TENANT, policy, CacheProperties.Provider.LOCAL);
        assertThat(cache.get(REGION, "clock", String.class, () -> "old")).isEqualTo("old");
        clock.advance(-60);
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(cache.get(REGION, "clock", String.class, () -> "fresh")).isEqualTo("fresh"));
    }

    @Test void invalidationPreventsAnInFlightOldReadFromRefilling() throws Exception {
        var cache = cache(CacheDescriptor.Scope.TENANT, CachePolicy.defaults(), CacheProperties.Provider.LOCAL);
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> old = executor.submit(() -> cache.get(REGION, "key", String.class, () -> {
                started.countDown(); await(release); return "old";
            }));
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                cache.invalidate(REGION);
            } finally { release.countDown(); }
            assertThat(old.get(5, TimeUnit.SECONDS)).isEqualTo("old");
            assertThat(cache.snapshot(REGION).size()).isZero();
            assertThat(cache.get(REGION, "key", String.class, () -> "new")).isEqualTo("new");
        }
    }

    @Test void concurrentReadersOfOneKeyShareOneSuccessfulLoad() throws Exception {
        var cache = cache(CacheDescriptor.Scope.TENANT, CachePolicy.defaults(), CacheProperties.Provider.LOCAL);
        var loads = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<String>>();
            for (int i = 0; i < 20; i++) futures.add(executor.submit(() -> cache.get(REGION, "same", String.class, () -> {
                loads.incrementAndGet(); return "value";
            })));
            for (var future : futures) assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("value");
        }
        assertThat(loads).hasValue(1);
    }

    @Test void transactionsBypassCachedAndUncommittedValuesWithoutPublishingThem() {
        var cache = cache(CacheDescriptor.Scope.TENANT, CachePolicy.defaults(), CacheProperties.Provider.LOCAL);
        cache.get(REGION, "key", String.class, () -> "committed");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThat(cache.get(REGION, "key", String.class, () -> "uncommitted")).isEqualTo("uncommitted"); }
        finally { TransactionSynchronizationManager.clear(); }
        assertThat(cache.get(REGION, "key", String.class, () -> "wrong")).isEqualTo("committed");
    }

    @Test void configurationRejectsUnknownRegionsAndUnsupportedDeploymentModes() {
        var catalog = registry(CacheDescriptor.Scope.TENANT, CachePolicy.defaults());
        assertThatThrownBy(() -> new LocalManagedCache(catalog,
                new CacheProperties(CacheProperties.Provider.REDIS, null, null, null, null, null, null), clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocalManagedCache(catalog,
                new CacheProperties(null, null, CacheProperties.DeploymentMode.MULTI, null, null, null, null), clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocalManagedCache(catalog,
                new CacheProperties(null, null, null, null, null, null, Map.of("unknown.region",
                        new CacheProperties.RegionOverride(null, null, null, null, null))), clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String assertValue(LocalManagedCache cache, String value) {
        assertThat(cache.get(REGION, "key", String.class, () -> value)).isEqualTo(value); return value;
    }
    private SmaPrincipal principal(long id, String portal) {
        return new SmaPrincipal(id, "test", 9, portal, null, false, DataAccess.unrestricted(), Set.of(), "UTC");
    }
    private LocalManagedCache cache(CacheDescriptor.Scope scope, CachePolicy policy, CacheProperties.Provider provider) {
        return new LocalManagedCache(registry(scope, policy), new CacheProperties(provider, null, null, null, null, 0.0, null), clock);
    }
    private CacheRegistry registry(CacheDescriptor.Scope scope, CachePolicy policy) {
        return new CacheRegistry(List.of(() -> List.of(new CacheDescriptor(REGION, "config", "config.cache.parameters",
                "basic-config", "parameter-read", CacheDescriptor.Kind.DATA, scope, 1, policy,
                Set.of(CacheDescriptor.Operation.CLEAR), Set.of("test-write"), "monitor:cache:clear"))));
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out awaiting test gate"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
    }
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-11T00:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
