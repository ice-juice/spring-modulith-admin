CREATE TABLE identity_email_budget (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
 minute_slot BIGINT NULL, day_slot BIGINT NULL, minute_starts INT NOT NULL DEFAULT 0, day_starts INT NOT NULL DEFAULT 0,
 guess_minute BIGINT NULL, guess_day BIGINT NULL, minute_guesses INT NOT NULL DEFAULT 0, day_guesses INT NOT NULL DEFAULT 0,
 outstanding INT NOT NULL DEFAULT 0, retained INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0, UNIQUE KEY uk_identity_email_budget_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE identity_email_rate (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, scope_key VARCHAR(256) COLLATE utf8mb4_bin NOT NULL,
 window_slot BIGINT NOT NULL, attempts INT NOT NULL, expires_at DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_identity_email_rate_scope (tenant_id,scope_key), KEY ix_identity_email_rate_expiry (tenant_id,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE identity_email_challenge (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
 handle_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL, proof_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL,
 snapshot_json JSON NOT NULL, email_configuration_id BIGINT NOT NULL, email_configuration_version BIGINT NOT NULL,
 language VARCHAR(16) NOT NULL, purpose VARCHAR(24) NOT NULL, origin VARCHAR(255) COLLATE utf8mb4_bin NULL,
 target_index CHAR(64) COLLATE utf8mb4_bin NOT NULL, target_envelope TEXT COLLATE utf8mb4_bin NULL,
 subject_id BIGINT NULL, subject_generation BIGINT NULL,
 code_fingerprint VARCHAR(256) COLLATE utf8mb4_bin NULL, status VARCHAR(16) NOT NULL, failures INT NOT NULL DEFAULT 0,
 delivery_id BIGINT NULL, grant_hash CHAR(64) COLLATE utf8mb4_bin NULL,
 expires_at DATETIME(3) NOT NULL, grant_expires_at DATETIME(3) NULL, retain_until DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_identity_email_challenge_handle (tenant_id,handle_hash), UNIQUE KEY uk_identity_email_challenge_grant (tenant_id,grant_hash),
 KEY ix_identity_email_challenge_expiry (tenant_id,status,expires_at), KEY ix_identity_email_grant_expiry (tenant_id,status,grant_expires_at),
 KEY ix_identity_email_challenge_retention (tenant_id,retain_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
