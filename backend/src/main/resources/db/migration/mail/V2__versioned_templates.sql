CREATE TABLE mail_template (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
 code VARCHAR(64) NOT NULL, application VARCHAR(64) NOT NULL, scene VARCHAR(64) NOT NULL,
 language VARCHAR(16) NOT NULL, profile_code VARCHAR(64) NOT NULL, content_json JSON NOT NULL,
 enabled TINYINT(1) NOT NULL, version BIGINT NOT NULL, published_version BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT(1) NOT NULL DEFAULT 0,
 active_code VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN code ELSE NULL END) STORED,
 UNIQUE KEY uk_mail_template_tenant_code_locale (tenant_id,active_code,language)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE mail_template_version (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, template_id BIGINT NOT NULL, version BIGINT NOT NULL,
 profile_id BIGINT NOT NULL, profile_version BIGINT NOT NULL, credential_version BIGINT NOT NULL, content_json JSON NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_mail_template_version (tenant_id,template_id,version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
