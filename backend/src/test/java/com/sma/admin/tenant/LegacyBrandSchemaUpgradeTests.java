package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Uses the retained P10 tenant-V3 upgrade fixture, never cleans or repairs it. */
@EnabledIfEnvironmentVariable(named="SMA_P10_BRAND_LEGACY_UPGRADE_DB",matches=".+")
class LegacyBrandSchemaUpgradeTests {
    @Test void upgradesRetainedTenantV3AndConfigV4WithoutRewritingExistingBrandOrParameterData() throws Exception {
        String url=System.getenv("SMA_P10_BRAND_LEGACY_UPGRADE_DB");
        if(!url.startsWith("jdbc:mysql://") || !URI.create(url.substring(5)).getPath().matches("/sma_test_p10_[a-z0-9_]+"))
            throw new IllegalArgumentException("A dedicated P10 upgrade schema is required");
        String user=System.getenv().getOrDefault("SMA_TEST_DB_USERNAME","springboot_modulith_admin"),password=System.getenv().getOrDefault("SMA_TEST_DB_PASSWORD","sma_dev");
        String original;
        try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()) {
            try(var rows=statement.executeQuery("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history_tenant WHERE success=1")) {
                assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isEqualTo(3);
            }
            try(var rows=statement.executeQuery("SELECT draft_json,draft_version FROM tenant_brand_head WHERE id=71001")) {
                assertThat(rows.next()).isTrue();original=rows.getString(1);assertThat(rows.getLong(2)).isEqualTo(7);
            }
        }
        var oldConfig=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration/config")
                .table("flyway_schema_history_config").baselineOnMigrate(true).baselineVersion("0").target("4").load();
        assertThat(oldConfig.migrate().migrationsExecuted).isEqualTo(4);
        try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO config_parameter(id,tenant_id,param_key,group_key,name,value_type,param_value,builtin,public_readable,secret,sort,created_at,created_by,updated_at,updated_by,deleted)
                VALUES(72001,71000,'login.backgroundUrl','login','Legacy background','STRING','/images/customer-old.png',1,1,0,0,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)
                """);
        }
        var nextConfig=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration/config").table("flyway_schema_history_config").target("5").load();
        var nextTenant=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration/tenant").table("flyway_schema_history_tenant").target("4").load();
        assertThat(nextConfig.migrate().migrationsExecuted).isEqualTo(1);assertThat(nextTenant.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(nextConfig.migrate().migrationsExecuted).isZero();assertThat(nextTenant.migrate().migrationsExecuted).isZero();
        try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()) {
            try(var rows=statement.executeQuery("SELECT draft_json,draft_version,published_revision_id,legacy_background_url FROM tenant_brand_head WHERE id=71001")) {
                assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo(original);assertThat(rows.getLong(2)).isEqualTo(7);
                assertThat(rows.getObject(3)).isNull();assertThat(rows.getObject(4)).isNull();
            }
            try(var rows=statement.executeQuery("SELECT param_value,public_readable,secret,brand_archived FROM config_parameter WHERE id=72001")) {
                assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("/images/customer-old.png");
                assertThat(rows.getBoolean(2)).isTrue();assertThat(rows.getBoolean(3)).isFalse();assertThat(rows.getBoolean(4)).isFalse();
            }
        }
    }
}
