package com.sma.admin.defense.internal.service;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("sma.defense.storage")
public record DefenseStorageProperties(Long maximumPlatformRecords,Long maximumTenantRecords,Long maximumTenantTotalRecords,
        Duration retention,Integer cleanupBatch) {
    public DefenseStorageProperties {
        maximumPlatformRecords=maximumPlatformRecords==null?100000L:maximumPlatformRecords;
        maximumTenantRecords=maximumTenantRecords==null?100000L:maximumTenantRecords;
        maximumTenantTotalRecords=maximumTenantTotalRecords==null?1000000L:maximumTenantTotalRecords;
        retention=retention==null?Duration.ofDays(30):retention;cleanupBatch=cleanupBatch==null?100:cleanupBatch;
        if(maximumPlatformRecords<1||maximumPlatformRecords>10000000||maximumTenantRecords<1||maximumTenantRecords>1000000
            ||maximumTenantTotalRecords<maximumTenantRecords||maximumTenantTotalRecords>10000000
            ||retention.compareTo(Duration.ofHours(1))<0||retention.compareTo(Duration.ofDays(365))>0||cleanupBatch<1||cleanupBatch>100)
            throw new IllegalArgumentException("Invalid defense storage budgets");
    }
}
