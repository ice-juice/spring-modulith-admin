package com.sma.admin.file.internal.service;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("sma.file.cluster")
public record FileClusterProperties(Set<String> sharedResources,Duration probeTimeout) {
    public FileClusterProperties {
        sharedResources=sharedResources==null?Set.of():Set.copyOf(sharedResources);probeTimeout=probeTimeout==null?Duration.ofSeconds(2):probeTimeout;
        if(sharedResources.size()>17||sharedResources.stream().anyMatch(id->!id.matches("file\\.legacy|file\\.local\\.[a-z][a-z0-9-]{0,63}"))
                ||probeTimeout.compareTo(Duration.ofMillis(100))<0||probeTimeout.compareTo(Duration.ofSeconds(10))>0)throw new IllegalArgumentException("Invalid shared file deployment configuration");
    }
}
