ALTER TABLE common_request_execution
    ADD COLUMN fingerprint_key_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'v1' AFTER input_hash;
