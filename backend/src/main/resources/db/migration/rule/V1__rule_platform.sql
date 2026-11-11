CREATE TABLE rule_asset (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, portal VARCHAR(32) NOT NULL,
 kind VARCHAR(16) NOT NULL, code VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL, reference_id BIGINT,
 state VARCHAR(16) NOT NULL DEFAULT 'DRAFT', draft_json MEDIUMTEXT NOT NULL,
 revision BIGINT NOT NULL DEFAULT 1, release_revision BIGINT NOT NULL DEFAULT 0, active_version_id BIGINT, trial_hash CHAR(64),
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL,
 updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
 active_code VARCHAR(64) GENERATED ALWAYS AS(CASE WHEN deleted=0 THEN code ELSE NULL END) STORED,
 UNIQUE KEY uk_rule_asset(tenant_id,portal,kind,active_code), KEY ix_rule_asset_reference(tenant_id,reference_id)
);
CREATE TABLE rule_version (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, portal VARCHAR(32) NOT NULL,
 asset_id BIGINT NOT NULL, kind VARCHAR(16) NOT NULL, revision BIGINT NOT NULL,
 content_json MEDIUMTEXT NOT NULL, content_hash CHAR(64) NOT NULL, dependency_id BIGINT,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL,
 updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_rule_version(tenant_id,asset_id,revision), KEY ix_rule_version_dependency(tenant_id,dependency_id)
);
CREATE TABLE rule_release (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, portal VARCHAR(32) NOT NULL,
 asset_id BIGINT NOT NULL, version_id BIGINT, release_revision BIGINT NOT NULL,
 action VARCHAR(32) NOT NULL, operation_key VARCHAR(64) NOT NULL, fingerprint CHAR(64) NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL,
 updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_rule_operation(tenant_id,portal,operation_key),
 UNIQUE KEY uk_rule_release(tenant_id,asset_id,release_revision)
);
CREATE TABLE rule_execution (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, portal VARCHAR(32) NOT NULL,
 instance_id BIGINT NOT NULL, instance_revision BIGINT, template_version_id BIGINT,
 status VARCHAR(32) NOT NULL, content_hash CHAR(64), input_hash CHAR(64),
 trace_json TEXT NOT NULL, problems_json TEXT NOT NULL, duration_micros BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL,
 updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
 KEY ix_rule_execution(tenant_id,instance_id,created_at), KEY ix_rule_execution_retention(tenant_id,created_at)
);
CREATE TABLE rule_write_lock (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL,
 updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0
);
