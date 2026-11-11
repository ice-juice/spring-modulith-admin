-- Reviewed platform metadata only. Customer overrides remain in tenant-isolated Config tables.
CREATE TABLE config_shared_head (
    id BIGINT NOT NULL PRIMARY KEY,
    kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    config_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    owner_module VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    definition_json MEDIUMTEXT NOT NULL,
    contract_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    draft_json MEDIUMTEXT NOT NULL,
    draft_label_json VARCHAR(4096) NOT NULL,
    draft_version BIGINT NOT NULL DEFAULT 1,
    published_revision_id BIGINT NULL,
    next_revision BIGINT NOT NULL DEFAULT 1,
    history_bytes BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_config_shared_head_key (kind,config_key),
    CONSTRAINT ck_config_shared_head_kind CHECK (kind IN ('PARAMETER','DICTIONARY')),
    CONSTRAINT ck_config_shared_head_versions CHECK (draft_version > 0 AND next_revision > 0 AND history_bytes >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE config_shared_revision (
    id BIGINT NOT NULL PRIMARY KEY,
    head_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    contract_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content_json MEDIUMTEXT NOT NULL,
    content_bytes INT NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_config_shared_revision_number (head_id,revision),
    KEY ix_config_shared_revision_head (head_id,id),
    CONSTRAINT ck_config_shared_revision_bounds CHECK (revision > 0 AND content_bytes > 0 AND content_bytes <= 262144)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
