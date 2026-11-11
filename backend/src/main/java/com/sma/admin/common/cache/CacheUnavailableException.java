package com.sma.admin.common.cache;

/** Transient infrastructure backpressure, translated to a localized HTTP 503 by the host. */
public final class CacheUnavailableException extends RuntimeException {
    public CacheUnavailableException() { super("Cache source capacity is temporarily unavailable"); }
}
