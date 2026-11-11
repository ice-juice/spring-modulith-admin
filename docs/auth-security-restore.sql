-- OFFLINE ONLY: stop/isolate every application node before restoring the database.
-- Reconcile credential/binding changes after the backup using trusted binlogs first.
-- Run against the explicitly selected restored schema, before reopening any traffic.
-- This is NOT a TOTP reset or a way to recover a lost authenticator.
START TRANSACTION;
DELETE FROM auth_session;
DELETE FROM auth_challenge;
UPDATE auth_totp_credential
SET pending_secret_ciphertext=NULL, pending_challenge_id=NULL, pending_expires_at=NULL,
    last_accepted_step=CASE WHEN enabled=1
        THEN GREATEST(COALESCE(last_accepted_step,0),FLOOR(UNIX_TIMESTAMP()/30)+1)
        ELSE last_accepted_step END,
    updated_at=UTC_TIMESTAMP(3), updated_by=0;
UPDATE auth_user_security
SET security_version=security_version+1, updated_at=UTC_TIMESTAMP(3), updated_by=0
WHERE deleted=0;
COMMIT;
-- Keep throttle rows. Leave all nodes offline for at least 15 minutes after this
-- transaction (and until clocks are synchronized), so a rollback cannot refund
-- the most recent guessing window or make a previously accepted TOTP reusable.
