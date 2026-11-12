-- Platform owned directory. No credentials or business payloads are stored here.
CREATE TABLE tenant_storage_resource (
    id BIGINT NOT NULL,
    resource_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    revision BIGINT NOT NULL,
    display_name VARCHAR(96) NOT NULL,
    db_host VARCHAR(253) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    db_port INT NOT NULL,
    database_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    tls_mode VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    secret_ref VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    pool_max INT NOT NULL,
    connect_timeout_millis INT NOT NULL,
    state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_storage_resource_revision (resource_id,revision),
    KEY ix_tenant_storage_resource_state (state,resource_id),
    CONSTRAINT ck_tenant_storage_resource CHECK (revision>0 AND db_port BETWEEN 1 AND 65535
        AND pool_max BETWEEN 1 AND 8 AND connect_timeout_millis BETWEEN 100 AND 5000
        AND state IN ('REGISTERED','RETIRED') AND deleted=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tenant_storage_binding (
    id BIGINT NOT NULL,
    owner_tenant_id BIGINT NOT NULL,
    storage_group VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resource_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resource_revision BIGINT NOT NULL,
    schema_compatibility VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    binding_version BIGINT NOT NULL,
    state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_storage_binding_group (owner_tenant_id,storage_group),
    KEY ix_tenant_storage_binding_resource (resource_id,resource_revision,state),
    CONSTRAINT ck_tenant_storage_binding CHECK (owner_tenant_id>0 AND resource_revision>0
        AND binding_version>0 AND state IN ('PENDING','ACTIVE','DISABLED') AND deleted=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
