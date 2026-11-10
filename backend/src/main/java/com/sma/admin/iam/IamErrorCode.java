package com.sma.admin.iam;

import com.sma.admin.common.ErrorCode;

public enum IamErrorCode implements ErrorCode {
    INVALID_PARENT("iam.error.invalid-parent"),
    HAS_CHILDREN("iam.error.has-children"),
    REFERENCED("iam.error.referenced"),
    DUPLICATE_CODE("iam.error.duplicate-code"),
    DISABLED_PARENT("iam.error.disabled-parent"),
    PROTECTED_USER("iam.error.protected-user"),
    INVALID_PASSWORD("iam.error.invalid-password"),
    WEAK_PASSWORD("iam.error.weak-password");
    private final String key;
    IamErrorCode(String key) { this.key = key; }
    @Override public String key() { return key; }
    @Override public int status() { return 400; }
}
