package com.sma.admin.message.internal.service;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("sma.message.signals")
public record SocketSignalProperties(Boolean enabled,Integer maxEntries,Duration retention,Integer usersPerPoll,Long pollMillis) {
    public SocketSignalProperties {
        enabled=enabled==null?true:enabled;maxEntries=maxEntries==null?50000:maxEntries;
        retention=retention==null?Duration.ofHours(1):retention;usersPerPoll=usersPerPoll==null?500:usersPerPoll;pollMillis=pollMillis==null?1000L:pollMillis;
        if(maxEntries<1||maxEntries>100000||retention.compareTo(Duration.ofMinutes(1))<0||retention.compareTo(Duration.ofDays(1))>0
                ||usersPerPoll<1||usersPerPoll>1000||pollMillis<250||pollMillis>10000)throw new IllegalArgumentException("Invalid socket signal bounds");
    }
}
