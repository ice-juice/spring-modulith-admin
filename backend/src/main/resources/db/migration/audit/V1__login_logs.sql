CREATE TABLE audit_login_log(
 id BIGINT NOT NULL PRIMARY KEY,tenant_id BIGINT NOT NULL,event_id VARCHAR(36) NOT NULL,user_id BIGINT,dept_id BIGINT,username VARCHAR(64) NOT NULL,portal VARCHAR(32) NOT NULL,status VARCHAR(32) NOT NULL,
 ip VARCHAR(64),user_agent VARCHAR(512),browser VARCHAR(64),operating_system VARCHAR(64),country VARCHAR(128),province VARCHAR(128),city VARCHAR(128),country_code VARCHAR(16),
 local_address TINYINT NOT NULL,ip_changed TINYINT NOT NULL,unusual_location TINYINT NOT NULL,occurred_at DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL,created_by BIGINT NOT NULL,updated_at DATETIME(3) NOT NULL,updated_by BIGINT NOT NULL,deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_login_event(tenant_id,event_id),KEY ix_login_user(tenant_id,user_id,occurred_at),KEY ix_login_search(tenant_id,portal,occurred_at)
);
