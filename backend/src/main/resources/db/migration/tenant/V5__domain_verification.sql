ALTER TABLE tenant_domain
    ADD COLUMN configuration_version BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN dns_status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
    ADD COLUMN challenge_token VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN challenge_expires_at DATETIME(3) NULL,
    ADD COLUMN verified_at DATETIME(3) NULL,
    ADD COLUMN verified_challenge_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN tls_valid_until DATETIME(3) NULL,
    ADD COLUMN last_check_id BIGINT NULL,
    ADD COLUMN check_status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'IDLE',
    ADD COLUMN check_failure VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NONE',
    ADD COLUMN check_lease_until DATETIME(3) NULL,
    ADD COLUMN check_completed_at DATETIME(3) NULL,
    ADD CONSTRAINT ck_tenant_domain_version CHECK (configuration_version > 0);

UPDATE tenant_domain SET dns_status='LOCAL'
WHERE provenance='DEPLOYMENT' AND hostname IN ('localhost','127.0.0.1','[::1]') AND tls_status='LOCAL';

-- Check receipts belong to the authenticated platform tenant. owner_tenant_id is the target directory owner.
-- This table retains normal tenant isolation; it is not another globally exempt customer table.
CREATE TABLE tenant_domain_check (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    owner_tenant_id BIGINT NOT NULL,
    domain_id BIGINT NOT NULL,
    operation_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    intent_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    domain_version BIGINT NOT NULL,
    challenge_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    failure_code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ownership_verified TINYINT(1) NOT NULL DEFAULT 0,
    dns_ready TINYINT(1) NOT NULL DEFAULT 0,
    tls_ready TINYINT(1) NOT NULL DEFAULT 0,
    tls_valid_until DATETIME(3) NULL,
    addresses_json VARCHAR(2048) NOT NULL DEFAULT '[]',
    fingerprints_json VARCHAR(2048) NOT NULL DEFAULT '[]',
    lease_until DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tenant_domain_check_operation (tenant_id,domain_id,operation_id),
    KEY ix_tenant_domain_check_history (tenant_id,domain_id,created_at),
    CONSTRAINT ck_tenant_domain_check_status CHECK (status IN ('RUNNING','SUCCEEDED','FAILED','SUPERSEDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
