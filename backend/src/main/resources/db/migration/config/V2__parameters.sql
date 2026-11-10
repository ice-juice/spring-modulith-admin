CREATE TABLE config_parameter_group(
 id BIGINT NOT NULL PRIMARY KEY,tenant_id BIGINT NOT NULL,group_key VARCHAR(64) NOT NULL,name VARCHAR(128) NOT NULL,name_key VARCHAR(256),builtin TINYINT NOT NULL DEFAULT 0,sort INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL,created_by BIGINT NOT NULL,updated_at DATETIME(3) NOT NULL,updated_by BIGINT NOT NULL,deleted TINYINT NOT NULL DEFAULT 0,
 active_key VARCHAR(64) GENERATED ALWAYS AS(CASE WHEN deleted=0 THEN group_key ELSE NULL END) STORED,UNIQUE KEY uk_parameter_group(tenant_id,active_key)
);
CREATE TABLE config_parameter(
 id BIGINT NOT NULL PRIMARY KEY,tenant_id BIGINT NOT NULL,param_key VARCHAR(128) NOT NULL,group_key VARCHAR(64) NOT NULL,name VARCHAR(128) NOT NULL,name_key VARCHAR(256),
 value_type VARCHAR(16) NOT NULL,param_value TEXT NOT NULL,builtin TINYINT NOT NULL DEFAULT 0,public_readable TINYINT NOT NULL DEFAULT 0,secret TINYINT NOT NULL DEFAULT 0,sort INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL,created_by BIGINT NOT NULL,updated_at DATETIME(3) NOT NULL,updated_by BIGINT NOT NULL,deleted TINYINT NOT NULL DEFAULT 0,
 active_key VARCHAR(128) GENERATED ALWAYS AS(CASE WHEN deleted=0 THEN param_key ELSE NULL END) STORED,UNIQUE KEY uk_parameter(tenant_id,active_key),KEY ix_parameter_group(tenant_id,group_key,deleted)
);
