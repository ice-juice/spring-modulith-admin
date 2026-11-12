CREATE TABLE defense_platform_event (
    id BIGINT NOT NULL PRIMARY KEY, 
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    correlation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at DATETIME(3) NOT NULL, received_at DATETIME(3) NOT NULL, expires_at DATETIME(3) NOT NULL,
    fact VARCHAR(64) NOT NULL, category VARCHAR(32) NOT NULL, severity VARCHAR(16) NOT NULL,
    outcome VARCHAR(16) NOT NULL, assessment VARCHAR(32) NOT NULL, rule_version INT NOT NULL,
    payload TEXT NOT NULL, payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    held TINYINT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_platform_event (event_id),
    KEY ix_platform_retention (held,expires_at,id),
    KEY ix_platform_recent (received_at,id),
    CONSTRAINT ck_platform_payload CHECK (OCTET_LENGTH(payload)<=2048),
    CONSTRAINT ck_platform_rule CHECK (rule_version>0)
);
CREATE TABLE defense_tenant_event (
    id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    correlation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at DATETIME(3) NOT NULL, received_at DATETIME(3) NOT NULL, expires_at DATETIME(3) NOT NULL,
    fact VARCHAR(64) NOT NULL, category VARCHAR(32) NOT NULL, severity VARCHAR(16) NOT NULL,
    outcome VARCHAR(16) NOT NULL, assessment VARCHAR(32) NOT NULL, rule_version INT NOT NULL,
    payload TEXT NOT NULL, payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    held TINYINT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tenant_event (tenant_id,event_id),
    KEY ix_tenant_retention (tenant_id,held,expires_at,id),
    KEY ix_tenant_recent (tenant_id,received_at,id),
    CONSTRAINT ck_tenant_payload CHECK (OCTET_LENGTH(payload)<=2048),
    CONSTRAINT ck_tenant_rule CHECK (rule_version>0)
);
CREATE TABLE defense_capacity (
    id BIGINT NOT NULL PRIMARY KEY, records BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0, CONSTRAINT ck_defense_records CHECK(records>=0)
);
INSERT INTO defense_capacity(id,records,created_at,created_by,updated_at,updated_by,deleted)
VALUES (1,0,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0),(2,0,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0);
CREATE TABLE defense_tenant_capacity (
    id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, records BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0, UNIQUE KEY uk_defense_tenant_capacity(tenant_id),
    CONSTRAINT ck_defense_tenant_records CHECK(records>=0)
);
