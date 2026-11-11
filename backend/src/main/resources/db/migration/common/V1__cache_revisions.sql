CREATE TABLE common_cache_revision (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    region VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    revision BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_common_cache_revision_scope (tenant_id, region),
    CONSTRAINT ck_common_cache_revision_positive CHECK (revision > 0 AND deleted = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
