package com.sma.admin.file.internal.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sma.file.local-transfer")
public record LocalTransferProperties(Integer maximumConcurrent,Duration deadline,Long minimumFreeBytes){
    public LocalTransferProperties{
        maximumConcurrent=maximumConcurrent==null?8:maximumConcurrent;deadline=deadline==null?Duration.ofMinutes(10):deadline;minimumFreeBytes=minimumFreeBytes==null?256L*1024*1024:minimumFreeBytes;
        if(maximumConcurrent<1 || maximumConcurrent>32 || deadline.compareTo(Duration.ofSeconds(1))<0 || deadline.compareTo(Duration.ofHours(2))>0 || minimumFreeBytes<0)
            throw new IllegalArgumentException("Invalid local transfer resources");
    }
}
