CREATE TABLE auth_user_security (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, user_id BIGINT NOT NULL, portal VARCHAR(32) NOT NULL,
 security_version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT(1) NOT NULL DEFAULT 0,
 active_user BIGINT GENERATED ALWAYS AS (IF(deleted=0,user_id,NULL)) STORED,
 UNIQUE KEY uk_auth_security_user (tenant_id,portal,active_user)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_totp_credential (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, user_id BIGINT NOT NULL, portal VARCHAR(32) NOT NULL,
 enabled TINYINT(1) NOT NULL DEFAULT 0, secret_ciphertext TEXT, last_accepted_step BIGINT, enabled_at DATETIME(3),
 pending_secret_ciphertext TEXT, pending_challenge_id BIGINT, pending_expires_at DATETIME(3),
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT(1) NOT NULL DEFAULT 0,
 active_user BIGINT GENERATED ALWAYS AS (IF(deleted=0,user_id,NULL)) STORED,
 UNIQUE KEY uk_auth_totp_user (tenant_id,portal,active_user), KEY ix_auth_totp_pending (tenant_id,pending_expires_at),
 CONSTRAINT ck_auth_totp_enabled CHECK (enabled=0 OR (secret_ciphertext IS NOT NULL AND enabled_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_security_password (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, user_id BIGINT NOT NULL, portal VARCHAR(32) NOT NULL,
 password_hash VARCHAR(255) NOT NULL, pepper_key_id VARCHAR(64) NOT NULL, credential_version BIGINT NOT NULL, changed_at DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT(1) NOT NULL DEFAULT 0,
 active_user BIGINT GENERATED ALWAYS AS (IF(deleted=0,user_id,NULL)) STORED,
 UNIQUE KEY uk_auth_password_user (tenant_id,portal,active_user)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_challenge (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, user_id BIGINT NOT NULL, portal VARCHAR(32) NOT NULL,
 token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, purpose VARCHAR(32) NOT NULL, source_session_id BIGINT,
 account_credential_version BIGINT NOT NULL, security_version BIGINT NOT NULL,
 expires_at DATETIME(3) NOT NULL, consumed_at DATETIME(3), attempt_count INT NOT NULL DEFAULT 0,
 single_terminal TINYINT(1) NOT NULL DEFAULT 0, session_ttl_seconds BIGINT NOT NULL DEFAULT 28800,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_auth_challenge_token (token_hash), KEY ix_auth_challenge_expiry (tenant_id,expires_at),
 KEY ix_auth_challenge_user (tenant_id,user_id,purpose), CONSTRAINT ck_auth_challenge_attempts CHECK (attempt_count>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_throttle (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, scope VARCHAR(32) NOT NULL,
 subject_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 attempt_count INT NOT NULL DEFAULT 0, expires_at DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_auth_throttle_subject (tenant_id,scope,subject_hash), KEY ix_auth_throttle_expiry (tenant_id,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE auth_session
 ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN authentication_method VARCHAR(32) NOT NULL DEFAULT 'PASSWORD',
 ADD COLUMN password_verified_at DATETIME(3) NULL,
 ADD COLUMN mfa_verified_at DATETIME(3) NULL;
