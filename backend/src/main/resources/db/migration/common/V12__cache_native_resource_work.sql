-- All task readers/workers must understand native bindings and UNKNOWN before resource operations are enabled.
ALTER TABLE common_cache_task
    DROP CHECK ck_common_cache_task_state,
    ADD CONSTRAINT ck_common_cache_task_state CHECK (state IN ('PREVIEW','QUEUED','RUNNING','SUCCEEDED','PARTIAL','FAILED','CANCELLED','UNKNOWN'));

ALTER TABLE common_cache_task_item
    DROP CHECK ck_common_cache_task_item_state,
    ADD CONSTRAINT ck_common_cache_task_item_state CHECK (state IN ('PENDING','APPLIED','DENIED','CHANGED','FAILED','CANCELLED','UNKNOWN')),
    ADD COLUMN native_process VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
    ADD COLUMN native_deadline DATETIME(3) NULL,
    ADD COLUMN native_started_at DATETIME(3) NULL,
    ADD COLUMN native_completed_at DATETIME(3) NULL,
    ADD COLUMN native_affected BIGINT NULL,
    ADD COLUMN native_remaining BIGINT NULL,
    ADD CONSTRAINT ck_common_cache_task_native_binding CHECK (
        (native_process = '' AND native_deadline IS NULL AND native_started_at IS NULL AND native_completed_at IS NULL AND native_affected IS NULL AND native_remaining IS NULL)
        OR (CHAR_LENGTH(native_process) = 36 AND native_deadline IS NOT NULL)),
    ADD CONSTRAINT ck_common_cache_task_native_result CHECK (
        (native_completed_at IS NULL AND native_affected IS NULL AND native_remaining IS NULL)
        OR (native_started_at IS NOT NULL AND native_completed_at IS NOT NULL AND native_affected IS NOT NULL AND native_remaining IS NOT NULL AND native_affected >= -1 AND native_remaining >= -1 AND state = 'APPLIED')),
    ADD CONSTRAINT ck_common_cache_task_native_applied CHECK (native_process = '' OR state <> 'APPLIED' OR native_completed_at IS NOT NULL),
    ADD CONSTRAINT ck_common_cache_task_native_unknown CHECK (state <> 'UNKNOWN' OR (native_process <> '' AND native_started_at IS NOT NULL AND native_completed_at IS NULL));
