CREATE TABLE identity_subject (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, realm_id BIGINT NOT NULL,
 enabled TINYINT(1) NOT NULL, security_version BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 KEY ix_identity_subject_realm (tenant_id,realm_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE identity_external (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, realm_id BIGINT NOT NULL, subject_id BIGINT NOT NULL,
 issuer VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, external_subject VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_identity_external_provider (tenant_id,realm_id,issuer,external_subject),
 KEY ix_identity_external_subject (tenant_id,subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE identity_session (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, subject_id BIGINT NOT NULL, realm_id BIGINT NOT NULL, client_id BIGINT NOT NULL,
 application VARCHAR(64) NOT NULL, mode VARCHAR(16) NOT NULL, status VARCHAR(16) NOT NULL,
 access_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL, csrf_hash CHAR(64) COLLATE utf8mb4_bin NULL,
 access_expires_at DATETIME(3) NOT NULL, refresh_expires_at DATETIME(3) NOT NULL,
 tenant_generation BIGINT NOT NULL, realm_generation BIGINT NOT NULL, client_generation BIGINT NOT NULL, subject_generation BIGINT NOT NULL,
 revoked_at DATETIME(3) NULL, revocation_reason VARCHAR(32) NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_identity_session_access (tenant_id,access_hash),
 KEY ix_identity_session_subject (tenant_id,subject_id,status,refresh_expires_at),
 KEY ix_identity_session_expiry (tenant_id,refresh_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE identity_refresh (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, session_id BIGINT NOT NULL,
 token_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL, csrf_hash CHAR(64) COLLATE utf8mb4_bin NULL,
 consumed_at DATETIME(3) NULL, expires_at DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_identity_refresh_hash (tenant_id,token_hash),
 KEY ix_identity_refresh_session (tenant_id,session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
