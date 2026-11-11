ALTER TABLE tenant_brand_revision
    ADD COLUMN operation_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN operation_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN publisher_id BIGINT NULL,
    ADD COLUMN defaults_revision VARCHAR(64) NULL,
    ADD UNIQUE KEY uk_tenant_brand_operation (tenant_id,portal,operation_id);

CREATE TABLE tenant_brand_reference (
    id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
    portal VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reference_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    slot VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, file_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tenant_brand_ref (tenant_id,portal,reference_key,slot),
    KEY ix_tenant_brand_file (tenant_id,portal,file_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tenant_brand_asset (
    id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
    portal VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    revision_id BIGINT NOT NULL, slot VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_file_id BIGINT NOT NULL, image_bytes MEDIUMBLOB NOT NULL,
    checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, width INT NOT NULL, height INT NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tenant_brand_asset (tenant_id,portal,revision_id,slot),
    CONSTRAINT ck_tenant_brand_image_size CHECK (OCTET_LENGTH(image_bytes)<=2097152 AND width>0 AND height>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
