package com.sma.admin.tenant.internal.service;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Trusted deployment configuration, never a tenant form field. Empty ingress addresses disable external verification. */
@ConfigurationProperties("sma.tenant.domain-verification")
public record DomainVerificationProperties(Set<String> ingressAddresses,Duration dnsTimeout,Duration connectTimeout,Duration totalTimeout) {
    public DomainVerificationProperties {
        ingressAddresses=ingressAddresses==null?Set.of():ingressAddresses.stream().map(value->DomainAddresses.literal(value).getHostAddress()).collect(Collectors.toUnmodifiableSet());
        dnsTimeout=dnsTimeout==null?Duration.ofSeconds(1):dnsTimeout;
        connectTimeout=connectTimeout==null?Duration.ofSeconds(2):connectTimeout;
        totalTimeout=totalTimeout==null?Duration.ofSeconds(12):totalTimeout;
        if(ingressAddresses.size()>32 || !bounded(dnsTimeout,100,2000) || !bounded(connectTimeout,100,5000)
                || !bounded(totalTimeout,1000,12000) || dnsTimeout.compareTo(totalTimeout)>0 || connectTimeout.compareTo(totalTimeout)>0)
            throw new IllegalArgumentException("Invalid domain verification budget");
    }
    private static boolean bounded(Duration value,long min,long max) { return value!=null && value.compareTo(Duration.ofMillis(min))>=0 && value.compareTo(Duration.ofMillis(max))<=0; }
}
