CREATE TABLE identity_realm (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
 code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL,
 enabled TINYINT(1) NOT NULL, registration_enabled TINYINT(1) NOT NULL, password_enabled TINYINT(1) NOT NULL,
 version BIGINT NOT NULL, security_version BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_identity_realm_code (tenant_id,code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE identity_client (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, realm_id BIGINT NOT NULL,
 code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL, application VARCHAR(64) NOT NULL,
 platform VARCHAR(16) NOT NULL, login_handle VARCHAR(43) COLLATE utf8mb4_bin NOT NULL,
 enabled TINYINT(1) NOT NULL, version BIGINT NOT NULL, security_version BIGINT NOT NULL,
 configuration_version BIGINT NOT NULL, credential_version BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_identity_client_code (tenant_id,code),
 UNIQUE KEY uk_identity_client_handle (login_handle),
 KEY ix_identity_client_realm (tenant_id,realm_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE identity_client_revision (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, client_id BIGINT NOT NULL,
 configuration_version BIGINT NOT NULL, credential_version BIGINT NOT NULL,
 google_enabled TINYINT(1) NOT NULL, google_client_id VARCHAR(255) COLLATE utf8mb4_bin NULL,
 secret_envelope TEXT COLLATE utf8mb4_bin NULL,
 callbacks_json JSON NOT NULL, origins_json JSON NOT NULL,
 access_token_seconds INT NOT NULL, refresh_token_seconds INT NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_identity_client_revision (tenant_id,client_id,configuration_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
