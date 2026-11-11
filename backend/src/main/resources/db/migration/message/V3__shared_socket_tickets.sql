-- Security state: never ordinary cache data. Old in-memory tickets are not migrated.
CREATE TABLE message_socket_ticket_guard (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_socket_ticket_guard (tenant_id)
);
CREATE TABLE message_socket_ticket (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
 portal VARCHAR(32) NOT NULL, user_id BIGINT NOT NULL, session_id BIGINT NOT NULL,
 token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 expires_at DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_socket_ticket_session (tenant_id,portal,session_id),
 UNIQUE KEY uk_socket_ticket_hash (tenant_id,portal,token_hash),
 KEY ix_socket_ticket_expiry (tenant_id,expires_at),
 CONSTRAINT ck_socket_ticket_identity CHECK (tenant_id>0 AND user_id>0 AND session_id>0)
);
