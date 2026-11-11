CREATE TABLE common_cache_confirmation (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    region VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    node_id VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expected_process VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    expected_epoch BIGINT NULL,
    manifest_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    required_revision BIGINT NOT NULL,
    required_control BIGINT NOT NULL,
    observed_revision BIGINT NULL,
    observed_control BIGINT NULL,
    state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    confirmed_at DATETIME(3) NULL,
    last_error VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
    last_attempt_at DATETIME(3) NULL,
    next_attempt_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_common_cache_confirmation_target (tenant_id,task_id,region,node_id),
    KEY ix_common_cache_confirmation_due (tenant_id,node_id,expected_process,state,next_attempt_at,id),
    CONSTRAINT ck_common_cache_confirmation_state CHECK (state IN ('WAITING','UNKNOWN','CONFIRMED') AND deleted=0),
    CONSTRAINT ck_common_cache_confirmation_versions CHECK (required_revision>=0 AND required_control>=0),
    CONSTRAINT ck_common_cache_confirmation_binding CHECK (
        (state='UNKNOWN' AND expected_process IS NULL AND expected_epoch IS NULL)
        OR (state<>'UNKNOWN' AND expected_process IS NOT NULL AND expected_epoch IS NOT NULL AND expected_epoch>0)),
    CONSTRAINT ck_common_cache_confirmation_evidence CHECK (
        (state='CONFIRMED' AND observed_revision IS NOT NULL AND observed_control IS NOT NULL
            AND observed_revision>=required_revision AND observed_control>=required_control AND confirmed_at IS NOT NULL)
        OR (state<>'CONFIRMED' AND observed_revision IS NULL AND observed_control IS NULL AND confirmed_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
