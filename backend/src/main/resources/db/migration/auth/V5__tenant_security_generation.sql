-- Existing sessions/challenges lack a proven tenant generation and must not survive this security upgrade.
ALTER TABLE auth_session ADD COLUMN tenant_security_generation BIGINT NULL;
ALTER TABLE auth_challenge ADD COLUMN tenant_security_generation BIGINT NULL;
ALTER TABLE auth_captcha ADD COLUMN tenant_security_generation BIGINT NULL;
ALTER TABLE auth_captcha ADD COLUMN portal VARCHAR(32) NULL;
