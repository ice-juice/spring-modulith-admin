-- Global host framework metadata, explicitly not tenant business entities.
CREATE TABLE common_cache_node_group (
    id BIGINT NOT NULL,
    cluster_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    manifest_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    manifest_version BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id), UNIQUE KEY uk_common_cache_node_group_cluster (cluster_hash),
    CONSTRAINT ck_common_cache_node_group_active CHECK (deleted=0 AND manifest_version>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE common_cache_node_lease (
    id BIGINT NOT NULL,
    cluster_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    node_id VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    process_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    epoch BIGINT NOT NULL,
    manifest_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    lease_until DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id), UNIQUE KEY uk_common_cache_node_lease_slot (cluster_hash,node_id),
    KEY ix_common_cache_node_lease_live (cluster_hash,lease_until),
    CONSTRAINT ck_common_cache_node_lease_epoch CHECK (epoch>0 AND deleted=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE common_cache_task ADD COLUMN node_scope_json JSON NULL;
