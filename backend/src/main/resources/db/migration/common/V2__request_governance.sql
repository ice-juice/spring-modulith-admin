CREATE TABLE common_request_execution (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operation VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resource_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    key_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    input_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    permission_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_common_request_key (tenant_id,actor_hash,operation,resource_hash,key_hash),
    UNIQUE KEY uk_common_request_public (public_id),
    KEY ix_common_request_state (tenant_id,state,updated_at),
    CONSTRAINT ck_common_request_state CHECK (state IN ('RUNNING','SUCCEEDED','FAILED_FINAL','UNKNOWN') AND deleted=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE common_request_mutex (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL,
    resource_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id), UNIQUE KEY uk_common_request_mutex (tenant_id,resource_hash),
    CONSTRAINT ck_common_request_mutex_live CHECK (deleted=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE common_request_budget (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL,
    subject_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    used_count INT NOT NULL, expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id), UNIQUE KEY uk_common_request_budget (tenant_id,subject_hash),
    KEY ix_common_request_budget_expiry (tenant_id,expires_at),
    CONSTRAINT ck_common_request_budget CHECK (used_count>=0 AND deleted=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
