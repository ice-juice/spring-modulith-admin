package com.sma.admin.common.internal.cache;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.common.internal.service.CacheRevisionStore;
import org.springframework.dao.DataAccessException;

/** Shared data with no local value fallback. The durable facade supplies trusted authority versions and load budgets. */
final class RedisManagedCache implements ManagedCache, CacheInvalidationTarget {
    private record Scope(String region, long tenant) {}
    private record Slot(String generation, RedisCacheStore.Value value) {}
    private record Attempt<T>(boolean available, T value) {}
    private static final class State {
        final AtomicLong revision = new AtomicLong(-1);
        volatile CacheRevisionStore.State authority;
        volatile CacheCounters counters=new CacheCounters(0);
        final ReentrantLock controlLock=new ReentrantLock();
    }
    private final CacheRegistry registry;
    private final CacheProperties properties;
    private final CacheResilienceProperties resilience;
    private final RedisCacheStore store;
    private final CacheCodec codec = new CacheCodec();
    private final CacheCircuit circuit;
    private final Cache<Scope, State> states;
    private final Map<String, CachePolicy> policies;
    private final ReentrantLock[] locks = new ReentrantLock[256];
    private final LongAdder errors = new LongAdder();
    private final AtomicBoolean available = new AtomicBoolean(true);

    RedisManagedCache(CacheRegistry registry, CacheProperties properties, CacheResilienceProperties resilience, RedisCacheStore store) {
        this.registry = registry; this.properties = properties; this.resilience = resilience; this.store = store;
        circuit = new CacheCircuit(resilience);
        states = Caffeine.newBuilder().maximumSize(properties.maxScopes()).expireAfterAccess(Duration.ofHours(1)).build();
        properties.regions().keySet().forEach(registry::require);
        policies = registry.regions().stream().filter(r -> r.kind() == CacheDescriptor.Kind.DATA)
                .collect(Collectors.toUnmodifiableMap(CacheDescriptor::id, r -> properties.policy(r.id(), r.policy())));
        policies.values().forEach(policy -> {
            if (policy.maxEntries() > 10_000 || policy.maxBytes() > 64L * 1024 * 1024)
                throw new IllegalArgumentException("Redis regions must stay within bounded atomic-operation limits");
        });
        for (int i = 0; i < locks.length; i++) locks[i] = new ReentrantLock();
    }

    @Override public <T> T get(String region, String key, Class<T> type, Supplier<T> loader, Predicate<T> cacheable) {
        var lookup = CacheLookup.resolve(registry, region, key, type);
        if (loader == null || cacheable == null) throw new IllegalArgumentException("Invalid cache loader");
        var state = states.get(new Scope(region, lookup.tenant()), ignored -> new State());
        var counters=state.counters;
        var authority=state.authority;
        // Read revision/pause/policy from one immutable publication; never mix a newly advanced revision with old settings.
        long revision=authority==null?-1:authority.revision();
        var policy=CacheRuntimePolicy.effective(policies.get(region),authority==null?null:authority.override());
        if (CacheTransactions.bypass() || revision < 0 ||(authority!=null&&authority.paused())) { counters.bypasses.increment(); return loader.get(); }
        var lock = locks[(lookup.hashCode() & Integer.MAX_VALUE) % locks.length];
        try {
            if (!lock.tryLock(resilience.lockWait().toNanos(), TimeUnit.NANOSECONDS)) { counters.bypasses.increment(); return loader.get(); }
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new CacheUnavailableException(); }
        try {
            var attempt = call(() -> {
                String generation = store.acquireGeneration(region, lookup.tenant(), revision);
                if(generation==null)return new Slot(null,null);
                try { return new Slot(generation, store.read(lookup, generation)); }
                catch (IllegalArgumentException corrupt) { counters.rejected.increment(); store.evictCorrupt(lookup, generation); return new Slot(generation, null); }
            });
            if (!attempt.available()||attempt.value().generation()==null) { counters.bypasses.increment(); return loader.get(); }
            if(!sameDataState(state,revision)){counters.bypasses.increment();return loader.get();}
            var slot = attempt.value();
            if (slot.value() != null) {
                T value = null; boolean decoded = true;
                try { value = slot.value().absent() ? null : codec.decode(slot.value().payload(), type); }
                catch (RuntimeException corrupt) { decoded = false; counters.rejected.increment(); }
                if (decoded && cacheable.test(value)) { counters.hits.increment(); return value; }
                call(() -> { store.evictCorrupt(lookup, slot.generation()); return true; });
            }
            counters.misses.increment();
            T result = loader.get(); // Source/predicate exceptions never count as Redis failures or negative entries.
            if (!cacheable.test(result)) { counters.bypasses.increment(); return result; }
            Duration ttl = result == null ? policy.negativeTtl() : policy.ttl();
            if (ttl.isZero()) return result;
            byte[] payload;
            try { payload = result == null ? new byte[0] : codec.encode(result); }
            catch (RuntimeException invalid) { counters.rejected.increment(); return result; }
            if (payload.length > policy.maxEntryBytes()) { counters.rejected.increment(); return result; }
            if(!sameDataState(state,revision))return result;
            Duration effective = Duration.ofMillis(Math.max(1, (long)(ttl.toMillis() * (1 - properties.ttlJitter() * ThreadLocalRandom.current().nextDouble()))));
            var written = call(() -> store.write(lookup, slot.generation(), new RedisCacheStore.Value(payload, result == null), effective,
                    policy.maxEntries(), Math.min(policy.maxBytes(), properties.maxBytes())));
            if (!written.available() || !Boolean.TRUE.equals(written.value())) counters.rejected.increment();
            return result;
        } finally { lock.unlock(); }
    }

