package com.sma.extension.protocol;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TrustProtocolTests {
    @Test void signedDocumentsCannotChangePurposeEnvironmentBytesOrLifetime() throws Exception {
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var now = Instant.parse("2026-11-10T00:00:00Z");
        var claims = new SignedClaims(1, SignedClaims.Purpose.LEASE, "execution", "security", "host-a", "production", "lease-id", now, now.plusSeconds(60), "{}".getBytes(StandardCharsets.UTF_8));
        var envelope = DocumentSignatures.sign(claims, pair.getPrivate());
        var trust = Map.of("execution", new DocumentSignatures.TrustedKey(pair.getPublic(), Set.of(SignedClaims.Purpose.LEASE), now.minusSeconds(10), now.plusSeconds(3600)));
        assertThat(DocumentSignatures.verify(envelope, trust, SignedClaims.Purpose.LEASE, "security", "host-a", "production", now, Duration.ofMinutes(15)).documentId()).isEqualTo("lease-id");
        assertThatThrownBy(() -> DocumentSignatures.verify(envelope, trust, SignedClaims.Purpose.LEASE, "security", "host-a", "test", now, Duration.ofMinutes(15))).hasMessage("signature-context-mismatch");
        assertThatThrownBy(() -> DocumentSignatures.verify(envelope, trust, SignedClaims.Purpose.RELEASE, "security", "host-a", "production", now, Duration.ofMinutes(15))).hasMessage("untrusted-key");
        assertThatThrownBy(() -> DocumentSignatures.verify(envelope, trust, SignedClaims.Purpose.LEASE, "security", "host-a", "production", now.plusSeconds(60), Duration.ofMinutes(15))).hasMessage("expired-or-future-document");
        var modified = new SignedEnvelope(envelope.keyId(), DocumentSignatures.encode("{}".getBytes(StandardCharsets.UTF_8)), envelope.signature());
        assertThatThrownBy(() -> DocumentSignatures.verify(modified, trust, SignedClaims.Purpose.LEASE, "security", "host-a", "production", now, Duration.ofMinutes(15))).hasMessage("invalid-signature");
    }
    @Test void duplicateFieldsUnknownFieldsAndTrailingDocumentsAreRejected() {
        for (String json : List.of("{\"sha256\":\"a\",\"sha256\":\"b\",\"bytes\":1}", "{\"sha256\":\"a\",\"bytes\":1,\"extra\":true}", "{\"sha256\":\"a\",\"bytes\":1} {}")) {
            assertThatThrownBy(() -> ProtocolJson.read(json.getBytes(StandardCharsets.UTF_8), Hashes.DigestResult.class)).isInstanceOf(ProtocolException.class);
        }
    }
    @Test void scanMustBeCompleteFreshAndBoundToExactInventory() {
        var now = Instant.parse("2026-11-10T00:00:00Z"); var hash = "a".repeat(64);
        var fresh = new ScanReport(hash, "test-scanner", "1", now, now, true, List.of(), List.of(hash));
        assertThat(VulnerabilityGate.evaluate(fresh, "demo", hash, "production", 1, Set.of(hash), List.of(), now).allowed()).isTrue();
        assertThat(VulnerabilityGate.evaluate(fresh, "demo", hash, "production", 1, Set.of(hash), List.of(), now.plus(Duration.ofDays(1))).allowed()).isFalse();
        assertThat(VulnerabilityGate.evaluate(fresh, "demo", hash, "production", 1, Set.of(), List.of(), now).allowed()).isFalse();
        var finding = new ScanReport.Finding("CVE-example", hash, 9.8, ScanReport.Finding.Severity.CRITICAL, true, true, false, "known exploited");
        var bad = new ScanReport(hash, "test-scanner", "1", now, now, true, List.of(finding), List.of(hash));
        var permit = new VulnerabilityGate.ExceptionPermit("exception", "demo", hash, hash, "CVE-example", "production", 1, now, now.plusSeconds(10), "test evidence");
        assertThat(VulnerabilityGate.evaluate(bad, "demo", hash, "production", 1, Set.of(hash), List.of(permit), now).allowed()).isFalse();
    }
    @Test void streamQuotasAndWindowsTraversalAreRejected() throws Exception {
        assertThat(Hashes.sha256(new java.io.ByteArrayInputStream(new byte[3]), 3).bytes()).isEqualTo(3);
        assertThatThrownBy(() -> Hashes.sha256(new java.io.ByteArrayInputStream(new byte[4]), 3)).hasMessage("size-limit-exceeded");
        for (String name : List.of("../evil.class", "lib/../evil.jar", "C:/evil.jar", "a\\b", "lib/CON.jar", "lib/a.jar:stream", "lib/a. /b", "a//b"))
            assertThatThrownBy(() -> ArchiveInspector.validatePath(name)).hasMessage("unsafe-archive-path");
    }
}
