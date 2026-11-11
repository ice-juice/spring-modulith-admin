ALTER TABLE tenant_brand_head ADD COLUMN legacy_background_url VARCHAR(2048) NULL;
ALTER TABLE tenant_brand_revision ADD COLUMN legacy_background_url VARCHAR(2048) NULL;
CREATE TABLE tenant_brand_import (
    id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
    source_revision CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    portals_json JSON NOT NULL, excluded_keys_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tenant_brand_import (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
