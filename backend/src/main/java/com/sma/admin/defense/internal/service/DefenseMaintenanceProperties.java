package com.sma.admin.defense.internal.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sma.defense.maintenance")
public record DefenseMaintenanceProperties(Boolean enabled,Duration interval,Integer tenantBatch) {
    public DefenseMaintenanceProperties {
        enabled=enabled==null?true:enabled;interval=interval==null?Duration.ofSeconds(30):interval;tenantBatch=tenantBatch==null?8:tenantBatch;
        if(interval.compareTo(Duration.ofSeconds(1))<0||interval.compareTo(Duration.ofHours(1))>0||tenantBatch<1||tenantBatch>100)
            throw new IllegalArgumentException("Invalid security maintenance bounds");
    }
}
