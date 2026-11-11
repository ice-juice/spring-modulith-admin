package com.sma.admin.common.request;

import java.time.Instant;

/** Contains no business payload or submitted credentials. */
public record RequestReceipt(String operationId, String operation, State state, Instant createdAt, Instant updatedAt) {
    public enum State { RUNNING, SUCCEEDED, FAILED_FINAL, UNKNOWN }
}
