package com.sma.admin.common.internal.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sma.security.requests")
public record RequestGovernanceProperties(String fingerprintKey, String fingerprintKeyId, String previousFingerprintKeys,
                                          Long maxRecordsPerTenant, Integer maxConcurrent, java.time.Duration minimumWriteInterval) {
    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public RequestGovernanceProperties {
        fingerprintKey = fingerprintKey == null ? "" : fingerprintKey;
        fingerprintKeyId = fingerprintKeyId == null ? "v1" : fingerprintKeyId;
        previousFingerprintKeys = previousFingerprintKeys == null ? "" : previousFingerprintKeys;
        maxRecordsPerTenant = maxRecordsPerTenant == null ? 100000L : maxRecordsPerTenant;
        maxConcurrent = maxConcurrent == null ? 8 : maxConcurrent;
        minimumWriteInterval = minimumWriteInterval == null ? java.time.Duration.ofMillis(500) : minimumWriteInterval;
        if (maxRecordsPerTenant < 1 || maxRecordsPerTenant > 1000000 || maxConcurrent < 1 || maxConcurrent > 256)
            throw new IllegalArgumentException("Invalid request governance capacity");
        if (minimumWriteInterval.toMillis() < 100 || minimumWriteInterval.compareTo(java.time.Duration.ofMinutes(1)) > 0)
            throw new IllegalArgumentException("Invalid minimum write interval");
    }
    public RequestGovernanceProperties(String key,String id,String previous,Long capacity,Integer concurrent) {
        this(key,id,previous,capacity,concurrent,null);
    }
    @Override public String toString() { return "RequestGovernanceProperties[credentials=REDACTED, maxConcurrent=" + maxConcurrent + "]"; }
}
