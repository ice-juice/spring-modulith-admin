package com.sma.admin.auth.internal.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Key material is in a deployment-owned properties file, never in a database parameter. */
@ConfigurationProperties("sma.auth.security")
public record AccountSecurityProperties(String keyFile, String issuer) {
    public AccountSecurityProperties {
        keyFile = keyFile == null ? "" : keyFile;
        issuer = issuer == null || issuer.isBlank() ? "SMA Admin" : issuer;
        if (issuer.length() > 64 || issuer.contains(":")) throw new IllegalArgumentException("Invalid TOTP issuer");
    }
}
