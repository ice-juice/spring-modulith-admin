package com.sma.admin.common.internal.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.security.CurrentUser;

/** Bounded single-process implementation. Shared backends follow in P08 S2. */
public final class LocalManagedCache implements ManagedCache, CacheInvalidationTarget {
    private record Scope(String region, long tenant) {}
    private record Key(Scope scope, String portal, long user, int schema, String digest, String type, String generation) {}
    private record Entry(byte[] payload, boolean absent, Instant expiresAt, long ttlNanos, long weight) {}
    private static final class State {
        String generation = UUID.randomUUID().toString();
        long revision = -1;
        long controlVersion=-1;
        volatile boolean paused;
        CachePolicy policy;
        long entries,bytes;
        volatile CacheCounters counters=new CacheCounters(0);
    }
    private static final class Budget { long entries, bytes; }
    private final CacheRegistry registry;
    private final CacheProperties properties;
    private final Map<String, CachePolicy> policies;
    private final Map<String, Budget> budgets;
    private final Cache<Scope, State> states;
    private final Cache<Key, Entry> entries;
    private final ReentrantLock[] loadLocks = new ReentrantLock[256];
    private final CacheResilienceProperties resilience;
    private final Object admission = new Object();
    private long usedBytes;
    private final Clock clock;
    // Cache representation is independent of HTTP JSON customization and type metadata.
    private final CacheCodec codec = new CacheCodec();

    public LocalManagedCache(CacheRegistry registry, CacheProperties properties, Clock clock) {
        this(registry, properties, clock, CacheResilienceProperties.defaults());
    }

    LocalManagedCache(CacheRegistry registry, CacheProperties properties, Clock clock, CacheResilienceProperties resilience) {
        this.registry = registry; this.properties = properties; this.clock = clock;
        this.resilience = resilience;
        if (properties.provider() == CacheProperties.Provider.REDIS) {
            throw new IllegalArgumentException("Redis provider is not available until P08 S2 is installed");
        }
        if (properties.deploymentMode() != CacheProperties.DeploymentMode.SINGLE) {
            throw new IllegalArgumentException("Multi-instance deployment requires P08 reliable invalidation and cluster acceptance");
        }
        properties.regions().keySet().forEach(registry::require);
        policies = registry.regions().stream().filter(r -> r.kind() == CacheDescriptor.Kind.DATA)
                .collect(Collectors.toUnmodifiableMap(CacheDescriptor::id, r -> properties.policy(r.id(), r.policy())));
        budgets = policies.keySet().stream().collect(Collectors.toUnmodifiableMap(id -> id, id -> new Budget()));
        states = Caffeine.newBuilder().maximumSize(properties.maxScopes()).expireAfterAccess(Duration.ofHours(1)).build();
        entries = Caffeine.newBuilder().maximumWeight(properties.maxBytes())
                .weigher((Key key, Entry entry) -> (int) Math.min(Integer.MAX_VALUE, entry.weight()))
                // Caffeine's default monotonic ticker bounds lifetime even if wall-clock time moves backwards.
                .expireAfter(new Expiry<Key, Entry>() {
                    public long expireAfterCreate(Key key, Entry value, long now) { return value.ttlNanos(); }
                    public long expireAfterUpdate(Key key, Entry value, long now, long remaining) { return value.ttlNanos(); }
                    public long expireAfterRead(Key key, Entry value, long now, long remaining) { return remaining; }
                }).executor(Runnable::run).removalListener((Key key, Entry value, com.github.benmanes.caffeine.cache.RemovalCause cause) -> {
                    if (key != null && value != null) synchronized (admission) {
                        var budget = budgets.get(key.scope().region()); budget.entries--; budget.bytes -= value.weight(); usedBytes -= value.weight();
                        var scopeState=states.getIfPresent(key.scope());
                        if(scopeState!=null&&scopeState.generation.equals(key.generation())){scopeState.entries--;scopeState.bytes-=value.weight();}
                    }
                }).build();
        for (int i = 0; i < loadLocks.length; i++) loadLocks[i] = new ReentrantLock();
    }

