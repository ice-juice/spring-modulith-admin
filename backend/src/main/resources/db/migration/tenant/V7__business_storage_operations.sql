-- Platform operator task receipts; target business rows and credentials never live here.
CREATE TABLE tenant_storage_operation (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    operation_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    binding_id BIGINT NOT NULL,
    owner_tenant_id BIGINT NOT NULL,
    storage_group VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resource_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resource_revision BIGINT NOT NULL,
    expected_binding_version BIGINT NOT NULL,
    fence BIGINT NOT NULL,
    state VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    lease_until DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_storage_operation_key (tenant_id,operation_id),
    KEY ix_tenant_storage_operation_binding (binding_id,state),
    CONSTRAINT ck_tenant_storage_operation CHECK (tenant_id>0 AND owner_tenant_id>0
        AND resource_revision>0 AND expected_binding_version>0 AND fence>0
        AND state IN ('RUNNING','NEEDS_REVIEW','SUCCEEDED') AND deleted=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
