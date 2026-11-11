ALTER TABLE config_parameter
    MODIFY COLUMN param_value TEXT NULL,
    ADD COLUMN definition_contract CHAR(64) NULL,
    ADD COLUMN value_overridden TINYINT(1) NOT NULL DEFAULT 1;

ALTER TABLE config_write_lock
    ADD COLUMN parameter_revision BIGINT NOT NULL DEFAULT 0;
