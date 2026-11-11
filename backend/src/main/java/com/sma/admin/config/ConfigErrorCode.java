package com.sma.admin.config;
import com.sma.admin.common.ErrorCode;
public enum ConfigErrorCode implements ErrorCode {
    BUILTIN("config.error.builtin"),HAS_CHILDREN("config.error.has-children"),INVALID_PARENT("config.error.invalid-parent"),DUPLICATE("config.error.duplicate"),INVALID_VALUE("config.error.invalid-value"),SECRET_POLICY("config.error.secret-policy"),BRAND_MANAGED("config.error.brand-managed"),
    SHARED_INVALID("config.error.shared-invalid"),SHARED_STALE("config.error.shared-stale"),SHARED_CAPACITY("config.error.shared-capacity"),SHARED_UNAVAILABLE("config.error.shared-unavailable"),
    DICTIONARY_CODE_CONFLICT("config.error.dictionary-code-conflict"),DICTIONARY_CODE_CAPACITY("config.error.dictionary-code-capacity");
    private final String key;
    ConfigErrorCode(String key) { this.key=key; }
    @Override public String key() { return key; }
    @Override public int status() { return this == SHARED_UNAVAILABLE ? 503 : 400; }
}
