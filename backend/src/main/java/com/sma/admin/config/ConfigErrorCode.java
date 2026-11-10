package com.sma.admin.config;
import com.sma.admin.common.ErrorCode;
public enum ConfigErrorCode implements ErrorCode {
    BUILTIN("config.error.builtin"),HAS_CHILDREN("config.error.has-children"),INVALID_PARENT("config.error.invalid-parent"),DUPLICATE("config.error.duplicate"),INVALID_VALUE("config.error.invalid-value");
    private final String key;
    ConfigErrorCode(String key) { this.key=key; }
    @Override public String key() { return key; }
    @Override public int status() { return 400; }
}
