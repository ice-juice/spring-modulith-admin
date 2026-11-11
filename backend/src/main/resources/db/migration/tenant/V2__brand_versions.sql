CREATE TABLE tenant_brand_head (
    id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
    portal VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    draft_json JSON NOT NULL, draft_version BIGINT NOT NULL DEFAULT 0,
    published_revision_id BIGINT NULL, next_revision BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tenant_brand_scope (tenant_id,portal),
    CONSTRAINT ck_tenant_brand_versions CHECK (draft_version>=0 AND next_revision>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tenant_brand_revision (
    id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
    portal VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    revision BIGINT NOT NULL, source_revision_id BIGINT NULL,
    content_json JSON NOT NULL, content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tenant_brand_revision (tenant_id,portal,revision),
    KEY ix_tenant_brand_recent (tenant_id,portal,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
