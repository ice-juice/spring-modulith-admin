-- Deploy every task reader/consumer before explicitly enabling PREWARM; old action decoders cannot coexist.
ALTER TABLE common_cache_task
    DROP CHECK ck_common_cache_task_action,
    ADD CONSTRAINT ck_common_cache_task_action CHECK (action IN ('CLEAR','PAUSE','RESUME','RESET_STATS','PREWARM')),
    ADD COLUMN warm_started_at DATETIME(3) NULL;

ALTER TABLE common_cache_task_item
    ADD COLUMN warm_cursor VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
    ADD COLUMN warm_reads BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN warm_process VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
    ADD COLUMN warm_started_at DATETIME(3) NULL,
    ADD CONSTRAINT ck_common_cache_task_warm_reads CHECK (warm_reads >= 0 AND warm_reads <= 1000),
    ADD CONSTRAINT ck_common_cache_task_warm_process CHECK (
        (warm_started_at IS NULL AND warm_process = '' AND warm_reads = 0 AND warm_cursor = '')
        OR (warm_started_at IS NOT NULL AND CHAR_LENGTH(warm_process) = 36));
