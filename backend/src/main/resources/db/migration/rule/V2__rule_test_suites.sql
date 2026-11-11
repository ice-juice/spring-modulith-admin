CREATE TABLE rule_testcase (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, portal VARCHAR(32) NOT NULL,
 template_id BIGINT NOT NULL, revision BIGINT NOT NULL, enabled TINYINT NOT NULL DEFAULT 1, payload_json MEDIUMTEXT NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL,
 updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
 KEY ix_rule_cases(tenant_id,template_id,enabled,deleted)
);
CREATE TABLE rule_testrun (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, portal VARCHAR(32) NOT NULL,
 template_id BIGINT NOT NULL, version_id BIGINT, content_hash CHAR(64) NOT NULL,
 case_set_hash CHAR(64) NOT NULL, passed TINYINT NOT NULL, report_json MEDIUMTEXT NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL,
 updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
 KEY ix_rule_runs(tenant_id,template_id,created_at)
);