    @Override public void applyRevision(String region, long tenant, long revision) {
        registry.require(region);
        if (tenant < 1 || revision < 0) throw new IllegalArgumentException("Invalid cache scope");
        var state = states.get(new Scope(region, tenant), ignored -> new State());
        var current=state.authority;
        applyState(region,tenant,new CacheRevisionStore.State(revision,current==null?0:current.controlVersion(),
                current==null?0:current.statsEpoch(),current!=null&&current.paused(),current==null?null:current.override()));
    }

    private boolean sameDataState(State state,long revision){
        var current=state.authority;return current==null?state.revision.get()==revision:current.revision()==revision&&!current.paused();
    }

    @Override public void invalidate(String region) { throw new IllegalStateException("Use the durable cache facade to invalidate shared data"); }

    @Override public void applyState(String region,long tenant,CacheRevisionStore.State authority) {
        registry.require(region);if(tenant<1)throw new IllegalArgumentException("Invalid cache scope");
        var state=states.get(new Scope(region,tenant),ignored->new State());
        try {
            if(!state.controlLock.tryLock(resilience.lockWait().toNanos(),TimeUnit.NANOSECONDS))throw new CacheUnavailableException();
        } catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new CacheUnavailableException();}
        try {
            var previous=state.authority;
            if(authority.revision()<state.revision.get()||authority.statsEpoch()<state.counters.epoch||
                    (previous!=null&&authority.controlVersion()<previous.controlVersion()))throw new IllegalStateException("Cache control authority cannot regress");
            state.revision.accumulateAndGet(authority.revision(),Math::max);state.authority=authority;
            if(authority.statsEpoch()>state.counters.epoch)state.counters=new CacheCounters(authority.statsEpoch());
            if(!call(()->store.synchronize(region,tenant,authority.revision())).available())throw new CacheUnavailableException();
        } finally {state.controlLock.unlock();}
    }

    @Override public CacheSnapshot snapshot(String region) {
        registry.require(region); long tenant = CurrentUser.tenantId();
        var state = states.getIfPresent(new Scope(region, tenant));
        var counters=state==null?new CacheCounters(0):state.counters;
        var stats = call(() -> store.snapshot(region, tenant));
        return new CacheSnapshot(region, "redis", stats.available() ? stats.value().size() : -1,
                stats.available() ? stats.value().bytes() : -1, counters.hits.sum(), counters.misses.sum(),
                counters.bypasses.sum(), counters.rejected.sum());
    }
    CacheCircuit.Status status() { return circuit.status(); }
    boolean available() { return available.get(); }
    long errors() { return errors.sum(); }
    private <T> Attempt<T> call(Supplier<T> operation) {
        var permit = circuit.acquire();
        if (permit == null) return new Attempt<>(false, null);
        try { T value = operation.get(); circuit.success(permit); available.set(true); return new Attempt<>(true, value); }
        catch (DataAccessException | IllegalStateException failure) { errors.increment(); available.set(false); circuit.failure(permit); return new Attempt<>(false, null); }
    }
}
