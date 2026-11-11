package com.sma.admin.file.internal.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Admission ceilings, not a claim that this size has passed transport or scanner capacity validation. */
@ConfigurationProperties("sma.file.scenes")
public record FileSceneLimits(Long maximumBytes,Integer maximumProfiles,Integer maximumRetentionDays) {
    public FileSceneLimits{
        maximumBytes=maximumBytes==null?20L*1024*1024*1024:maximumBytes;maximumProfiles=maximumProfiles==null?8:maximumProfiles;maximumRetentionDays=maximumRetentionDays==null?3650:maximumRetentionDays;
        if(maximumBytes<1 || maximumBytes>20L*1024*1024*1024 || maximumProfiles<1 || maximumProfiles>16 || maximumRetentionDays<1 || maximumRetentionDays>3650)throw new IllegalArgumentException("Invalid file scene limits");
    }
}
