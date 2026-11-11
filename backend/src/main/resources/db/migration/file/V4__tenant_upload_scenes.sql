CREATE TABLE file_scene (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, application VARCHAR(64) NOT NULL, code VARCHAR(96) NOT NULL,
 name VARCHAR(128) NOT NULL, draft_json JSON NOT NULL, version BIGINT NOT NULL, published_version BIGINT NOT NULL,
 enabled TINYINT(1) NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 active_code VARCHAR(96) GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN code ELSE NULL END) STORED,
 UNIQUE KEY uk_file_scene_code (tenant_id,active_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE file_scene_revision (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, scene_id BIGINT NOT NULL, version BIGINT NOT NULL,
 configuration_json JSON NOT NULL, profiles_json JSON NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_file_scene_revision (tenant_id,scene_id,version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
