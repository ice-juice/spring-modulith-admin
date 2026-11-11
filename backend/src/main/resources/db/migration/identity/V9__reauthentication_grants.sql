CREATE TABLE identity_reauthentication (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, realm_id BIGINT NOT NULL, subject_id BIGINT NOT NULL, session_id BIGINT NOT NULL,
 stamp_json JSON NOT NULL, action VARCHAR(32) NOT NULL, target_ref VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
 method VARCHAR(16) NOT NULL, grant_hash CHAR(64) COLLATE utf8mb4_bin NULL, status VARCHAR(16) NOT NULL,
 expires_at DATETIME(3) NOT NULL, retain_until DATETIME(3) NOT NULL, consumed_at DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_identity_reauthentication_grant (tenant_id,grant_hash),
 KEY ix_identity_reauthentication_realm (tenant_id,realm_id,id), KEY ix_identity_reauthentication_subject (tenant_id,realm_id,subject_id,id),
 KEY ix_identity_reauthentication_expiry (tenant_id,status,expires_at), KEY ix_identity_reauthentication_retention (tenant_id,retain_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
