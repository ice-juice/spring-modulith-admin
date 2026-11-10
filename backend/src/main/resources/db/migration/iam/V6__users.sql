CREATE TABLE iam_user (
    id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, username VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL, password_hash VARCHAR(100) NOT NULL, portal VARCHAR(32) NOT NULL DEFAULT 'admin',
    dept_id BIGINT, email VARCHAR(254), mobile VARCHAR(32), gender VARCHAR(16),
    enabled TINYINT NOT NULL DEFAULT 1, super_admin TINYINT NOT NULL DEFAULT 0,
    locale VARCHAR(16), time_zone VARCHAR(64), password_changed_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
    active_username VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN username ELSE NULL END) STORED,
    UNIQUE KEY uk_username (tenant_id,portal,active_username),
    KEY ix_user_department (tenant_id,dept_id,deleted)
);
CREATE TABLE iam_user_role (
    id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, user_id BIGINT NOT NULL, role_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
    KEY ix_user_role (tenant_id,user_id,deleted), KEY ix_role_user (tenant_id,role_id,deleted)
);
CREATE TABLE iam_user_post (
    id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, user_id BIGINT NOT NULL, post_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
    KEY ix_user_post (tenant_id,user_id,deleted), KEY ix_post_user (tenant_id,post_id,deleted)
);
