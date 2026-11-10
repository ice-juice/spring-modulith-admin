CREATE TABLE audit_operation_log(
 id BIGINT NOT NULL PRIMARY KEY,tenant_id BIGINT NOT NULL,event_id VARCHAR(36) NOT NULL,user_id BIGINT NOT NULL,dept_id BIGINT,username VARCHAR(64) NOT NULL,portal VARCHAR(32) NOT NULL,
 action VARCHAR(128) NOT NULL,message_key VARCHAR(256) NOT NULL,message_args TEXT NOT NULL,default_message VARCHAR(512) NOT NULL,object_id VARCHAR(128),client_path VARCHAR(512),request_path VARCHAR(512),
 result_code INT NOT NULL,error_key VARCHAR(256),duration_ms BIGINT NOT NULL,ip VARCHAR(64),user_agent VARCHAR(512),occurred_at DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL,created_by BIGINT NOT NULL,updated_at DATETIME(3) NOT NULL,updated_by BIGINT NOT NULL,deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_operation_event(tenant_id,event_id),KEY ix_operation_user(tenant_id,user_id,occurred_at),KEY ix_operation_search(tenant_id,portal,occurred_at)
);
