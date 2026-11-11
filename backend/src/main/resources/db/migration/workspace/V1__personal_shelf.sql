CREATE TABLE workspace_shelf (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, user_id BIGINT NOT NULL, portal VARCHAR(32) NOT NULL,
 display_mode VARCHAR(16) NOT NULL DEFAULT 'drawer', version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_workspace_shelf_owner(tenant_id,user_id,portal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE workspace_bookmark (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, user_id BIGINT NOT NULL, portal VARCHAR(32) NOT NULL,
 menu_id BIGINT NOT NULL, alias VARCHAR(50) NOT NULL DEFAULT '', position INT NOT NULL,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT(1) NOT NULL DEFAULT 0,
 active_menu BIGINT GENERATED ALWAYS AS(CASE WHEN deleted=0 THEN menu_id ELSE NULL END) STORED,
 UNIQUE KEY uk_workspace_bookmark_menu(tenant_id,user_id,portal,active_menu)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE workspace_note (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, user_id BIGINT NOT NULL, portal VARCHAR(32) NOT NULL,
 request_key VARCHAR(36) NOT NULL, title VARCHAR(50) NOT NULL, kind VARCHAR(16) NOT NULL,
 content MEDIUMTEXT NOT NULL, items_json MEDIUMTEXT NOT NULL, color VARCHAR(16) NOT NULL, pinned TINYINT(1) NOT NULL DEFAULT 0,
 status VARCHAR(16) NOT NULL, trashed_at DATETIME(3) NULL, related_menu_id BIGINT NULL, version BIGINT NOT NULL DEFAULT 1,
 created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, updated_by BIGINT NOT NULL, deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_workspace_note_request(tenant_id,user_id,portal,request_key),
 KEY ix_workspace_note_owner(tenant_id,user_id,portal,status,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
