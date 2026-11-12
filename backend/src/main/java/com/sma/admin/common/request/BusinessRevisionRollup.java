package com.sma.admin.common.request;

import com.sma.admin.common.security.ActorContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded observation of business authority; never a substitute for a resource-local read. */
public final class BusinessRevisionRollup {
    public record State(long revision, Instant observedAt, boolean stale) {}
    private record Key(String resourceId, long resourceRevision, long tenantId, String region) {}
    private final Clock clock;
    private final Duration maxAge;
    private final int maxEntries;
    private final Map<Key, State> entries = new HashMap<>();

    public BusinessRevisionRollup(Clock clock, Duration maxAge, int maxEntries) {
        this.clock = Objects.requireNonNull(clock);
        if (maxAge == null || maxAge.isNegative() || maxAge.isZero() || maxEntries < 1)
            throw new IllegalArgumentException("Invalid business revision observation budget");
        this.maxAge = maxAge;
        this.maxEntries = maxEntries;
    }

    /** Polling and persistence are supplied by S6/S7; failures retain the last value but mark it stale. */
    public State sample(String resourceId, long resourceRevision, String region, BusinessLedger.Session session) {
        return sample(resourceId, resourceRevision, region, () -> session.authoritativeRevision(region));
    }

    public State sample(String resourceId, long resourceRevision, String region, java.util.function.LongSupplier read) {
        var key = key(resourceId, resourceRevision, region);
        Objects.requireNonNull(read);
        try {
            long revision = read.getAsLong();
            var state = new State(revision, clock.instant(), false);
            synchronized (this) {
                var previous = entries.get(key);
                if (previous != null && previous.revision() > state.revision()) {
                    var stale = new State(previous.revision(), previous.observedAt(), true);
                    entries.put(key, stale);
                    return stale;
                }
                if (!entries.containsKey(key) && entries.size() >= maxEntries) {
                    var oldest = entries.entrySet().stream().min(Comparator.comparing(e -> e.getValue().observedAt())).orElseThrow();
                    entries.remove(oldest.getKey());
                }
                entries.put(key, state);
            }
            return state;
        } catch (RuntimeException failure) {
            synchronized (this) {
                var previous = entries.get(key);
                if (previous == null) return new State(0, null, true);
                var stale = new State(previous.revision(), previous.observedAt(), true);
                entries.put(key, stale);
                return stale;
            }
        }
    }

    public synchronized State observe(String resourceId, long resourceRevision, String region) {
        var existing = entries.get(key(resourceId, resourceRevision, region));
        if (existing == null) return new State(0, null, true);
        boolean expired = Duration.between(existing.observedAt(), clock.instant()).compareTo(maxAge) > 0;
        return new State(existing.revision(), existing.observedAt(), existing.stale() || expired);
    }

    private static Key key(String resourceId, long resourceRevision, String region) {
        if (resourceId == null || !resourceId.matches("[a-z][a-z0-9_-]{0,63}") || resourceRevision < 1
                || region == null || region.length() > 96 || !region.matches("[a-z]+\\.[a-z][a-z0-9-]{0,79}"))
            throw new IllegalArgumentException("Invalid business revision key");
        return new Key(resourceId, resourceRevision, ActorContext.tenantId(), region);
    }
}
