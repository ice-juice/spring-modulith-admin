CREATE TABLE file_upload_request (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, portal VARCHAR(32) NOT NULL,
 public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 key_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 file_id BIGINT NOT NULL, security_generation BIGINT NOT NULL, image_only TINYINT(1) NOT NULL,
 expected_size BIGINT NOT NULL, expected_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 state VARCHAR(24) NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_upload_public (tenant_id, public_id),
 UNIQUE KEY uk_upload_intent (tenant_id, portal, created_by, key_hash),
 UNIQUE KEY uk_upload_file (tenant_id, file_id)
);
