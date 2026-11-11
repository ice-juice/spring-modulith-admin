package com.sma.admin.common.cache;

import java.time.Duration;
import java.util.Objects;

/** Hard upper bounds. A cache hit never extends either TTL. */
public record CachePolicy(Duration ttl, Duration negativeTtl, long maxEntries, long maxBytes, int maxEntryBytes) {
    public CachePolicy {
        Objects.requireNonNull(ttl, "ttl");
        Objects.requireNonNull(negativeTtl, "negativeTtl");
        if (ttl.compareTo(Duration.ofMillis(1)) < 0 || ttl.compareTo(Duration.ofDays(1)) > 0
                || negativeTtl.isNegative() || negativeTtl.compareTo(ttl) > 0
                || maxEntries < 1 || maxBytes < 1 || maxEntryBytes < 1 || maxEntryBytes > maxBytes) {
            throw new IllegalArgumentException("Invalid cache policy bounds");
        }
    }

    public static CachePolicy defaults() {
        return new CachePolicy(Duration.ofMinutes(5), Duration.ofSeconds(15), 10_000, 16L * 1024 * 1024, 256 * 1024);
    }
}
