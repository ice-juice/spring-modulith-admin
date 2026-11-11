ALTER TABLE config_user_preference
 ADD COLUMN skin VARCHAR(16) NOT NULL DEFAULT 'iris' AFTER theme;
