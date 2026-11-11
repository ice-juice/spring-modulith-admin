-- Coalesced hints only: message content and authorization remain in their owning tables.
CREATE TABLE message_socket_signal_guard (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_socket_signal_guard (tenant_id)
);
CREATE TABLE message_socket_signal (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
 portal VARCHAR(32) NOT NULL, user_id BIGINT NOT NULL,
 signal_type VARCHAR(16) NOT NULL, object_id BIGINT NOT NULL, revision BIGINT NOT NULL,
 reason VARCHAR(32) NULL, expires_at DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_socket_signal_subject (tenant_id,portal,user_id,signal_type),
 KEY ix_socket_signal_expiry (tenant_id,expires_at),
 CONSTRAINT ck_socket_signal_identity CHECK (tenant_id>0 AND user_id>0 AND object_id>0 AND revision>0),
 CONSTRAINT ck_socket_signal_type CHECK (signal_type IN ('notice','todo','private-message','session-ended')),
 CONSTRAINT ck_socket_signal_scope CHECK ((signal_type='session-ended' AND portal='*' AND reason IS NOT NULL) OR (signal_type<>'session-ended' AND portal<>'*' AND reason IS NULL))
);
