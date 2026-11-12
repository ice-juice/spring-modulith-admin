-- Host-only deployment evidence on the existing, explicitly reviewed membership metadata.
-- Legacy rows have no evidence and never inherit qualification merely from a live lease.
ALTER TABLE common_cache_node_group
    ADD COLUMN evidence_manifest_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN evidence_nonce CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN evidence_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL;

ALTER TABLE common_cache_node_lease
    ADD COLUMN evidence_nonce CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN evidence_process_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN evidence_epoch BIGINT NULL,
    ADD COLUMN evidence_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN evidence_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN evidence_until DATETIME(3) NULL,
    ADD CONSTRAINT ck_common_node_evidence_state CHECK (
        evidence_state IS NULL OR evidence_state IN ('MATCH','MISMATCH','UNAVAILABLE','WAITING_BASELINE'));
