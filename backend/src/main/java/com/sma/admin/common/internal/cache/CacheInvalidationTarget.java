package com.sma.admin.common.internal.cache;

/** Internal provider hook. Applying a durable version must be idempotent and must not move backwards. */
interface CacheInvalidationTarget {
    void applyRevision(String region, long tenant, long revision);
    default void applyState(String region,long tenant,com.sma.admin.common.internal.service.CacheRevisionStore.State state) {
        if(state.controlVersion()!=0)throw new IllegalStateException("Cache target does not support runtime control");
        applyRevision(region,tenant,state.revision());
    }
}
