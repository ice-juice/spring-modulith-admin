package com.sma.admin.auth;
import com.sma.admin.common.ErrorCode;
public enum AuthErrorCode implements ErrorCode {
    INVALID_CREDENTIALS("auth.error.invalid-credentials",400),
    INVALID_CAPTCHA("auth.error.invalid-captcha",400),
    RATE_LIMITED("auth.error.rate-limited",429),
    SCREEN_LOCKED("auth.error.screen-locked",423),
    SECURITY_UNAVAILABLE("auth.error.security-unavailable",503),
    INVALID_CHALLENGE("auth.error.invalid-challenge",400),
    INVALID_TOTP("auth.error.invalid-totp",400),
    SECURITY_PASSWORD_INVALID("auth.error.security-password-invalid",400),
    SECURITY_PASSWORD_FORMAT("auth.error.security-password-format",400),
    SECURITY_STATE_CHANGED("auth.error.security-state-changed",409);
    private final String key; private final int status;
    AuthErrorCode(String key,int status) { this.key=key; this.status=status; }
    @Override public String key() { return key; }
    @Override public int status() { return status; }
}
