package com.sma.admin.identity.internal.service;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sma.identity.login")
public record IdentityLoginProperties(Duration lifetime,Duration retention,Integer maximumOutstanding,Integer maximumRetained,
                                      Integer maximumRateKeys,Integer tenantPerMinute,Integer tenantPerDay,Integer clientPerMinute,Integer sourcePerMinute){
    public IdentityLoginProperties{
        lifetime=lifetime==null?Duration.ofMinutes(5):lifetime;retention=retention==null?Duration.ofDays(1):retention;
        maximumOutstanding=maximumOutstanding==null?1000:maximumOutstanding;maximumRetained=maximumRetained==null?10000:maximumRetained;
        maximumRateKeys=maximumRateKeys==null?2000:maximumRateKeys;tenantPerMinute=tenantPerMinute==null?200:tenantPerMinute;
        tenantPerDay=tenantPerDay==null?5000:tenantPerDay;clientPerMinute=clientPerMinute==null?20:clientPerMinute;sourcePerMinute=sourcePerMinute==null?10:sourcePerMinute;
        if(lifetime.compareTo(Duration.ofSeconds(30))<0 || lifetime.compareTo(Duration.ofMinutes(10))>0
                || retention.compareTo(lifetime)<0 || retention.compareTo(Duration.ofDays(7))>0
                || maximumOutstanding<1 || maximumOutstanding>10000 || maximumRetained<maximumOutstanding || maximumRetained>100000
                || maximumRateKeys<2 || maximumRateKeys>10000 || tenantPerMinute<1 || tenantPerMinute>10000
                || tenantPerDay<tenantPerMinute || tenantPerDay>100000 || clientPerMinute<1 || clientPerMinute>tenantPerMinute
                || sourcePerMinute<1 || sourcePerMinute>tenantPerMinute)throw new IllegalArgumentException("Invalid identity login budget");
    }
}
