CREATE TABLE openapi_application(
 id BIGINT NOT NULL PRIMARY KEY,tenant_id BIGINT NOT NULL,portal VARCHAR(32) NOT NULL,dept_id BIGINT,name VARCHAR(128) NOT NULL,type INT NOT NULL,description VARCHAR(200),
 app_id CHAR(32) NOT NULL,secret_cipher VARCHAR(256) NOT NULL,enabled TINYINT NOT NULL,signature_required TINYINT NOT NULL,credential_version BIGINT NOT NULL DEFAULT 0,
 target_type VARCHAR(64),target_id VARCHAR(128),target_name VARCHAR(128),
 created_at DATETIME(3) NOT NULL,created_by BIGINT NOT NULL,updated_at DATETIME(3) NOT NULL,updated_by BIGINT NOT NULL,deleted TINYINT NOT NULL DEFAULT 0,
 active_name VARCHAR(128) GENERATED ALWAYS AS(IF(deleted=0,name,NULL)) STORED,
 UNIQUE KEY uk_application_name(tenant_id,portal,active_name),UNIQUE KEY uk_application_app_id(app_id),KEY ix_application_scope(tenant_id,dept_id,created_by,deleted)
);
