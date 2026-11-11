ALTER TABLE mail_profile ADD recipient_key_envelope TEXT COLLATE utf8mb4_bin NULL;
ALTER TABLE mail_delivery
 ADD recipient_key VARCHAR(64) COLLATE utf8mb4_bin NULL,
 ADD receipt_state VARCHAR(24) NULL;
CREATE TABLE mail_receipt (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
 profile_id BIGINT NOT NULL, application VARCHAR(64) NOT NULL,
 provider VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, event_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
 delivery_id BIGINT NOT NULL, type VARCHAR(24) NOT NULL, occurred_at DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_mail_receipt_event (tenant_id,profile_id,provider,event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE mail_suppression (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
 profile_id BIGINT NOT NULL, application VARCHAR(64) NOT NULL,
 recipient_key VARCHAR(64) COLLATE utf8mb4_bin NOT NULL, reason VARCHAR(24) NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL,
 deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_mail_suppression_recipient (tenant_id,profile_id,application,recipient_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
