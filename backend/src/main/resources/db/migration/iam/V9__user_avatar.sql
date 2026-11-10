ALTER TABLE iam_user ADD COLUMN avatar_file_id BIGINT NULL;
CREATE INDEX idx_iam_user_avatar ON iam_user(tenant_id, avatar_file_id, deleted);
