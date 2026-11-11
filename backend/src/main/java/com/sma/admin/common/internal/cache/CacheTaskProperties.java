package com.sma.admin.common.internal.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sma.cache.tasks")
public record CacheTaskProperties(Duration previewTtl,Integer maxRecordsPerTenant) {
    public CacheTaskProperties {
        previewTtl=previewTtl==null?Duration.ofMinutes(5):previewTtl;
        maxRecordsPerTenant=maxRecordsPerTenant==null?10000:maxRecordsPerTenant;
        if(previewTtl.compareTo(Duration.ofSeconds(30))<0||previewTtl.compareTo(Duration.ofMinutes(10))>0
                ||previewTtl.getNano()%1_000_000!=0||maxRecordsPerTenant<1||maxRecordsPerTenant>100000)
            throw new IllegalArgumentException("Invalid cache task limits");
    }
}
