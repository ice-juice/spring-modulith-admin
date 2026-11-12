-- Business resources use their own Flyway history. Do not scan this location on the platform pool.
CREATE TABLE common_cache_revision (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL,
    region VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    revision BIGINT NOT NULL, control_version BIGINT NOT NULL DEFAULT 0,
    stats_epoch BIGINT NOT NULL DEFAULT 0, paused TINYINT(1) NOT NULL DEFAULT 0,
    policy_ttl_millis BIGINT NULL, policy_negative_ttl_millis BIGINT NULL,
    policy_max_entries BIGINT NULL, policy_max_bytes BIGINT NULL, policy_max_entry_bytes INT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id), UNIQUE KEY uk_common_cache_revision_scope (tenant_id,region),
    CONSTRAINT ck_common_cache_revision_nonnegative CHECK (revision>=0 AND control_version>=0 AND stats_epoch>=0 AND deleted=0),
    CONSTRAINT ck_common_cache_control_policy CHECK (
        (policy_ttl_millis IS NULL AND policy_negative_ttl_millis IS NULL AND policy_max_entries IS NULL
         AND policy_max_bytes IS NULL AND policy_max_entry_bytes IS NULL)
        OR (policy_ttl_millis IS NOT NULL AND policy_negative_ttl_millis IS NOT NULL AND policy_max_entries IS NOT NULL
            AND policy_max_bytes IS NOT NULL AND policy_max_entry_bytes IS NOT NULL AND policy_ttl_millis BETWEEN 1 AND 86400000
            AND policy_negative_ttl_millis BETWEEN 0 AND policy_ttl_millis AND policy_max_entries>0 AND policy_max_bytes>0
            AND policy_max_entry_bytes>0 AND policy_max_entry_bytes<=policy_max_bytes)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE common_request_execution (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL,
    public_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operation VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resource_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    key_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    input_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    fingerprint_key_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'v1',
    permission_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_common_request_key (tenant_id,actor_hash,operation,resource_hash,key_hash),
    UNIQUE KEY uk_common_request_actor_key (tenant_id,actor_hash,key_hash),
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

CREATE TABLE common_business_outbox (
    id BIGINT NOT NULL, tenant_id BIGINT NOT NULL,
    event_type VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    key_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload VARBINARY(16384) NOT NULL,
    state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_common_business_outbox_key (tenant_id,event_type,key_hash),
    KEY ix_common_business_outbox_pending (state,updated_at,id),
    CONSTRAINT ck_common_business_outbox_state CHECK (state IN ('PENDING','UNKNOWN','DONE') AND deleted=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
