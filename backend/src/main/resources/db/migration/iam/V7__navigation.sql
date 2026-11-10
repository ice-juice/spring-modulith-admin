CREATE TABLE iam_navigation (
    id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, portal VARCHAR(32) NOT NULL DEFAULT 'admin',
    name VARCHAR(128) NOT NULL, icon VARCHAR(64), sort INT NOT NULL DEFAULT 0, enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
    KEY ix_navigation_sort (tenant_id,portal,deleted,sort)
);
CREATE TABLE iam_navigation_menu (
    id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, navigation_id BIGINT NOT NULL, permission_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
    KEY ix_navigation_menu (tenant_id,navigation_id,deleted), KEY ix_menu_navigation (tenant_id,permission_id,deleted)
);
