package com.sma.admin.common.request;

import com.sma.admin.common.ErrorCode;

public class RequestRejected extends RuntimeException {
    public enum Reason implements ErrorCode {
        KEY_REQUIRED("common.request.key-required", 400),
        PROCESSING("common.request.processing", 202),
        UNKNOWN("common.request.unknown", 202),
        COMPLETED("common.request.completed", 409),
        FAILED("common.request.failed", 409),
        TOO_LARGE("common.request.too-large", 413),
        RATE_LIMITED("common.request.rate-limited", 429),
        BUSY("common.request.busy", 409),
        CONFLICT("common.request.conflict", 409),
        UNAVAILABLE("common.request.unavailable", 503),
        NOT_FOUND("common.error.not-found", 404);
        private final String key; private final int status;
        Reason(String key, int status) { this.key = key; this.status = status; }
        @Override public String key() { return key; }
        @Override public int status() { return status; }
    }
    private final Reason reason;
    private final long retryAfterSeconds;
    public RequestRejected(Reason reason, long retryAfterSeconds) {
        super(reason.key()); this.reason = reason; this.retryAfterSeconds = retryAfterSeconds;
    }
    public Reason reason() { return reason; }
    public long retryAfterSeconds() { return retryAfterSeconds; }
}
