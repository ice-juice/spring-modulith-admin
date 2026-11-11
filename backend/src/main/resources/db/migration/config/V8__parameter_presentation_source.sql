ALTER TABLE config_parameter
    ADD COLUMN presentation_overridden TINYINT(1) NOT NULL DEFAULT 1;
