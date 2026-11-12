package com.sma.admin.tenant.storage;

import java.time.Instant;

/** Deployment-controlled physical copy verifier. Implementations must inspect backup and both databases. */
public interface StorageMigrationEvidence {
    record Target(long tenantId, String storageGroup, String sourceResourceId, long sourceRevision,
                  String targetResourceId, long targetRevision) {}
    record Snapshot(String backupRef, String sourceDigest, String targetDigest,
                    long sourceRows, long targetRows, long sourceOutbox,
                    long targetOutbox, Instant verifiedAt) {}
    Snapshot initial(Target target);
    Snapshot finalSync(Target target);
}
