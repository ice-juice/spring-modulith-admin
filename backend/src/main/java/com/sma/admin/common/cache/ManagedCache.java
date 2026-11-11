package com.sma.admin.common.cache;

import java.util.function.Supplier;
import java.util.function.Predicate;

/**
 * The host derives scope from trusted context. Null denotes a missing source value, never an error.
 * A loader must be read-only and must not publish uncommitted or historical transaction snapshots.
 * Cache access does not authorize the caller to access the loaded object.
 */
public interface ManagedCache {
    default <T> T get(String region, String key, Class<T> valueType, Supplier<T> loader) {
        return get(region, key, valueType, loader, value -> true);
    }

    /** An owner may narrow admission (for example secret-marked records), never widen policy bounds. */
    <T> T get(String region, String key, Class<T> valueType, Supplier<T> loader, Predicate<T> cacheable);

    /** Invalidate the current tenant after commit; rollback must leave existing committed entries intact. */
    void invalidate(String region);

    /** Current tenant; counters belong to this process and observation lifetime. */
    CacheSnapshot snapshot(String region);
}
