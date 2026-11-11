package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.cache.ManagedCache;
import com.sma.admin.common.internal.persistence.CacheRevisionEntity;
import com.sma.admin.common.internal.persistence.CacheRevisionMapper;
import com.sma.admin.common.internal.service.CacheRevisionStore;
import com.sma.admin.common.security.TenantScope;
import com.sma.admin.support.IntegrationTest;

@IntegrationTest
class CacheRevisionIntegrationTests {
    private static final String REGION = "config.parameters";
    @Autowired CacheRevisionStore revisions;
    @Autowired CacheRegistry registry;
    @Autowired CacheRevisionMapper rows;
    @Autowired PlatformTransactionManager transactions;
    private final MutableClock clock = new MutableClock();

    @Test void durableStateSurvivesMissingCallbacksAndEachNodeIndependentlyCatchesUp() {
        TenantScope.within(7301, () -> {
            var a = node(); var b = node(); var source = new AtomicReference<>("old");
            assertThat(read(a, source)).isEqualTo("old"); assertThat(read(b, source)).isEqualTo("old");
            // Commit without calling either facade: models a process dying between DB commit and its fast path.
            revisions.advance(REGION); source.set("new"); clock.advance(2);
            a.reconcile(); assertThat(read(a, source)).isEqualTo("new");
            b.reconcile(); assertThat(read(b, source)).isEqualTo("new");
            assertThat(read(node(), source)).isEqualTo("new");
            return null;
        });
    }

    @Test void repeatedInvalidationCoalescesWithinTransactionAndRollbackLeavesNoPublishedRevision() {
        TenantScope.within(7302, () -> {
            var node = node(); var tx = new TransactionTemplate(transactions);
            tx.execute(status -> { node.invalidate(REGION); node.invalidate(REGION); status.setRollbackOnly(); return null; });
            assertThat(revisions.current(REGION)).isZero();
            tx.execute(status -> { node.invalidate(REGION); node.invalidate(REGION); return null; });
            assertThat(revisions.current(REGION)).isEqualTo(1);
            return null;
        });
    }

    @Test void concurrentCommitsCannotLoseAnInvalidationOrPublishAnotherTenantsRevision() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<Long>>();
            for (int i = 0; i < 6; i++) futures.add(executor.submit(() -> TenantScope.within(7303, () -> revisions.advance(REGION))));
            var versions = new HashSet<Long>();
            for (var future : futures) versions.add(future.get(15, TimeUnit.SECONDS));
            assertThat(versions).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L);
        }
        TenantScope.within(7303, () -> { assertThat(revisions.current(REGION)).isEqualTo(6); return null; });
        TenantScope.within(7304, () -> { assertThat(revisions.current(REGION)).isZero(); return null; });
    }

    @Test void authorityReadsDoNotReuseAnOlderBusinessTransactionSnapshot() throws Exception {
        TenantScope.within(7305, () -> { revisions.advance(REGION); return null; });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            TenantScope.within(7305, () -> new TransactionTemplate(transactions).execute(status -> {
                assertThat(rowVersion()).isEqualTo(1);
                try { executor.submit(() -> TenantScope.within(7305, () -> revisions.advance(REGION))).get(15, TimeUnit.SECONDS); }
                catch (Exception failure) { throw new AssertionError(failure); }
                assertThat(rowVersion()).isEqualTo(1); // Repeatable-read business snapshot remains old.
                assertThat(revisions.current(REGION)).isEqualTo(2); // Separate committed authority view.
                status.setRollbackOnly(); return null;
            }));
        }
    }

    private long rowVersion() { return rows.selectOne(new LambdaQueryWrapper<CacheRevisionEntity>().eq(CacheRevisionEntity::getRegion, REGION)).getRevision(); }
    private DurableManagedCache node() {
        var properties = new CacheProperties(null, null, null, null, null, 0.0, null);
        var local = new LocalManagedCache(registry, properties, clock);
        return new DurableManagedCache(local, local, revisions, registry, properties, clock);
    }
    private String read(ManagedCache cache, AtomicReference<String> source) { return cache.get(REGION, "key", String.class, source::get); }
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-11T00:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
