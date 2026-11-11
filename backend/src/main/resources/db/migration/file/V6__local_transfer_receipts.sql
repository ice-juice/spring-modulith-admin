ALTER TABLE file_upload_part ADD attempts INT NOT NULL DEFAULT 1;
ALTER TABLE file_upload_task ADD freeze_claim CHAR(36) COLLATE utf8mb4_bin NULL,
 ADD frozen_receipt VARCHAR(512) COLLATE utf8mb4_bin NULL,
 ADD frozen_checksum CHAR(64) COLLATE utf8mb4_bin NULL;
