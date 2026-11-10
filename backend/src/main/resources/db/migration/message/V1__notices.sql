CREATE TABLE message_notice(
 id BIGINT NOT NULL PRIMARY KEY,tenant_id BIGINT NOT NULL,portal VARCHAR(32) NOT NULL,dept_id BIGINT,
 title VARCHAR(255) NOT NULL,content MEDIUMTEXT NOT NULL,type VARCHAR(16) NOT NULL,priority INT NOT NULL,audience_type VARCHAR(16) NOT NULL,audience_ids TEXT NOT NULL,
 created_at DATETIME(3) NOT NULL,created_by BIGINT NOT NULL,updated_at DATETIME(3) NOT NULL,updated_by BIGINT NOT NULL,deleted TINYINT NOT NULL DEFAULT 0,
 KEY ix_notice_portal(tenant_id,portal,deleted,id),KEY ix_notice_department(tenant_id,dept_id,deleted)
);
CREATE TABLE message_notice_recipient(
 id BIGINT NOT NULL PRIMARY KEY,tenant_id BIGINT NOT NULL,notice_id BIGINT NOT NULL,user_id BIGINT NOT NULL,user_name VARCHAR(128) NOT NULL,read_at DATETIME(3),
 created_at DATETIME(3) NOT NULL,created_by BIGINT NOT NULL,updated_at DATETIME(3) NOT NULL,updated_by BIGINT NOT NULL,deleted TINYINT NOT NULL DEFAULT 0,
 active_user BIGINT GENERATED ALWAYS AS(IF(deleted=0,user_id,NULL)) STORED,
 UNIQUE KEY uk_recipient(tenant_id,notice_id,active_user),KEY ix_recipient_user(tenant_id,user_id,deleted,read_at)
);
CREATE TABLE message_notice_attachment(
 id BIGINT NOT NULL PRIMARY KEY,tenant_id BIGINT NOT NULL,notice_id BIGINT NOT NULL,file_id BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL,created_by BIGINT NOT NULL,updated_at DATETIME(3) NOT NULL,updated_by BIGINT NOT NULL,deleted TINYINT NOT NULL DEFAULT 0,
 KEY ix_attachment_notice(tenant_id,notice_id,deleted),KEY ix_attachment_file(tenant_id,file_id,deleted)
);
