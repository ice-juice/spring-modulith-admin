ALTER TABLE auth_session ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0;
