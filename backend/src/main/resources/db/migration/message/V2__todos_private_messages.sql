CREATE TABLE message_todo(
 id BIGINT NOT NULL PRIMARY KEY,tenant_id BIGINT NOT NULL,portal VARCHAR(32) NOT NULL,dept_id BIGINT,user_id BIGINT NOT NULL,
 title VARCHAR(255) NOT NULL,content TEXT,url VARCHAR(512),type INT NOT NULL,priority INT NOT NULL,state INT NOT NULL,due_at DATETIME(3) NOT NULL,
 completed_at DATETIME(3),cancelled_at DATETIME(3),read_at DATETIME(3),source_system VARCHAR(64),source_message_id VARCHAR(64),source_request_id VARCHAR(64),source_url VARCHAR(512),sender_key VARCHAR(64),receiver_key VARCHAR(64),
 created_at DATETIME(3) NOT NULL,created_by BIGINT NOT NULL,updated_at DATETIME(3) NOT NULL,updated_by BIGINT NOT NULL,deleted TINYINT NOT NULL DEFAULT 0,
 KEY ix_todo_user(tenant_id,portal,user_id,deleted,state,due_at),KEY ix_todo_owner(tenant_id,dept_id,created_by,deleted),KEY ix_todo_source(tenant_id,source_system,source_message_id)
);
CREATE TABLE message_private(
 id BIGINT NOT NULL PRIMARY KEY,tenant_id BIGINT NOT NULL,portal VARCHAR(32) NOT NULL,sender_id BIGINT NOT NULL,recipient_id BIGINT NOT NULL,
 sender_name VARCHAR(128) NOT NULL,recipient_name VARCHAR(128) NOT NULL,content TEXT NOT NULL,read_at DATETIME(3),
 created_at DATETIME(3) NOT NULL,created_by BIGINT NOT NULL,updated_at DATETIME(3) NOT NULL,updated_by BIGINT NOT NULL,deleted TINYINT NOT NULL DEFAULT 0,
 KEY ix_private_inbox(tenant_id,portal,recipient_id,deleted,read_at,id),KEY ix_private_outbox(tenant_id,portal,sender_id,deleted,id)
);
