package com.sma.admin.auth.internal.service;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("sma.auth")
public record AuthProperties(Duration sessionTtl,Duration rememberTtl,boolean singleTerminal) {
    public AuthProperties {
        sessionTtl=sessionTtl==null?Duration.ofHours(8):sessionTtl;
        rememberTtl=rememberTtl==null?Duration.ofDays(7):rememberTtl;
        if(sessionTtl.isNegative()||sessionTtl.isZero()||rememberTtl.isNegative()||rememberTtl.isZero()) throw new IllegalArgumentException("Session TTL must be positive");
    }
}
