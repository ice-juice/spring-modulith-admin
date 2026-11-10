package com.sma.admin.auth;
import com.sma.admin.common.ErrorCode;
public enum AuthErrorCode implements ErrorCode {
    INVALID_CREDENTIALS("auth.error.invalid-credentials",400),
    INVALID_CAPTCHA("auth.error.invalid-captcha",400),
    RATE_LIMITED("auth.error.rate-limited",429),
    SCREEN_LOCKED("auth.error.screen-locked",423);
    private final String key; private final int status;
    AuthErrorCode(String key,int status) { this.key=key; this.status=status; }
    @Override public String key() { return key; }
    @Override public int status() { return status; }
}
