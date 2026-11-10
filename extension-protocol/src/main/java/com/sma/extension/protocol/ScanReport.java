package com.sma.extension.protocol;

import java.time.Instant;
import java.util.List;

/** Accepted only from the authenticated scanner worker; uploaded self-reports are not evidence. */
public record ScanReport(String artifactSha256, String scanner, String scannerVersion, Instant scannedAt,
        Instant databaseUpdatedAt, boolean complete, List<Finding> findings, List<String> inventorySha256) {
    public ScanReport {
        Checks.hash(artifactSha256); Checks.text(scanner, 100); Checks.text(scannerVersion, 100);
        Checks.require(scannedAt != null && databaseUpdatedAt != null, "scan-time-required");
        findings = List.copyOf(findings); inventorySha256 = List.copyOf(inventorySha256);
        Checks.unique(inventorySha256, 10000); inventorySha256.forEach(Checks::hash);
        Checks.require(findings.size() <= 10000, "too-many-findings");
    }
    public record Finding(String id, String componentSha256, Double cvss, Severity severity,
            boolean knownExploited, boolean affected, boolean malware, String evidence) {
        public enum Severity { LOW, MEDIUM, HIGH, CRITICAL, UNKNOWN }
        public Finding {
            Checks.text(id, 200); Checks.hash(componentSha256); Checks.text(evidence, 2000);
            Checks.require(severity != null && (cvss == null || Double.isFinite(cvss) && cvss >= 0 && cvss <= 10), "invalid-severity");
        }
    }
}
