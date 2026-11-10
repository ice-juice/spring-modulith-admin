CREATE TABLE config_user_preference(
 id BIGINT NOT NULL PRIMARY KEY,tenant_id BIGINT NOT NULL,user_id BIGINT NOT NULL,locale VARCHAR(16) NOT NULL,time_zone VARCHAR(64) NOT NULL,
 theme VARCHAR(16) NOT NULL,tabs_enabled TINYINT NOT NULL,keep_tabs_after_reload TINYINT NOT NULL,
 created_at DATETIME(3) NOT NULL,created_by BIGINT NOT NULL,updated_at DATETIME(3) NOT NULL,updated_by BIGINT NOT NULL,deleted TINYINT NOT NULL DEFAULT 0,
 active_user BIGINT GENERATED ALWAYS AS(CASE WHEN deleted=0 THEN user_id ELSE NULL END) STORED,UNIQUE KEY uk_user_preference(tenant_id,active_user)
);
