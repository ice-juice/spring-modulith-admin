CREATE TABLE file_storage_profile (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL,
 provider VARCHAR(16) NOT NULL, uploads_enabled TINYINT(1) NOT NULL, reads_enabled TINYINT(1) NOT NULL,
 version BIGINT NOT NULL, configuration_version BIGINT NOT NULL, credential_version BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 active_code VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN code ELSE NULL END) STORED,
 UNIQUE KEY uk_file_storage_profile_code (tenant_id,active_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE file_storage_revision (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, profile_id BIGINT NOT NULL,
 configuration_version BIGINT NOT NULL, credential_version BIGINT NOT NULL,
 local_root VARCHAR(64) NULL, region VARCHAR(64) COLLATE utf8mb4_bin NULL, endpoint VARCHAR(512) COLLATE utf8mb4_bin NULL,
 bucket VARCHAR(63) COLLATE utf8mb4_bin NULL, object_prefix VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
 applications_json JSON NOT NULL, secret_envelope MEDIUMTEXT COLLATE utf8mb4_bin NULL,
 credential_hint VARCHAR(32) NULL, credential_expires_at DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_file_storage_revision (tenant_id,profile_id,configuration_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
