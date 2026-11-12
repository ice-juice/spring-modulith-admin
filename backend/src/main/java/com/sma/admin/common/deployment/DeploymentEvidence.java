package com.sma.admin.common.deployment;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import com.sma.admin.common.security.TenantEntryScope;

/** Bounded v1 evidence encoding. Only digests cross the SPI; input keys are neither retained nor modified. */
public final class DeploymentEvidence {
    private DeploymentEvidence() { }

    public record Challenge(String clusterHash, String manifestHash, String nonce) {
        public Challenge {
            if (!hash(clusterHash) || !hash(manifestHash) || !hash(nonce)) throw invalid();
        }
        @Override public String toString() { return "DeploymentChallenge[redacted]"; }
    }

    public record Proof(String domain, String digest) {
        public Proof { requireDomain(domain); if (digest != null && !hash(digest)) throw invalid(); }
        public boolean available() { return digest != null; }
        @Override public String toString() { return "DeploymentProof[domain=" + domain + ",available=" + available() + "]"; }
    }

    public static Proof unavailable(String domain) { requireHost(); return new Proof(domain, null); }

    /** Each key is a high-entropy 256-bit deployment key. Passwords/tokens are not accepted as this contract. */
    public static Proof keyRing(String domain, Challenge challenge, String active, Map<String, byte[]> keys) {
        requireHost(); requireDomain(domain);
        if (active == null || active.isBlank() || keys == null || !keys.containsKey(active) || keys.isEmpty())
            return unavailable(domain);
        if (!identifier(active) || keys.size() > 16) throw invalid();
        var fields = new TreeMap<String, String>();
        fields.put("active", active);
        for (var entry : keys.entrySet()) {
            if (!identifier(entry.getKey()) || entry.getValue() == null || entry.getValue().length != 32) throw invalid();
            byte[] signed = null;
            try {
                var mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(entry.getValue(), "HmacSHA256"));
                signed = mac.doFinal(encode(domain, challenge, Map.of("purpose", "key-proof", "key-id", entry.getKey())));
                fields.put("key." + entry.getKey(), HexFormat.of().formatHex(signed));
            } catch (GeneralSecurityException failure) { throw new IllegalStateException("Deployment evidence unavailable"); }
            finally { if (signed != null) Arrays.fill(signed, (byte) 0); }
        }
        return settings(domain, challenge, fields);
    }

    /** Only explicitly reviewed non-secret semantic fields; never Environment, credentials, paths or record.toString(). */
    public static Proof settings(String domain, Challenge challenge, Map<String, String> fields) {
        requireHost(); requireDomain(domain);
        try {
            return new Proof(domain, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(domain, challenge, fields))));
        } catch (GeneralSecurityException failure) { throw new IllegalStateException("Deployment evidence unavailable"); }
    }

    public static Proof combine(String domain, Challenge challenge, List<Proof> proofs) {
        requireHost(); requireDomain(domain);
        if (proofs == null || proofs.isEmpty() || proofs.size() > 64) throw invalid();
        var fields = new TreeMap<String, String>();
        for (var proof : proofs) {
            if (proof == null || !proof.available()) return unavailable(domain);
            if (fields.putIfAbsent(proof.domain(), proof.digest()) != null) throw invalid();
        }
        return settings(domain, challenge, fields);
    }

    public static void requireHost() {
        if (SecurityContextHolder.getContext().getAuthentication() != null || TenantEntryScope.isHttpRequest()
                || RequestContextHolder.getRequestAttributes() != null)
            throw new AccessDeniedException("Deployment evidence requires trusted host execution");
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Deployment evidence requires no database transaction");
    }

    private static byte[] encode(String domain, Challenge challenge, Map<String, String> fields) {
        if (challenge == null || fields == null || fields.size() > 512) throw invalid();
        try {
            var bytes = new ByteArrayOutputStream();
            var output = new DataOutputStream(bytes);
            for (String value : List.of("sma:deployment:evidence:v1", domain, challenge.clusterHash(), challenge.manifestHash(), challenge.nonce()))
                write(output, value);
            output.writeInt(fields.size());
            for (var entry : new TreeMap<>(fields).entrySet()) {
                if (entry.getKey() == null || !entry.getKey().matches("[A-Za-z0-9_.-]{1,100}")
                        || entry.getValue() == null || entry.getValue().length() > 8192) throw invalid();
                write(output, entry.getKey()); write(output, entry.getValue());
                if (bytes.size() > 65536) throw invalid();
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException("Deployment evidence encoding unavailable"); }
    }

    private static void write(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length); output.write(encoded);
    }
    private static boolean identifier(String value) { return value != null && value.matches("[A-Za-z0-9_-]{1,64}"); }
    private static boolean hash(String value) { return value != null && value.matches("[a-f0-9]{64}"); }
    private static void requireDomain(String value) {
        if (value == null || !value.matches("[a-z]+\\.[a-z][a-z0-9.-]{0,63}")) throw invalid();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid deployment evidence input"); }
}
