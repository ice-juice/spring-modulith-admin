package com.sma.admin.common.cache;

import java.util.function.Predicate;
import java.util.function.Supplier;

/** Reads rebuildable business data on a host-selected storage session, outside an existing transaction.
 * Authorize the object before calling, including on cache hits. The loader performs only business Mapper reads.
 * Writers invalidate with BusinessLedger.Session.invalidate in their business transaction.
 */
public interface BusinessCache {
    default <T> T get(String region,String key,Class<T> type,Supplier<T> loader) {
        return get(region,key,type,loader,value->true);
    }
    <T> T get(String region,String key,Class<T> type,Supplier<T> loader,Predicate<T> cacheable);
}
