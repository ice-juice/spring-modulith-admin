package com.sma.admin.common.internal.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.function.Supplier;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.service.CacheRevisionStore;
import com.sma.admin.common.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Durable coalesced invalidations: each process observes its own scopes, never a competing consumer group. */
public final class DurableManagedCache implements ManagedCache, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DurableManagedCache.class);
    private record Scope(String region, long tenant) {}
    private static final class Observed {
        long revision = -1;
        volatile CacheRevisionStore.State authority;
        Instant checkedAt;
        volatile boolean failed;
        volatile CacheCounters counters=new CacheCounters(0);
        final ReentrantLock lock = new ReentrantLock();
        final AtomicLong requiredRevision = new AtomicLong();
        final AtomicLong requiredControlVersion = new AtomicLong();
    }
    private final ManagedCache delegate;
    private final CacheInvalidationTarget target;
    private final CacheRevisionStore revisions;
    private final CacheRegistry registry;
    private final Cache<Scope, Observed> observed;
    private final Clock clock;
    private final CacheLoadGuard loads;
    private final CacheResilienceProperties resilience;
    private final boolean noCache;
    private volatile boolean closed;

    DurableManagedCache(ManagedCache delegate, CacheInvalidationTarget target, CacheRevisionStore revisions,
            CacheRegistry registry, CacheProperties properties, Clock clock) {
        this(delegate, target, revisions, registry, properties, clock, CacheResilienceProperties.defaults());
    }

    DurableManagedCache(ManagedCache delegate, CacheInvalidationTarget target, CacheRevisionStore revisions,
            CacheRegistry registry, CacheProperties properties, Clock clock, CacheResilienceProperties resilience) {
        this.delegate = delegate; this.target = target; this.revisions = revisions; this.registry = registry; this.clock = clock;
        this.resilience = resilience; this.loads = new CacheLoadGuard(registry, resilience);
        noCache = properties.provider() == CacheProperties.Provider.NONE;
        observed = Caffeine.newBuilder().maximumSize(properties.maxScopes()).expireAfterAccess(Duration.ofHours(1)).build();
    }

    @Override public <T> T get(String region, String key, Class<T> valueType, Supplier<T> loader, Predicate<T> cacheable) {
        CacheLookup.resolve(registry, region, key, valueType);
        if (loader == null || cacheable == null) throw new IllegalArgumentException("Invalid cache loader");
        var guarded = loads.protect(region, loader);
        if (CacheTransactions.bypass() || noCache) return delegate.get(region, key, valueType, guarded, cacheable);
        var scope = new Scope(region, CurrentUser.tenantId());
        var state = observed.get(scope, ignored -> new Observed());
        if (!refresh(scope, state, false)) { state.counters.bypasses.increment(); return guarded.get(); }
        return delegate.get(region, key, valueType, guarded, cacheable);
    }

    @Override public void invalidate(String region) {
        var descriptor = registry.require(region);
        if (descriptor.kind() != CacheDescriptor.Kind.DATA || !descriptor.operations().contains(CacheDescriptor.Operation.CLEAR)) {
            throw new IllegalArgumentException("Region does not allow cache invalidation");
        }
        var scope = new Scope(region, CurrentUser.tenantId());
        boolean transaction = CacheTransactions.bypass();
        if (transaction && TransactionSynchronizationManager.getSynchronizations().stream().anyMatch(sync ->
                sync instanceof Invalidation invalidation && invalidation.owner() == this && invalidation.scope.equals(scope))) return;
        // REQUIRED joins the business transaction. Failure here must roll back the business write as well.
        long version = revisions.advance(region);
        if (transaction) TransactionSynchronizationManager.registerSynchronization(new Invalidation(scope, version));
        else committed(scope, version);
    }

    private final class Invalidation implements TransactionSynchronization {
        private final Scope scope;
        private final long version;
        Invalidation(Scope scope, long version) { this.scope = scope; this.version = version; }
        DurableManagedCache owner() { return DurableManagedCache.this; }
        @Override public void afterCommit() { committed(scope, version); }
    }

    private void committed(Scope scope, long version) {
        var state = observed.get(scope, ignored -> new Observed());
        state.requiredRevision.accumulateAndGet(version, Math::max);
        // A lost/failed fast path never turns a committed business transaction into an apparent failure.
        refresh(scope, state, true);
    }

    /** Called only after a governance transaction commits; reconciliation repairs a lost notification. */
    void controlCommitted(String region,CacheRevisionStore.State committed) {
        registry.require(region);
        var scope=new Scope(region,CurrentUser.tenantId());
        var state=observed.get(scope,ignored->new Observed());
        state.requiredRevision.accumulateAndGet(committed.revision(),Math::max);
        state.requiredControlVersion.accumulateAndGet(committed.controlVersion(),Math::max);
        refresh(scope,state,true);
    }

    CacheRevisionStore.State controlState(String region) {
        if(registry.require(region).kind()!=CacheDescriptor.Kind.DATA)throw new IllegalArgumentException("Data region required");
        var scope=new Scope(region,CurrentUser.tenantId());
        var state=observed.get(scope,ignored->new Observed());
        if(!refresh(scope,state,true))throw new CacheUnavailableException();
        return state.authority;
    }

    private boolean refresh(Scope scope, Observed state, boolean force) {
        try {
            if (!state.lock.tryLock(resilience.lockWait().toNanos(), TimeUnit.NANOSECONDS)) return false;
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
        try {
            Instant now = clock.instant();
            if (!force && !state.failed && state.revision >= state.requiredRevision.get()
                    &&state.authority!=null&&state.authority.controlVersion()>=state.requiredControlVersion.get()&&state.checkedAt != null && !now.isBefore(state.checkedAt)
                    && now.isBefore(state.checkedAt.plusSeconds(1))) return true;
            try {
                var authority=CacheExecutionScope.within(scope.tenant,()->revisions.state(scope.region));
                long version=authority.revision();
                if (version < state.revision || version < state.requiredRevision.get()) throw new IllegalStateException("Cache authority revision regressed or is older than a committed write");
                if(authority.controlVersion()<state.requiredControlVersion.get()||
                        (state.authority!=null&&(authority.controlVersion()<state.authority.controlVersion()||authority.statsEpoch()<state.authority.statsEpoch())))
                    throw new IllegalStateException("Cache control authority regressed");
                target.applyState(scope.region,scope.tenant,authority);
                state.revision = version;
                state.authority=authority;
                if(authority.statsEpoch()>state.counters.epoch)state.counters=new CacheCounters(authority.statsEpoch());
                // Record the start time, not completion; a slow poll cannot extend the freshness allowance.
                state.checkedAt = now; state.failed = false; return true;
            } catch (RuntimeException failure) {
                if (!state.failed) log.warn("Cache revision unavailable for region {}; bypass until recovered", scope.region);
                state.failed = true; state.checkedAt = null; return false;
            }
        } finally { state.lock.unlock(); }
    }

    @Scheduled(fixedDelay=1000)
    public void reconcile() {
        if (closed) return;
        observed.asMap().forEach((scope, state) -> refresh(scope, state, false));
    }

    record RuntimeState(String state, String circuit, long sourceRejections, long backendErrors, long invalidationFailures) {}
    RuntimeState runtime() {
        long failed = observed.asMap().values().stream().filter(state -> state.failed).count();
        if (delegate instanceof RedisManagedCache redis) return new RuntimeState(redis.available() && failed == 0 ? "AVAILABLE" : "BYPASS",
                redis.status().name(), loads.rejected(), redis.errors(), failed);
        return new RuntimeState(noCache ? "DISABLED" : failed > 0 ? "BYPASS" : "AVAILABLE", "CLOSED", loads.rejected(), 0, failed);
    }
    @Override public void close() { closed = true; observed.invalidateAll(); }

    @Override public CacheSnapshot snapshot(String region) {
        registry.require(region);
        var scope = new Scope(region, CurrentUser.tenantId());
        var state = observed.get(scope, ignored -> new Observed());
        boolean current = refresh(scope, state, false);
        var snapshot = delegate.snapshot(region);
        return new CacheSnapshot(snapshot.region(), snapshot.provider(), current||noCache ? snapshot.size() : -1, current||noCache ? snapshot.bytes() : -1, snapshot.hits(),
                snapshot.misses(), snapshot.bypasses() + state.counters.bypasses.sum(), snapshot.rejectedWrites());
    }
}
