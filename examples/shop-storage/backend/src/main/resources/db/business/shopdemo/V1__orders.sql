CREATE TABLE shopdemo_order (
 id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,code VARCHAR(32) NOT NULL,
 created_at DATETIME(3) NOT NULL,created_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL,updated_by BIGINT NOT NULL,deleted TINYINT(1) NOT NULL DEFAULT 0,
 active_code VARCHAR(32) GENERATED ALWAYS AS(IF(deleted=0,code,NULL)) STORED,
 UNIQUE KEY uk_shopdemo_order(tenant_id,active_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE shopdemo_projection (
 id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,order_id BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL,created_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL,updated_by BIGINT NOT NULL,deleted TINYINT(1) NOT NULL DEFAULT 0,
 UNIQUE KEY uk_shopdemo_projection(tenant_id,order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
