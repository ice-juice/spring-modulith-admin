-- Global control-plane metadata. No customer business table is exempted from tenant isolation.
CREATE TABLE tenant_tenant (
    id BIGINT NOT NULL PRIMARY KEY,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    security_generation BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    active_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (IF(deleted=0,code,NULL)) STORED,
    UNIQUE KEY uk_tenant_code (active_code),
    KEY ix_tenant_status (status,deleted,id),
    CONSTRAINT ck_tenant_status CHECK (status IN ('PROVISIONING','ACTIVE','SUSPENDED','MAINTENANCE','RETIRED')),
    CONSTRAINT ck_tenant_generation CHECK (security_generation > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tenant_domain (
    id BIGINT NOT NULL PRIMARY KEY,
    owner_tenant_id BIGINT NOT NULL,
    hostname VARCHAR(253) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    portal VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    primary_domain TINYINT(1) NOT NULL DEFAULT 0,
    verification_status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    tls_status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provenance VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    active_hostname VARCHAR(253) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (IF(deleted=0,hostname,NULL)) STORED,
    primary_owner VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS
        (IF(deleted=0 AND primary_domain=1,CONCAT(owner_tenant_id,':',portal),NULL)) STORED,
    UNIQUE KEY uk_tenant_domain_host (active_hostname),
    UNIQUE KEY uk_tenant_domain_primary (primary_owner),
    KEY ix_tenant_domain_owner (owner_tenant_id,portal,deleted),
    CONSTRAINT ck_tenant_domain_status CHECK (status IN ('PENDING','ACTIVE','DISABLED')),
    CONSTRAINT ck_tenant_domain_owner CHECK (owner_tenant_id > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
