package com.sma.admin.common.internal.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sma.cache.tasks.dispatch")
public record CacheTaskDispatchProperties(Boolean enabled,Integer tenantsPerTick,Integer itemsPerTenant,Duration lease) {
    public CacheTaskDispatchProperties {
        enabled=enabled==null?true:enabled;
        tenantsPerTick=tenantsPerTick==null?16:tenantsPerTick;
        itemsPerTenant=itemsPerTenant==null?4:itemsPerTenant;
        lease=lease==null?Duration.ofSeconds(30):lease;
        if(tenantsPerTick<1||tenantsPerTick>32||itemsPerTenant<1||itemsPerTenant>8
                ||lease.compareTo(Duration.ofSeconds(30))<0||lease.compareTo(Duration.ofMinutes(5))>0||lease.getNano()!=0)
            throw new IllegalArgumentException("Invalid cache dispatcher bounds");
    }
}
