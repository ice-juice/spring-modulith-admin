CREATE TABLE identity_email_credential (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, realm_id BIGINT NOT NULL, subject_id BIGINT NOT NULL,
 email_index CHAR(64) COLLATE utf8mb4_bin NOT NULL, email_envelope TEXT COLLATE utf8mb4_bin NOT NULL,
 email_version BIGINT NOT NULL, email_verified_at DATETIME(3) NOT NULL,
 password_hash VARCHAR(255) COLLATE utf8mb4_bin NULL, password_version BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_identity_email_credential_address (tenant_id,realm_id,email_index),
 UNIQUE KEY uk_identity_email_credential_subject (tenant_id,subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE identity_session ADD authentication_method VARCHAR(24) NOT NULL DEFAULT 'UNKNOWN',
 ADD authenticated_at DATETIME(3) NULL;
