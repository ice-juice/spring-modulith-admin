ALTER TABLE mail_profile
 ADD quota_day DATE NULL,
 ADD day_attempts INT NOT NULL DEFAULT 0,
 ADD quota_minute BIGINT NULL,
 ADD minute_attempts INT NOT NULL DEFAULT 0;
CREATE TABLE mail_delivery (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
 application VARCHAR(64) NOT NULL, scene VARCHAR(64) NOT NULL, idempotency_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
 template_id BIGINT NOT NULL, template_version BIGINT NOT NULL,
 profile_id BIGINT NOT NULL, profile_version BIGINT NOT NULL, credential_version BIGINT NOT NULL,
 actor_kind VARCHAR(24) NOT NULL, actor_subject VARCHAR(128) COLLATE utf8mb4_bin NOT NULL, actor_realm BIGINT, actor_client BIGINT,
 recipient_masked VARCHAR(260) NOT NULL, payload_envelope MEDIUMTEXT COLLATE utf8mb4_bin,
 state VARCHAR(24) NOT NULL, attempts INT NOT NULL, fence BIGINT NOT NULL,
 lease_owner VARCHAR(64) COLLATE utf8mb4_bin, lease_until DATETIME(3), next_attempt_at DATETIME(3) NOT NULL,
 expires_at DATETIME(3) NOT NULL, message_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL, last_result VARCHAR(64),
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_mail_delivery_idempotency (tenant_id,application,scene,idempotency_key),
 UNIQUE KEY uk_mail_delivery_message (tenant_id,message_id),
 KEY ix_mail_delivery_due (tenant_id,state,next_attempt_at,id),
 KEY ix_mail_delivery_lease (tenant_id,state,lease_until),
 KEY ix_mail_delivery_expiry (tenant_id,expires_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE mail_attempt (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, delivery_id BIGINT NOT NULL, fence BIGINT NOT NULL,
 state VARCHAR(24) NOT NULL, started_at DATETIME(3) NOT NULL, finished_at DATETIME(3), result_code VARCHAR(64),
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_mail_attempt_fence (tenant_id,delivery_id,fence)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
