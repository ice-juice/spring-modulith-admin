package com.sma.extension.api;

import java.util.Objects;

public record InvocationIdentity(long tenantId, String subject, Kind kind) {
    public enum Kind { USER, APPLICATION, JOB }
    public InvocationIdentity {
        if (tenantId <= 0) throw new IllegalArgumentException("Invalid tenant");
        Objects.requireNonNull(subject); Objects.requireNonNull(kind);
        if (subject.isBlank() || subject.length() > 200) throw new IllegalArgumentException("Invalid subject");
    }
}
