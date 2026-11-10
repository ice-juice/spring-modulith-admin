CREATE TABLE iam_role (
    id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0, portal VARCHAR(32) NOT NULL DEFAULT 'admin',
    name VARCHAR(128) NOT NULL, code VARCHAR(64) NOT NULL,
    sort INT NOT NULL DEFAULT 0, enabled TINYINT NOT NULL DEFAULT 1,
    data_scope VARCHAR(32) NOT NULL DEFAULT 'SELF', description VARCHAR(512),
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
    active_code VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN code ELSE NULL END) STORED,
    UNIQUE KEY uk_role_code (tenant_id,portal,active_code), KEY ix_role_parent (tenant_id,portal,parent_id,deleted)
) COMMENT='Roles are organized as a tree without permission inheritance';
CREATE TABLE iam_role_department (
    id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, role_id BIGINT NOT NULL, department_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
    KEY ix_role_department (tenant_id,role_id,deleted), KEY ix_department_role (tenant_id,department_id,deleted)
);
