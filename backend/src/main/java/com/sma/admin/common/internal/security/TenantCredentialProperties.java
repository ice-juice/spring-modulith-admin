package com.sma.admin.common.internal.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deployment-owned key ring, not a shared Google/SMTP/cloud account. Blank disables secret operations. */
@ConfigurationProperties("sma.common.credentials")
public record TenantCredentialProperties(String keyFile) {
    public TenantCredentialProperties { keyFile = keyFile == null ? "" : keyFile.trim(); }
}
