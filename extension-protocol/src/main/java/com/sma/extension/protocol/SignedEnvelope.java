package com.sma.extension.protocol;

/** Detached signature of the exact encoded claims bytes. The key id is also covered inside the signed claims. */
public record SignedEnvelope(String keyId, String payload, String signature) {
    public SignedEnvelope {
        Checks.id(keyId);
        Checks.require(payload != null && payload.length() <= 900000 && signature != null && signature.length() <= 256, "invalid-envelope");
    }
}
