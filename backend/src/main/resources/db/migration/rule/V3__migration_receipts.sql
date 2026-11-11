CREATE TABLE rule_migration_batch (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, portal VARCHAR(32) NOT NULL,
 operation_key VARCHAR(64) NOT NULL, request_hash CHAR(64) NOT NULL, state VARCHAR(32) NOT NULL, report_json MEDIUMTEXT NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL,
 updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_rule_batch(tenant_id,portal,operation_key)
);
