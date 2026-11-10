CREATE TABLE iam_role_permission (
    id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, role_id BIGINT NOT NULL, permission_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
    KEY ix_role_permission (tenant_id,role_id,deleted), KEY ix_permission_role (tenant_id,permission_id,deleted)
);
