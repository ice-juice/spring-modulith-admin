ALTER TABLE config_shared_head
    ADD COLUMN dictionary_code_count BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_config_dictionary_code_count CHECK (dictionary_code_count >= 0 AND dictionary_code_count <= 65536);

-- Cross-tenant namespace metadata only: no tenant, private code text, labels, parents or business content.
CREATE TABLE config_dictionary_code (
    id BIGINT NOT NULL PRIMARY KEY,
    head_id BIGINT NOT NULL,
    code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ownership VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_config_dictionary_code_identity (head_id,code_hash),
    CONSTRAINT ck_config_dictionary_code_ownership CHECK (ownership IN ('SHARED','PRIVATE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
