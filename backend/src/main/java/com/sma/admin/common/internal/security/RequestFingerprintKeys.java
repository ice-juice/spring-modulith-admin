package com.sma.admin.common.internal.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.sma.admin.common.request.RequestRejected;

/** Deployment-owned HMAC key ring; no key material appears in toString or errors. */
public final class RequestFingerprintKeys {
    private final String active;
    private final Map<String, byte[]> keys;
    public RequestFingerprintKeys(String active, String value, String previous) {
        if (active == null || !active.matches("[A-Za-z0-9_-]{1,32}")) throw new IllegalArgumentException("Invalid request fingerprint key id");
        this.active = active;
        var entries = new HashMap<String, byte[]>();
        if (!value.isBlank()) entries.put(active, decode(value));
        if (!previous.isBlank()) for (String entry : previous.split(";", -1)) {
            String[] pair = entry.split("=", 2);
            if (pair.length != 2 || !pair[0].matches("[A-Za-z0-9_-]{1,32}") || pair[0].equals(active) || entries.containsKey(pair[0]))
                throw new IllegalArgumentException("Invalid previous request fingerprint key entry");
            entries.put(pair[0], decode(pair[1]));
        }
        if (entries.size() > 8) throw new IllegalArgumentException("Too many request fingerprint keys");
        keys = Map.copyOf(entries);
    }
    public String activeId() { return active; }
    public com.sma.admin.common.deployment.DeploymentEvidence.Proof deploymentEvidence(
            com.sma.admin.common.deployment.DeploymentEvidence.Challenge challenge) {
        return com.sma.admin.common.deployment.DeploymentEvidence.keyRing("common.request-fingerprint", challenge, active, keys);
    }
    public String digest(String id, String context, byte[] input) {
        byte[] key = keys.get(id);
        if (key == null) throw new RequestRejected(RequestRejected.Reason.UNAVAILABLE, 0);
        try {
            var mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(context.getBytes(StandardCharsets.UTF_8)); return HexFormat.of().formatHex(mac.doFinal(input));
        } catch (java.security.GeneralSecurityException failure) { throw new IllegalStateException("Request fingerprint unavailable"); }
    }
    private static byte[] decode(String value) {
        try {
            byte[] key = Base64.getDecoder().decode(value);
            if (key.length != 32) throw new IllegalArgumentException(); return key;
        } catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Request fingerprint keys must be Base64 encoded 32-byte keys"); }
    }
    @Override public String toString() { return "RequestFingerprintKeys[REDACTED]"; }
}
