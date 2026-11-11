package com.sma.admin.identity.internal.service;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sma.identity.email")
public record IdentityEmailProperties(Duration lifetime,Duration grantLifetime,Duration retention,Integer maximumOutstanding,Integer maximumRetained,
        Integer maximumRateKeys,Integer tenantPerMinute,Integer tenantPerDay,Integer clientPerMinute,Integer sourcePerMinute,Integer targetPerDay,
        Integer guessesPerMinute,Integer guessesPerDay,Integer sourceGuessesPerMinute,Integer maximumFailures){
    public IdentityEmailProperties{
        lifetime=lifetime==null?Duration.ofMinutes(5):lifetime;grantLifetime=grantLifetime==null?Duration.ofMinutes(2):grantLifetime;retention=retention==null?Duration.ofDays(1):retention;
        maximumOutstanding=maximumOutstanding==null?1000:maximumOutstanding;maximumRetained=maximumRetained==null?10000:maximumRetained;
        maximumRateKeys=maximumRateKeys==null?5000:maximumRateKeys;tenantPerMinute=tenantPerMinute==null?100:tenantPerMinute;tenantPerDay=tenantPerDay==null?2000:tenantPerDay;
        clientPerMinute=clientPerMinute==null?20:clientPerMinute;sourcePerMinute=sourcePerMinute==null?5:sourcePerMinute;targetPerDay=targetPerDay==null?10:targetPerDay;
        guessesPerMinute=guessesPerMinute==null?500:guessesPerMinute;guessesPerDay=guessesPerDay==null?20000:guessesPerDay;
        sourceGuessesPerMinute=sourceGuessesPerMinute==null?30:sourceGuessesPerMinute;maximumFailures=maximumFailures==null?5:maximumFailures;
        if(lifetime.compareTo(Duration.ofMinutes(1))<0 || lifetime.compareTo(Duration.ofMinutes(10))>0 || grantLifetime.compareTo(Duration.ofSeconds(30))<0
                || grantLifetime.compareTo(lifetime)>0 || retention.compareTo(lifetime.plus(grantLifetime))<0 || retention.compareTo(Duration.ofDays(7))>0
                || maximumOutstanding<1 || maximumOutstanding>10000 || maximumRetained<maximumOutstanding || maximumRetained>100000
                || maximumRateKeys<4 || maximumRateKeys>10000 || tenantPerMinute<1 || tenantPerMinute>10000 || tenantPerDay<tenantPerMinute || tenantPerDay>100000
                || clientPerMinute<1 || clientPerMinute>tenantPerMinute || sourcePerMinute<1 || sourcePerMinute>tenantPerMinute || targetPerDay<1 || targetPerDay>50
                || guessesPerMinute<1 || guessesPerMinute>10000 || guessesPerDay<guessesPerMinute || guessesPerDay>1000000
                || sourceGuessesPerMinute<1 || sourceGuessesPerMinute>guessesPerMinute || maximumFailures<1 || maximumFailures>10)
            throw new IllegalArgumentException("Invalid email challenge budget");
    }
}
