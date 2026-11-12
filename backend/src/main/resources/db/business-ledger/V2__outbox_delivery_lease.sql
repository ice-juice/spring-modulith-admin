-- Unknown delivery is retried only with the same immutable event and a new fence.
ALTER TABLE common_business_outbox
    ADD COLUMN delivery_fence BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN lease_until DATETIME(3) NULL,
    ADD COLUMN attempts INT NOT NULL DEFAULT 0;
CREATE INDEX ix_common_business_outbox_claim ON common_business_outbox (state,lease_until,id);
