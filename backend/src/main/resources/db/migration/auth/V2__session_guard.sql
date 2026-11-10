CREATE TABLE auth_session_guard (
    id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
    KEY ix_session_guard_tenant (tenant_id)
) COMMENT='Per-account row lock makes single-terminal session replacement atomic';
