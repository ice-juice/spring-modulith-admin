package com.sma.admin.tenant;

import com.sma.admin.common.ErrorCode;

public enum TenantErrorCode implements ErrorCode {
    INVALID("tenant.error.invalid"), DUPLICATE("tenant.error.duplicate"),
    STALE("tenant.error.stale"), STATE("tenant.error.state"), ENTRY_UNAVAILABLE("tenant.error.entry-unavailable"), BRAND_INVALID("tenant.error.brand-invalid"), BRAND_CAPACITY("tenant.error.brand-capacity"), BRAND_MIGRATION_INVALID("tenant.error.brand-migration-invalid"),
    DOMAIN_VERIFICATION_DISABLED("tenant.error.domain-verification-disabled"), DOMAIN_CHALLENGE_EXPIRED("tenant.error.domain-challenge-expired"),
    DOMAIN_CHECK_RUNNING("tenant.error.domain-check-running"), DOMAIN_CHECK_CAPACITY("tenant.error.domain-check-capacity"),
    DOMAIN_VERIFICATION_REQUIRED("tenant.error.domain-verification-required"), DOMAIN_LAST_ENTRY("tenant.error.domain-last-entry");
    private final String key;
    TenantErrorCode(String key) { this.key=key; }
    @Override public String key() { return key; }
}
