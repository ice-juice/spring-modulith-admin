CREATE TABLE shop_baseline_probe (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    UNIQUE KEY uk_shop_baseline_probe (tenant_id,code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