    @Override public <T> T get(String region, String key, Class<T> valueType, Supplier<T> loader, Predicate<T> cacheable) {
        if (loader == null || cacheable == null) throw new IllegalArgumentException("Invalid cache loader");
        var lookup = CacheLookup.resolve(registry, region, key, valueType);
        var descriptor = lookup.descriptor();
        var scope = new Scope(region, lookup.tenant());
        State state = states.get(scope, ignored -> new State());
        var counters=state.counters;
        String portal = lookup.portal(); long user = lookup.user();
        if (properties.provider() == CacheProperties.Provider.NONE || CacheTransactions.bypass() || state.paused) {
            counters.bypasses.increment(); return loader.get();
        }
        final Key address;
        final CachePolicy policy;
        synchronized (admission) {
            address = new Key(scope, portal, user, descriptor.schemaVersion(), lookup.digest(), valueType.getName(), state.generation);
            policy=state.policy==null?policies.get(region):state.policy;
        }
        var loadLock = loadLocks[(address.hashCode() & Integer.MAX_VALUE) % loadLocks.length];
        try {
            if (!loadLock.tryLock(resilience.lockWait().toNanos(), TimeUnit.NANOSECONDS)) {
                counters.bypasses.increment(); return loader.get();
            }
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new CacheUnavailableException(); }
        try {
            Entry found = entries.getIfPresent(address);
            boolean current;
            synchronized (admission) { current = !state.paused&&states.getIfPresent(scope) == state && address.generation().equals(state.generation); }
            if (current && found != null && found.expiresAt().isAfter(clock.instant())) {
                try {
                    T value = found.absent() ? null : codec.decode(found.payload(), valueType);
                    if (cacheable.test(value)) { counters.hits.increment(); return value; }
                    entries.invalidate(address);
                } catch (RuntimeException invalid) { entries.invalidate(address); }
            }
            counters.misses.increment();
            T result = loader.get(); // Exceptions propagate and are never cached as missing values.
            if (!cacheable.test(result)) { counters.bypasses.increment(); return result; }
            Duration ttl = result == null ? policy.negativeTtl() : policy.ttl();
            if (ttl.isZero()) return result;
            final byte[] payload;
            try { payload = result == null ? new byte[0] : codec.encode(result); }
            catch (RuntimeException encodingFailure) { counters.rejected.increment(); return result; }
            long weight = payload.length + 512L + 2L * portal.length() + 2L * valueType.getName().length();
            if (payload.length > policy.maxEntryBytes()) { counters.rejected.increment(); return result; }
            long ttlNanos = Math.max(1, (long) (ttl.toNanos() * (1 - properties.ttlJitter() * ThreadLocalRandom.current().nextDouble())));
            entries.cleanUp();
            synchronized (admission) {
                if (state.paused||states.getIfPresent(scope) != state || !address.generation().equals(state.generation)) return result;
                entries.invalidate(address);
                Budget budget = budgets.get(region);
                var deployment=policies.get(region);
                if (budget.entries >= deployment.maxEntries() || budget.bytes + weight > deployment.maxBytes()
                        ||state.entries>=policy.maxEntries()||state.bytes+weight>policy.maxBytes()
                        || usedBytes + weight > properties.maxBytes()) {
                    counters.rejected.increment(); return result;
                }
                budget.entries++; budget.bytes += weight; usedBytes += weight;
                state.entries++;state.bytes+=weight;
                entries.put(address, new Entry(payload, result == null, clock.instant().plusNanos(ttlNanos), ttlNanos, weight));
            }
            return result;
        } finally { loadLock.unlock(); }
    }

    @Override public void invalidate(String region) {
        var descriptor = registry.require(region);
        if (descriptor.kind() != CacheDescriptor.Kind.DATA || !descriptor.operations().contains(CacheDescriptor.Operation.CLEAR)) {
            throw new IllegalArgumentException("Region does not allow cache invalidation");
        }
        var scope = new Scope(region, CurrentUser.tenantId());
        CacheTransactions.afterCommit(() -> clear(scope));
    }

    private void clear(Scope scope) {
        synchronized (admission) {
            var state = states.getIfPresent(scope);
            if (state != null) {state.generation = UUID.randomUUID().toString();state.entries=0;state.bytes=0;}
            entries.asMap().keySet().removeIf(key -> key.scope().equals(scope));
        }
    }

    @Override public void applyState(String region,long tenant,com.sma.admin.common.internal.service.CacheRevisionStore.State authority) {
        registry.require(region);if(tenant<1)throw new IllegalArgumentException("Invalid cache scope");
        var scope=new Scope(region,tenant);
        synchronized(admission){
            var state=states.get(scope,ignored->new State());
            if(authority.revision()<state.revision||authority.controlVersion()<state.controlVersion||authority.statsEpoch()<state.counters.epoch)
                throw new IllegalStateException("Cache authority state cannot regress");
            var policy=CacheRuntimePolicy.effective(policies.get(region),authority.override());
            if(authority.revision()!=state.revision||state.paused!=authority.paused()||(state.policy!=null&&!policy.equals(state.policy)))clear(scope);
            state.revision=authority.revision();state.controlVersion=authority.controlVersion();state.paused=authority.paused();state.policy=policy;
            if(authority.statsEpoch()>state.counters.epoch)state.counters=new CacheCounters(authority.statsEpoch());
        }
    }

    @Override public void applyRevision(String region, long tenant, long revision) {
        registry.require(region);
        if (tenant < 1 || revision < 0) throw new IllegalArgumentException("Invalid durable cache scope");
        var scope = new Scope(region, tenant);
        synchronized (admission) {
            var state = states.get(scope, ignored -> new State());
            if (revision < state.revision) throw new IllegalStateException("Cache revision cannot regress");
            if (revision != state.revision) { clear(scope); state.revision = revision; }
        }
    }

    @Override public CacheSnapshot snapshot(String region) {
        registry.require(region);
        var scope = new Scope(region, CurrentUser.tenantId());
        var state = states.getIfPresent(scope);
        if (state == null) return new CacheSnapshot(region, properties.provider().name().toLowerCase(java.util.Locale.ROOT), 0, 0, 0, 0, 0, 0);
        var counters=state.counters;
        long size = 0, bytes = 0;
        synchronized (admission) {
            for (var entry : entries.asMap().entrySet()) {
                if (entry.getKey().scope().equals(scope) && entry.getKey().generation().equals(state.generation)
                        && entry.getValue().expiresAt().isAfter(clock.instant())) {
                    size++; bytes += entry.getValue().payload().length;
                }
            }
        }
        return new CacheSnapshot(region, properties.provider().name().toLowerCase(java.util.Locale.ROOT), size, bytes,
                counters.hits.sum(), counters.misses.sum(), counters.bypasses.sum(), counters.rejected.sum());
    }

}
