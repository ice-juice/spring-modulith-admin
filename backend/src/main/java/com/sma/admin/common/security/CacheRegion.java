package com.sma.admin.common.security;
/** Modules expose tenant-scoped cache metadata and eviction, never cached values. */
public interface CacheRegion {
 record Snapshot(String id,String nameKey,long size,long hits,long misses){}
 String id();
 Snapshot snapshot();
 void clear();
}
