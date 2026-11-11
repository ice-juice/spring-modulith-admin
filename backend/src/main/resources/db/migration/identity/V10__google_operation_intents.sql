ALTER TABLE identity_login_transaction ADD intent VARCHAR(16) NOT NULL DEFAULT 'LOGIN',
 ADD operation_json JSON NULL;
