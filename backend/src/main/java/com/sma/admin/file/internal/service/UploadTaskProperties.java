package com.sma.admin.file.internal.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tenant admission budgets. These ceilings are not measured transport/scanner capacity guarantees. */
@ConfigurationProperties("sma.file.tasks")
public record UploadTaskProperties(Duration lifetime,Duration retention,Long maximumReservedBytes,Integer maximumActive,Integer maximumRetained,
        Integer perMinute,Integer perDay,Long partBytes,Integer maximumParts,Integer maximumConcurrentParts){
    public UploadTaskProperties{
        lifetime=lifetime==null?Duration.ofHours(24):lifetime;retention=retention==null?Duration.ofDays(7):retention;
        maximumReservedBytes=maximumReservedBytes==null?100L*1024*1024*1024:maximumReservedBytes;
        maximumActive=maximumActive==null?100:maximumActive;maximumRetained=maximumRetained==null?10000:maximumRetained;
        perMinute=perMinute==null?30:perMinute;perDay=perDay==null?1000:perDay;partBytes=partBytes==null?16L*1024*1024:partBytes;
        maximumParts=maximumParts==null?10000:maximumParts;maximumConcurrentParts=maximumConcurrentParts==null?3:maximumConcurrentParts;
        if(lifetime.compareTo(Duration.ofMinutes(5))<0 || lifetime.compareTo(Duration.ofHours(24))>0 || retention.compareTo(lifetime)<0 || retention.compareTo(Duration.ofDays(30))>0
                || maximumReservedBytes<1 || maximumReservedBytes>10L*1024*1024*1024*1024 || maximumActive<1 || maximumActive>10000 || maximumRetained<maximumActive || maximumRetained>100000
                || perMinute<1 || perMinute>10000 || perDay<perMinute || perDay>100000 || partBytes<1024*1024 || partBytes>64L*1024*1024
                || maximumParts<1 || maximumParts>10000 || maximumConcurrentParts<1 || maximumConcurrentParts>8)throw new IllegalArgumentException("Invalid file task limits");
    }
}
