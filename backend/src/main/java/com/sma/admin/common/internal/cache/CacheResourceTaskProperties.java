package com.sma.admin.common.internal.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Enable only after all task readers/consumers understand native bindings and UNKNOWN results. */
@ConfigurationProperties("sma.cache.tasks.resources")
public record CacheResourceTaskProperties(Boolean enabled) {
    public CacheResourceTaskProperties {enabled=enabled==null?false:enabled;}
}
