CREATE TABLE openapi_token(
 id BIGINT NOT NULL PRIMARY KEY,tenant_id BIGINT NOT NULL,application_id BIGINT NOT NULL,portal VARCHAR(32) NOT NULL,token_hash CHAR(64) NOT NULL,credential_version BIGINT NOT NULL,expires_at DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL,created_by BIGINT NOT NULL,updated_at DATETIME(3) NOT NULL,updated_by BIGINT NOT NULL,deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_open_token_hash(token_hash),KEY ix_open_token_app(tenant_id,application_id,deleted,expires_at)
);
CREATE TABLE openapi_nonce(
 id BIGINT NOT NULL PRIMARY KEY,tenant_id BIGINT NOT NULL,application_id BIGINT NOT NULL,nonce VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,expires_at DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL,created_by BIGINT NOT NULL,updated_at DATETIME(3) NOT NULL,updated_by BIGINT NOT NULL,deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_open_nonce(tenant_id,application_id,nonce),KEY ix_open_nonce_expiry(tenant_id,expires_at)
);
