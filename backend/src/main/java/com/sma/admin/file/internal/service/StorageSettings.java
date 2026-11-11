package com.sma.admin.file.internal.service;

import java.util.Map;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deployment-owned roots. Tenant administrators choose aliases, never arbitrary server filesystem paths. */
@ConfigurationProperties("sma.file.storage")
public record StorageSettings(Map<String,String> localRoots) {
    public StorageSettings {
        localRoots=localRoots==null?Map.of("default","data/managed-files"):Map.copyOf(localRoots);
        if(localRoots.isEmpty() || localRoots.size()>16)throw new IllegalArgumentException("Invalid managed storage roots");
        for(var entry:localRoots.entrySet()){
            if(!entry.getKey().matches("[a-z][a-z0-9-]{0,63}") || entry.getValue()==null || entry.getValue().isBlank())throw new IllegalArgumentException("Invalid managed storage root");
            Path.of(entry.getValue());
        }
    }
}
