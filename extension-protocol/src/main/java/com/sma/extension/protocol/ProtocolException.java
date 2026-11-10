package com.sma.extension.protocol;

/** Stable machine-readable errors. Messages must never contain secrets or uploaded source. */
public final class ProtocolException extends RuntimeException {
    private final String code;
    public ProtocolException(String code) { super(code); this.code = code; }
    public ProtocolException(String code, Throwable cause) { super(code, cause); this.code = code; }
    public String code() { return code; }
}
