package com.sma.admin.common.cache;

public record CacheSnapshot(String region, String provider, long size, long bytes, long hits, long misses,
        long bypasses, long rejectedWrites) {}
