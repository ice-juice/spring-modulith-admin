package com.sma.extension.protocol;

import java.time.Instant;
import java.util.Objects;

public record SignedClaims(int format, Purpose purpose, String keyId, String issuer, String audience,
        String environment, String documentId, Instant issuedAt, Instant expiresAt, byte[] body) {
    public enum Purpose { ARTIFACT, RELEASE, LEASE, WORK_ORDER, SCAN }
    public SignedClaims {
        Checks.require(format == 1, "unsupported-signature-format");
        Objects.requireNonNull(purpose); Checks.id(keyId); Checks.id(issuer); Checks.id(audience); Checks.id(environment);
        Checks.text(documentId, 100); Objects.requireNonNull(issuedAt); Objects.requireNonNull(expiresAt);
        Checks.require(expiresAt.isAfter(issuedAt), "invalid-document-lifetime");
        body = Objects.requireNonNull(body).clone();
        Checks.require(body.length > 0 && body.length <= 500000, "invalid-signed-body");
    }
    @Override public byte[] body() { return body.clone(); }
}
