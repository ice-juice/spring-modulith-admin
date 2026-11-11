package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.service.CacheRevisionStore;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;

class DurableManagedCacheTests {
    private static final String REGION = "config.parameters";
    private final MutableClock clock = new MutableClock();
    private final CacheRevisionStore revisions = mock(CacheRevisionStore.class);
    private final CacheRegistry registry = new CacheRegistry(List.of(() -> List.of(new CacheDescriptor(REGION, "config",
            "config.cache.parameters", "basic-config", "parameter-read", CacheDescriptor.Kind.DATA, CacheDescriptor.Scope.TENANT,
            1, CachePolicy.defaults(), Set.of(CacheDescriptor.Operation.CLEAR), Set.of("save"), "monitor:cache:clear"))));
    private final CacheProperties properties = new CacheProperties(null, null, null, null, null, 0.0, null);

    @Test void everyNodeRepairsMissedFastPathIndependentlyAndColdNodesObserveLatestVersion() {
        var version = new AtomicLong(); var source = new AtomicReference<>("old");
        when(revisions.state(REGION)).thenAnswer(call -> authority(version.get()));
        when(revisions.advance(REGION)).thenAnswer(call -> version.incrementAndGet());
        var a = node(); var b = node();
        assertThat(read(a, source)).isEqualTo("old"); assertThat(read(b, source)).isEqualTo("old");
        source.set("new"); a.invalidate(REGION);
        assertThat(read(a, source)).isEqualTo("new");
        assertThat(read(b, source)).isEqualTo("old"); // Permitted bounded window; no competing consumer acknowledged it away.
        clock.advance(2); b.reconcile();
        assertThat(read(b, source)).isEqualTo("new"); assertThat(read(node(), source)).isEqualTo("new");
    }

    @Test void failedFastPathDoesNotTurnACommittedWriteIntoFailureAndNextReadRetries() {
        var source = new AtomicReference<>("old"); var a = node();
        when(revisions.state(REGION)).thenReturn(authority(0));
        read(a, source);
        when(revisions.advance(REGION)).thenReturn(1L);
        when(revisions.state(REGION)).thenThrow(new IllegalStateException("database unavailable"));
        source.set("new"); assertThatCode(() -> a.invalidate(REGION)).doesNotThrowAnyException();
        assertThat(read(a, source)).isEqualTo("new");
        assertThat(a.snapshot(REGION).bypasses()).isEqualTo(1);
        doReturn(authority(1)).when(revisions).state(REGION);
        assertThat(read(a, source)).isEqualTo("new");
        source.set("not yet invalidated"); assertThat(read(a, source)).isEqualTo("new");
    }

    @Test void revisionRegressionBypassesRatherThanServingAnOldCachedValue() {
        var source = new AtomicReference<>("before restore"); var a = node();
        when(revisions.state(REGION)).thenReturn(authority(8));
        read(a, source); source.set("after restore"); clock.advance(2);
        when(revisions.state(REGION)).thenReturn(authority(7));
        assertThat(read(a, source)).isEqualTo("after restore");
        assertThat(a.snapshot(REGION).bypasses()).isEqualTo(1);
    }

    @Test void backwardsClockAdjustmentCannotSuppressAuthorityChecks() {
        var source = new AtomicReference<>("old"); var a = node();
        when(revisions.state(REGION)).thenReturn(authority(0)); read(a, source);
        clock.advance(-60); source.set("new"); when(revisions.state(REGION)).thenReturn(authority(1));
        assertThat(read(a, source)).isEqualTo("new");
    }

    @Test void aSlowPollCannotHideACommitWhoseFastPathTimedOutWaitingForThePoll() throws Exception {
        var version = new AtomicLong(); var source = new AtomicReference<>("old"); var a = node();
        when(revisions.state(REGION)).thenAnswer(call -> authority(version.get()));
        when(revisions.advance(REGION)).thenAnswer(call -> version.incrementAndGet());
        read(a, source); clock.advance(2);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { long captured = version.get(); entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test gate timed out"); return authority(captured);
        }).when(revisions).state(REGION);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var poll = executor.submit(a::reconcile);
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                source.set("new"); a.invalidate(REGION); // Must record minimum committed version even if the lock is busy.
            } finally { release.countDown(); }
            poll.get(5, TimeUnit.SECONDS);
        }
        doAnswer(call -> authority(version.get())).when(revisions).state(REGION);
        assertThat(read(a, source)).isEqualTo("new");
    }

    @Test void reconciliationUsesCapturedTenantAndRestoresUnrelatedHumanContext() {
        var a = node();
        when(revisions.state(REGION)).thenAnswer(call -> {
            assertThat(CurrentUser.tenantId()).isEqualTo(731); return authority(0);
        });
        TenantScope.within(731, () -> a.get(REGION, "key", String.class, () -> "one"));
        var actor = new SmaPrincipal(19, "other", 732, "admin", null, false, DataAccess.unrestricted(), Set.of(), "UTC");
        clock.advance(2);
        Users.as(actor, () -> { a.reconcile(); assertThat(CurrentUser.require()).isEqualTo(actor); return null; });
    }

    private DurableManagedCache node() {
        var local = new LocalManagedCache(registry, properties, clock);
        return new DurableManagedCache(local, local, revisions, registry, properties, clock);
    }
    private CacheRevisionStore.State authority(long revision){return new CacheRevisionStore.State(revision,0,0,false,null);}
    private String read(ManagedCache cache, AtomicReference<String> source) { return cache.get(REGION, "key", String.class, source::get); }
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-11T00:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
