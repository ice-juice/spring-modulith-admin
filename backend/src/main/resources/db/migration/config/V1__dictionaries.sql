CREATE TABLE config_dictionary_type (
 id BIGINT NOT NULL PRIMARY KEY,tenant_id BIGINT NOT NULL,type_key VARCHAR(64) NOT NULL,
 name VARCHAR(128) NOT NULL,name_key VARCHAR(256),tree TINYINT NOT NULL DEFAULT 0,builtin TINYINT NOT NULL DEFAULT 0,enabled TINYINT NOT NULL DEFAULT 1,sort INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL,created_by BIGINT NOT NULL,updated_at DATETIME(3) NOT NULL,updated_by BIGINT NOT NULL,deleted TINYINT NOT NULL DEFAULT 0,
 active_key VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN type_key ELSE NULL END) STORED,
 UNIQUE KEY uk_dictionary_type(tenant_id,active_key)
);
CREATE TABLE config_dictionary_item (
 id BIGINT NOT NULL PRIMARY KEY,tenant_id BIGINT NOT NULL,type_id BIGINT NOT NULL,parent_id BIGINT NOT NULL DEFAULT 0,
 item_value VARCHAR(128) NOT NULL,name VARCHAR(128) NOT NULL,name_key VARCHAR(256),builtin TINYINT NOT NULL DEFAULT 0,enabled TINYINT NOT NULL DEFAULT 1,sort INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL,created_by BIGINT NOT NULL,updated_at DATETIME(3) NOT NULL,updated_by BIGINT NOT NULL,deleted TINYINT NOT NULL DEFAULT 0,
 active_value VARCHAR(128) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN item_value ELSE NULL END) STORED,
 UNIQUE KEY uk_dictionary_item(tenant_id,type_id,active_value),KEY ix_dictionary_parent(tenant_id,type_id,parent_id,deleted)
);
CREATE TABLE config_i18n_text (
 id BIGINT NOT NULL PRIMARY KEY,tenant_id BIGINT NOT NULL,entity_type VARCHAR(64) NOT NULL,entity_id BIGINT NOT NULL,locale VARCHAR(16) NOT NULL,text VARCHAR(128) NOT NULL,
 created_at DATETIME(3) NOT NULL,created_by BIGINT NOT NULL,updated_at DATETIME(3) NOT NULL,updated_by BIGINT NOT NULL,deleted TINYINT NOT NULL DEFAULT 0,
 KEY ix_config_i18n(tenant_id,entity_type,entity_id,locale,deleted)
);
CREATE TABLE config_write_lock (
 id BIGINT NOT NULL PRIMARY KEY,tenant_id BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL,created_by BIGINT NOT NULL,updated_at DATETIME(3) NOT NULL,updated_by BIGINT NOT NULL,deleted TINYINT NOT NULL DEFAULT 0
);
