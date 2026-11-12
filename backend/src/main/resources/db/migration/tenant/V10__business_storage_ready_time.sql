-- A fresh physical verification is required after READY and before cutover.
ALTER TABLE tenant_storage_switch ADD COLUMN ready_at DATETIME(3) NULL;
