CREATE TABLE file_asset(
 id BIGINT NOT NULL PRIMARY KEY,tenant_id BIGINT NOT NULL,portal VARCHAR(32) NOT NULL,dept_id BIGINT,original_name VARCHAR(255) NOT NULL,storage_key VARCHAR(64) NOT NULL,
 content_type VARCHAR(128) NOT NULL,kind VARCHAR(16) NOT NULL,size_bytes BIGINT NOT NULL,checksum CHAR(64) NOT NULL,uploader VARCHAR(64) NOT NULL,
 created_at DATETIME(3) NOT NULL,created_by BIGINT NOT NULL,updated_at DATETIME(3) NOT NULL,updated_by BIGINT NOT NULL,deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_asset_storage(storage_key),KEY ix_asset_owner(tenant_id,portal,created_by,deleted),KEY ix_asset_department(tenant_id,dept_id,deleted)
);
