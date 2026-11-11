package com.sma.admin.mail;

import com.sma.admin.common.ErrorCode;

public enum MailErrorCode implements ErrorCode {
    PROFILE_NOT_FOUND("mail.error.profile-not-found", 404),
    DUPLICATE_CODE("mail.error.duplicate-code", 409),
    VERSION_CONFLICT("mail.error.version-conflict", 409),
    INVALID_CONFIGURATION("mail.error.invalid-configuration", 400),
    INVALID_TEMPLATE("mail.error.invalid-template", 400),
    TEMPLATE_NOT_FOUND("mail.error.template-not-found", 404),
    DELIVERY_NOT_FOUND("mail.error.delivery-not-found", 404),
    IDEMPOTENCY_CONFLICT("mail.error.idempotency-conflict", 409),
    QUOTA_EXCEEDED("mail.error.quota-exceeded", 429),
    INVALID_STATE("mail.error.invalid-state", 409),
    RECIPIENT_SUPPRESSED("mail.error.recipient-suppressed", 409);

    private final String key;
    private final int status;
    MailErrorCode(String key, int status) { this.key = key; this.status = status; }
    @Override public String key() { return key; }
    @Override public int status() { return status; }
}
