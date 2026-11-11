package com.sma.admin.config;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** A dedicated empty schema becomes Config V5 and then V6, with representative customer rows retained. */
@EnabledIfEnvironmentVariable(named="SMA_P10_SHARED_CONFIG_UPGRADE_DB", matches=".+")
class SharedConfigurationSchemaUpgradeTests {
    @Test void v6AddsOnlyPlatformTablesAndRetainsExistingPrivateValues() throws Exception {
        String url = System.getenv("SMA_P10_SHARED_CONFIG_UPGRADE_DB");
        if (!url.startsWith("jdbc:mysql://") || !URI.create(url.substring(5)).getPath().matches("/sma_test_p10_config_upgrade_[a-z0-9_]+")) throw new IllegalArgumentException("Shared configuration upgrade requires its dedicated schema");
        String user = System.getenv().getOrDefault("SMA_TEST_DB_USERNAME", "springboot_modulith_admin"), password = System.getenv().getOrDefault("SMA_TEST_DB_PASSWORD", "sma_dev");
        var previous = Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration/config").table("flyway_schema_history_config").target("5").load();
        assertThat(previous.migrate().migrationsExecuted).isEqualTo(5);
        try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
            sql.executeUpdate("""
                INSERT INTO config_parameter(id,tenant_id,param_key,group_key,name,value_type,param_value,builtin,public_readable,secret,sort,created_at,created_by,updated_at,updated_by,deleted)
                VALUES (91001,81,'customer.private','basic','Retained','STRING','retained-private-value',0,0,1,7,UTC_TIMESTAMP(3),1,UTC_TIMESTAMP(3),1,0)
                """);
        }
        var next = Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration/config").table("flyway_schema_history_config").target("6").load();
        assertThat(next.migrate().migrationsExecuted).isEqualTo(1); assertThat(next.migrate().migrationsExecuted).isZero();
        try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
            try (var rows = sql.executeQuery("SELECT tenant_id,param_value,secret,sort,brand_archived FROM config_parameter WHERE id=91001")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getLong(1)).isEqualTo(81); assertThat(rows.getString(2)).isEqualTo("retained-private-value"); assertThat(rows.getBoolean(3)).isTrue(); assertThat(rows.getInt(4)).isEqualTo(7); assertThat(rows.getBoolean(5)).isFalse();
            }
            try (var rows = sql.executeQuery("SELECT (SELECT COUNT(*) FROM config_shared_head)+(SELECT COUNT(*) FROM config_shared_revision)")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getLong(1)).isZero();
            }
        }
    }
}
