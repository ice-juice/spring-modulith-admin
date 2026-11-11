CREATE TABLE file_task_budget (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, active_tasks INT NOT NULL DEFAULT 0, retained_tasks INT NOT NULL DEFAULT 0,
 reserved_bytes BIGINT NOT NULL DEFAULT 0, minute_slot BIGINT NULL, minute_starts INT NOT NULL DEFAULT 0,
 day_slot BIGINT NULL, day_starts INT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_file_task_budget (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE file_upload_task (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, public_id CHAR(36) COLLATE utf8mb4_bin NOT NULL, file_id BIGINT NOT NULL,
 owner_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL, owner_json JSON NOT NULL, portal VARCHAR(64) NULL,
 key_hash CHAR(64) COLLATE utf8mb4_bin NOT NULL, input_fingerprint VARCHAR(256) COLLATE utf8mb4_bin NOT NULL,
 application VARCHAR(64) NOT NULL, scene_code VARCHAR(96) NOT NULL, snapshot_json JSON NOT NULL,
 original_name VARCHAR(510) NOT NULL, format VARCHAR(16) NOT NULL, mode VARCHAR(16) NOT NULL,
 expected_size BIGINT NOT NULL, expected_checksum CHAR(64) COLLATE utf8mb4_bin NOT NULL, part_bytes BIGINT NOT NULL, part_count INT NOT NULL,
 state VARCHAR(24) NOT NULL, version BIGINT NOT NULL, received_parts INT NOT NULL DEFAULT 0, received_bytes BIGINT NOT NULL DEFAULT 0,
 reserved_bytes BIGINT NOT NULL, reservation_released TINYINT(1) NOT NULL DEFAULT 0,
 expires_at DATETIME(3) NOT NULL, retain_until DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_file_upload_public (tenant_id,public_id), UNIQUE KEY uk_file_upload_intent (tenant_id,owner_hash,key_hash),
 UNIQUE KEY uk_file_upload_file (tenant_id,file_id), KEY ix_file_upload_expiry (tenant_id,state,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE file_upload_part (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, task_id BIGINT NOT NULL, part_number INT NOT NULL,
 size_bytes BIGINT NOT NULL, checksum CHAR(64) COLLATE utf8mb4_bin NOT NULL, state VARCHAR(16) NOT NULL,
 claim_id CHAR(36) COLLATE utf8mb4_bin NOT NULL, storage_receipt VARCHAR(512) COLLATE utf8mb4_bin NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_file_upload_part (tenant_id,task_id,part_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
