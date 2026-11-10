CREATE TABLE auth_session (
    id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, user_id BIGINT NOT NULL, portal VARCHAR(32) NOT NULL,
    token_hash CHAR(64) NOT NULL, expires_at DATETIME(3) NOT NULL, last_seen_at DATETIME(3) NOT NULL,
    ip VARCHAR(64), user_agent VARCHAR(512), locked TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_session_token (token_hash), KEY ix_session_user (tenant_id,user_id,deleted),
    KEY ix_session_expiration (tenant_id,expires_at,deleted)
);
CREATE TABLE auth_captcha (
    id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, challenge_id VARCHAR(64) NOT NULL,
    answer_hash CHAR(64) NOT NULL, expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_captcha_challenge (tenant_id,challenge_id), KEY ix_captcha_expiration (tenant_id,expires_at)
);
