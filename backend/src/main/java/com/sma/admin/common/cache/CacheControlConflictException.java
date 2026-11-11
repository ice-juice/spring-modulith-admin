package com.sma.admin.common.cache;
/** A stale editor must reload; callers must not silently retry with a newer version. */
public final class CacheControlConflictException extends RuntimeException {
    public CacheControlConflictException(){super("Cache control version conflict");}
}
