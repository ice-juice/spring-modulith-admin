CREATE TABLE identity_email_realm_key (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, realm_id BIGINT NOT NULL,
 key_envelope TEXT COLLATE utf8mb4_bin NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_identity_email_key_realm (tenant_id,realm_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE identity_email_configuration (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, client_id BIGINT NOT NULL,
 language VARCHAR(16) NOT NULL, enabled TINYINT(1) NOT NULL, version BIGINT NOT NULL,
 registration_template VARCHAR(64) NOT NULL, reset_template VARCHAR(64) NOT NULL, binding_template VARCHAR(64) NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_identity_email_config_language (tenant_id,client_id,language)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
