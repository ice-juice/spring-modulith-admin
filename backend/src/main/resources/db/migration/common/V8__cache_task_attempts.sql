ALTER TABLE common_cache_task_item
    ADD COLUMN attempts INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_common_cache_task_item_attempts CHECK (attempts >= 0 AND attempts <= 3);
