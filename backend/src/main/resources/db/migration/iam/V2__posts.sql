CREATE TABLE iam_post (
    id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL, code VARCHAR(64) NOT NULL,
    sort INT NOT NULL DEFAULT 0, enabled TINYINT NOT NULL DEFAULT 1,
    description VARCHAR(512), created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
    active_code VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN code ELSE NULL END) STORED,
    UNIQUE KEY uk_post_code (tenant_id, active_code), KEY ix_post_sort (tenant_id,deleted,sort)
) COMMENT='Organization posts; timestamps UTC';
