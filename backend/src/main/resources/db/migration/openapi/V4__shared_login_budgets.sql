-- Platform-only credential bootstrap metadata: no application, account or tenant business data.
CREATE TABLE openapi_login_budget_guard (
 id BIGINT NOT NULL PRIMARY KEY, key_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 key_cipher VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_open_login_guard CHECK(id=1)
);
CREATE TABLE openapi_login_budget (
 id BIGINT NOT NULL PRIMARY KEY, scope VARCHAR(12) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 subject_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 attempt_count INT NOT NULL, expires_at DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_open_login_budget(scope,subject_hash), KEY ix_open_login_budget_expiry(expires_at),
 CONSTRAINT ck_open_login_budget_scope CHECK(scope IN ('SOURCE','PAIR')),
 CONSTRAINT ck_open_login_budget_count CHECK(attempt_count>=0 AND attempt_count<=1000)
);
