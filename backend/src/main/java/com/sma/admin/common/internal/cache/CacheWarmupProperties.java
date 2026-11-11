package com.sma.admin.common.internal.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Explicit deployment acknowledgement: enable only after every task consumer understands PREWARM. */
@ConfigurationProperties("sma.cache.tasks.warmup")
public record CacheWarmupProperties(Boolean enabled) {
    public CacheWarmupProperties { enabled=enabled==null?false:enabled; }
}
