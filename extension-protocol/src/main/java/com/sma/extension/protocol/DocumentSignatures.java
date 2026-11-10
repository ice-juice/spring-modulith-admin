package com.sma.extension.protocol;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/** Ed25519 from the JDK provider. No key or algorithm is accepted from the artifact itself. */
public final class DocumentSignatures {
    private DocumentSignatures() {}
    public record TrustedKey(PublicKey publicKey, Set<SignedClaims.Purpose> purposes, Instant notBefore, Instant notAfter) {
        public TrustedKey {
            purposes = Set.copyOf(purposes);
            Checks.require(publicKey != null && notBefore != null && notAfter != null && notAfter.isAfter(notBefore), "invalid-trust-key");
        }
    }
    public static SignedEnvelope sign(SignedClaims claims, PrivateKey key) {
        byte[] bytes = ProtocolJson.write(claims);
        try {
            var signer = Signature.getInstance("Ed25519"); signer.initSign(key); signer.update(bytes);
            return new SignedEnvelope(claims.keyId(), encode(bytes), encode(signer.sign()));
        } catch (GeneralSecurityException failure) { throw new ProtocolException("signing-failed", failure); }
    }
    public static SignedClaims verify(SignedEnvelope envelope, Map<String, TrustedKey> trust,
            SignedClaims.Purpose purpose, String issuer, String audience, String environment,
            Instant now, Duration maximumLifetime) {
        var key = trust.get(envelope.keyId());
        Checks.require(key != null && key.purposes().contains(purpose), "untrusted-key");
        Checks.require(!now.isBefore(key.notBefore()) && now.isBefore(key.notAfter()), "expired-trust-key");
        byte[] bytes = decode(envelope.payload()), signed = decode(envelope.signature());
        Checks.require(bytes.length <= ProtocolJson.MAX_DOCUMENT_BYTES && signed.length == 64, "invalid-signature-size");
        try {
            var verifier = Signature.getInstance("Ed25519"); verifier.initVerify(key.publicKey()); verifier.update(bytes);
            Checks.require(verifier.verify(signed), "invalid-signature");
        } catch (GeneralSecurityException failure) { throw new ProtocolException("invalid-signature", failure); }
        var claims = ProtocolJson.read(bytes, SignedClaims.class);
        Checks.require(claims.keyId().equals(envelope.keyId()) && claims.purpose() == purpose && claims.issuer().equals(issuer)
                && claims.audience().equals(audience) && claims.environment().equals(environment), "signature-context-mismatch");
        Checks.require(!claims.issuedAt().isBefore(key.notBefore()) && !claims.expiresAt().isAfter(key.notAfter()), "signature-outside-key-lifetime");
        Checks.require(!maximumLifetime.isNegative() && !maximumLifetime.isZero()
                && Duration.between(claims.issuedAt(), claims.expiresAt()).compareTo(maximumLifetime) <= 0, "excessive-document-lifetime");
        // No future clock grace: a bad node clock must not extend execution permission.
        Checks.require(!claims.issuedAt().isAfter(now) && now.isBefore(claims.expiresAt()), "expired-or-future-document");
        return claims;
    }
    public static String encode(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public static byte[] decode(String value) {
        try {
            Checks.require(value != null && value.matches("[A-Za-z0-9_-]+"), "invalid-base64url");
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            Checks.require(encode(decoded).equals(value), "noncanonical-base64url");
            return decoded;
        } catch (IllegalArgumentException failure) { throw new ProtocolException("invalid-base64url", failure); }
    }
}
