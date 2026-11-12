-- A binding row is the serialization point for session admission and maintenance stop.
ALTER TABLE tenant_storage_binding DROP CHECK ck_tenant_storage_binding;
ALTER TABLE tenant_storage_binding ADD CONSTRAINT ck_tenant_storage_binding CHECK (
    owner_tenant_id>0 AND resource_revision>0 AND binding_version>0
    AND state IN ('PENDING','ACTIVE','DRAINING','DISABLED') AND deleted=0);

CREATE TABLE tenant_storage_session_lease (
    lease_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    binding_id BIGINT NOT NULL,
    owner_tenant_id BIGINT NOT NULL,
    storage_group VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    node_id VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    process_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    binding_version BIGINT NOT NULL,
    opened_at DATETIME(3) NOT NULL,
    PRIMARY KEY (lease_id),
    KEY ix_tenant_storage_session_binding (binding_id,node_id,process_id),
    CONSTRAINT ck_tenant_storage_session CHECK (binding_id>0 AND owner_tenant_id>0 AND binding_version>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tenant_storage_switch (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    operation_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    binding_id BIGINT NOT NULL,
    owner_tenant_id BIGINT NOT NULL,
    storage_group VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_resource_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_revision BIGINT NOT NULL,
    target_resource_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_revision BIGINT NOT NULL,
    expected_binding_version BIGINT NOT NULL,
    stop_generation BIGINT NOT NULL DEFAULT 0,
    state VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    manifest_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    manifest_version BIGINT NULL,
    backup_ref VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NULL,
    copy_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    copied_rows BIGINT NULL,
    source_outbox BIGINT NULL,
    final_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    final_rows BIGINT NULL,
    final_outbox BIGINT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_storage_switch_key (tenant_id,operation_id),
    KEY ix_tenant_storage_switch_binding (binding_id,state),
    CONSTRAINT ck_tenant_storage_switch CHECK (tenant_id>0 AND owner_tenant_id>0
        AND source_revision>0 AND target_revision>0 AND expected_binding_version>0
        AND stop_generation>=0 AND state IN ('PREPARING','COPIED','DRAINING','READY','NEEDS_REVIEW','SWITCHED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tenant_storage_switch_node (
    switch_id BIGINT NOT NULL,
    node_id VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    process_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    epoch BIGINT NOT NULL,
    drained_at DATETIME(3) NULL,
    PRIMARY KEY (switch_id,node_id),
    CONSTRAINT ck_tenant_storage_switch_node CHECK (switch_id>0 AND epoch>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
