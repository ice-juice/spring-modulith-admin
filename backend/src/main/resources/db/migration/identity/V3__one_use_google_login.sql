CREATE TABLE identity_login_budget (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
 minute_slot BIGINT NULL, minute_starts INT NOT NULL DEFAULT 0, day_slot DATE NULL, day_starts INT NOT NULL DEFAULT 0,
 outstanding INT NOT NULL DEFAULT 0, retained INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0, UNIQUE KEY uk_identity_login_budget_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE identity_login_rate (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
 scope_key VARCHAR(160) COLLATE utf8mb4_bin NOT NULL, minute_slot BIGINT NOT NULL, starts INT NOT NULL,
 expires_at DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_identity_login_rate_scope (tenant_id,scope_key), KEY ix_identity_login_rate_expiry (tenant_id,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE identity_login_transaction (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
 state_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL, proof_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL,
 snapshot_json JSON NOT NULL, callback_uri VARCHAR(2048) COLLATE utf8mb4_bin NOT NULL,
 origin VARCHAR(255) COLLATE utf8mb4_bin NULL, flow VARCHAR(16) NOT NULL, pkce_challenge CHAR(43) COLLATE utf8mb4_bin NOT NULL,
 nonce_envelope TEXT COLLATE utf8mb4_bin NULL, status VARCHAR(16) NOT NULL,
 claim_id VARCHAR(36) COLLATE utf8mb4_bin NULL, result_code VARCHAR(64) NULL, session_id BIGINT NULL,
 expires_at DATETIME(3) NOT NULL, retain_until DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_identity_login_transaction_state (tenant_id,state_hash),
 KEY ix_identity_login_transaction_expiry (tenant_id,status,expires_at), KEY ix_identity_login_transaction_retention (tenant_id,retain_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
