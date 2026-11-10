CREATE TABLE iam_permission (
    id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, parent_id BIGINT NOT NULL DEFAULT 0,
    portal VARCHAR(32) NOT NULL DEFAULT 'admin', permission_key VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL, name_key VARCHAR(256), type VARCHAR(16) NOT NULL,
    path VARCHAR(1024), icon VARCHAR(64), open_mode VARCHAR(16) NOT NULL DEFAULT 'ROUTE',
    sort INT NOT NULL DEFAULT 0, enabled TINYINT NOT NULL DEFAULT 1, hidden TINYINT NOT NULL DEFAULT 0,
    builtin TINYINT NOT NULL DEFAULT 0, super_admin_default TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
    active_key VARCHAR(128) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN permission_key ELSE NULL END) STORED,
    UNIQUE KEY uk_permission_key (tenant_id,portal,active_key),
    KEY ix_permission_tree (tenant_id,portal,parent_id,deleted)
);
CREATE TABLE iam_i18n_text (
    id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
    entity_type VARCHAR(64) NOT NULL, entity_id BIGINT NOT NULL, field VARCHAR(64) NOT NULL,
    locale VARCHAR(16) NOT NULL, text VARCHAR(512) NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
    KEY ix_i18n_entity (tenant_id,entity_type,entity_id,field,locale,deleted)
) COMMENT='Translations for menu names; source business text is preserved';
