package com.sma.admin.auth.internal.service;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("sma.auth")
public record AuthProperties(Duration sessionTtl,Duration rememberTtl,boolean singleTerminal,Duration autoLockAfter,int passwordNoticeDays) {
    public AuthProperties {
        sessionTtl=sessionTtl==null?Duration.ofHours(8):sessionTtl;
        rememberTtl=rememberTtl==null?Duration.ofDays(7):rememberTtl;
        autoLockAfter=autoLockAfter==null?Duration.ZERO:autoLockAfter;
        if(autoLockAfter.isNegative()||passwordNoticeDays<0) throw new IllegalArgumentException("Lock and password reminder settings cannot be negative");
        if(sessionTtl.isNegative()||sessionTtl.isZero()||rememberTtl.isNegative()||rememberTtl.isZero()) throw new IllegalArgumentException("Session TTL must be positive");
    }
}
