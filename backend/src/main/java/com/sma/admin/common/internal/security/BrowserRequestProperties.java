package com.sma.admin.common.internal.security;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import com.sma.admin.common.security.WebAddresses;

@ConfigurationProperties("sma.security.browser")
public record BrowserRequestProperties(Set<String> allowedOrigins,Integer maximumJsonBytes,Integer maximumConcurrentJson,Integer maximumConcurrentAnonymousJson) {
    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public BrowserRequestProperties {
        allowedOrigins = allowedOrigins == null ? Set.of() : allowedOrigins.stream().map(value ->
                WebAddresses.origin(value).orElseThrow(() -> new IllegalArgumentException("Browser origins must be exact http(s) origins")))
                .collect(Collectors.toUnmodifiableSet());
        maximumJsonBytes=maximumJsonBytes==null?2*1024*1024:maximumJsonBytes;
        maximumConcurrentJson=maximumConcurrentJson==null?16:maximumConcurrentJson;
        maximumConcurrentAnonymousJson=maximumConcurrentAnonymousJson==null?4:maximumConcurrentAnonymousJson;
        if(maximumJsonBytes<1024||maximumJsonBytes>16*1024*1024||maximumConcurrentJson<1||maximumConcurrentJson>128
                ||maximumConcurrentAnonymousJson<1||maximumConcurrentAnonymousJson>16)
            throw new IllegalArgumentException("Invalid browser JSON bounds");
    }
    public BrowserRequestProperties(Set<String> origins){this(origins,null,null,null);}
    public BrowserRequestProperties(Set<String> origins,Integer bytes,Integer concurrent){this(origins,bytes,concurrent,concurrent==null?null:Math.min(4,concurrent));}
}
