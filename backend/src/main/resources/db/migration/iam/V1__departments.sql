CREATE TABLE iam_department (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(128) NOT NULL,
    code VARCHAR(64) NOT NULL,
    path VARCHAR(2048) NOT NULL,
    sort INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    active_code VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN code ELSE NULL END) STORED,
    UNIQUE KEY uk_department_code (tenant_id, active_code),
    KEY ix_department_parent (tenant_id, parent_id, deleted),
    KEY ix_department_path (tenant_id, path(191))
) COMMENT='Organization departments; timestamps UTC';

CREATE TABLE iam_structure_lock (
    id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0, UNIQUE KEY uk_structure_tenant (tenant_id)
) COMMENT='Serializes organization mutations per tenant';
