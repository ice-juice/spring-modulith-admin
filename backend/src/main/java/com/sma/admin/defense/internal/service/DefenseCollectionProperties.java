package com.sma.admin.defense.internal.service;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("sma.defense.collection")
public record DefenseCollectionProperties(boolean enabled,String directory,String keyFile,Integer memoryRecords,Integer diskRecords,
        Long diskBytes,Integer perSecond,Integer batch,Duration retryDelay,String scope) {
    public DefenseCollectionProperties {
        directory=directory==null?"":directory;keyFile=keyFile==null?"":keyFile;scope=scope==null?"":scope;
        memoryRecords=memoryRecords==null?1024:memoryRecords;diskRecords=diskRecords==null?10000:diskRecords;
        diskBytes=diskBytes==null?32L*1024*1024:diskBytes;perSecond=perSecond==null?1000:perSecond;
        batch=batch==null?32:batch;retryDelay=retryDelay==null?Duration.ofSeconds(5):retryDelay;
        if(enabled&&(directory.isBlank()||keyFile.isBlank()||!scope.matches("[a-z][a-z0-9.-]{0,95}"))||memoryRecords<4||memoryRecords>10000||diskRecords<4||diskRecords>100000
                ||diskBytes<8192||diskBytes>1024L*1024*1024||perSecond<1||perSecond>10000||batch<1||batch>100
                ||retryDelay.compareTo(Duration.ofMillis(250))<0||retryDelay.compareTo(Duration.ofMinutes(5))>0)
            throw new IllegalArgumentException("Invalid defense collection settings");
    }
    @Override public String toString(){return "DefenseCollectionProperties[enabled="+enabled+",paths=REDACTED]";}
}
