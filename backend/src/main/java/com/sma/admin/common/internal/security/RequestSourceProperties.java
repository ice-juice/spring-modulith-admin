package com.sma.admin.common.internal.security;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deployment-only telemetry trust; does not extend Host/Proto trust or authentication authority. */
@ConfigurationProperties("sma.security.source")
public record RequestSourceProperties(Set<String> trustedProxies,Integer maximumHops,Integer maximumHeaderCharacters) {
    public RequestSourceProperties {
        if(trustedProxies!=null && trustedProxies.size()>64)throw new IllegalArgumentException("Too many source networks");
        trustedProxies=trustedProxies==null?Set.of():trustedProxies.stream().map(value->SourceIp.network(value).canonical()).collect(Collectors.toUnmodifiableSet());
        maximumHops=maximumHops==null?16:maximumHops;
        maximumHeaderCharacters=maximumHeaderCharacters==null?2048:maximumHeaderCharacters;
        if(maximumHops<1 || maximumHops>32 || maximumHeaderCharacters<64 || maximumHeaderCharacters>8192)
            throw new IllegalArgumentException("Invalid request source bounds");
    }
}
