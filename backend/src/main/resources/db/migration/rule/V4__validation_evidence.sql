ALTER TABLE rule_version ADD COLUMN test_run_id BIGINT, ADD COLUMN case_set_hash CHAR(64);
ALTER TABLE rule_execution ADD COLUMN release_revision BIGINT, MODIFY COLUMN trace_json MEDIUMTEXT NOT NULL;
