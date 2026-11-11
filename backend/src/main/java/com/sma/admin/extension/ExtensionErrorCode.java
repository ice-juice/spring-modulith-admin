package com.sma.admin.extension;

import com.sma.admin.common.ErrorCode;

public enum ExtensionErrorCode implements ErrorCode {
    UNAVAILABLE("extension.error.unavailable",503),
    REJECTED("extension.error.rejected",400),
    EXECUTION_FAILED("extension.error.execution-failed",500),
    CONSOLE_UNCONFIGURED("extension.error.console-unconfigured",503),
    CONSOLE_UNAVAILABLE("extension.error.console-unavailable",503),
    CONSOLE_PATH_DENIED("extension.error.console-path-denied",400);
    private final String key; private final int status;
    ExtensionErrorCode(String key,int status) { this.key=key;this.status=status; }
    @Override public String key() { return key; }
    @Override public int status() { return status; }
}
