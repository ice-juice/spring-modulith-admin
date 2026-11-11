package com.sma.admin.common.cache;

/** Metadata and current eligibility only; execution is exclusively through CacheTasks. */
public interface CachePrewarming {
    record Eligibility(boolean available,String reason,CacheWarmupLoader.Spec specification) {}
    Eligibility inspect(String region);
}
