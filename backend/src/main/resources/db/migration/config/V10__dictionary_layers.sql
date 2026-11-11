ALTER TABLE config_write_lock
    ADD COLUMN dictionary_revision BIGINT NOT NULL DEFAULT 0;

CREATE TABLE config_dictionary_layer (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    dictionary_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    definition_contract CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    legacy_type_id BIGINT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    label_json VARCHAR(4096) NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    active_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_general_ci GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN dictionary_key ELSE NULL END) STORED,
    UNIQUE KEY uk_config_dictionary_layer (tenant_id,active_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Codes are exact, case-sensitive, NO PAD strings. Shared overrides never copy shared defaults.
CREATE TABLE config_dictionary_entry (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    layer_id BIGINT NOT NULL,
    entry_kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    code VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    parent_code VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    label_json VARCHAR(4096) NULL,
    sort INT NULL,
    hidden TINYINT(1) NULL,
    enabled TINYINT(1) NULL,
    legacy_item_id BIGINT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    active_code VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN code ELSE NULL END) STORED,
    UNIQUE KEY uk_config_dictionary_entry (tenant_id,layer_id,active_code),
    KEY ix_config_dictionary_entry_layer (tenant_id,layer_id,deleted),
    CONSTRAINT ck_config_dictionary_entry_kind CHECK (entry_kind IN ('OVERRIDE','PRIVATE')),
    CONSTRAINT ck_config_dictionary_entry_sort CHECK (sort IS NULL OR sort >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
