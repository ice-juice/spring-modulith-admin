package com.sma.admin.rule;

import com.sma.admin.common.ErrorCode;

public enum RuleErrorCode implements ErrorCode {
    INVALID, CONFLICT, NOT_FOUND, UNAVAILABLE, UNSUPPORTED;

    @Override public String key() { return "rule.error." + name().toLowerCase(java.util.Locale.ROOT); }
    @Override public int status() { return this == NOT_FOUND ? 404 : this == CONFLICT ? 409 : 400; }
}
