package com.sma.admin.config;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Retains the legacy parameter ID/value/secret/translation while expanding Config V6 to V7. */
@EnabledIfEnvironmentVariable(named="SMA_P10_PARAMETER_UPGRADE_DB",matches=".+")
class ParameterInheritanceSchemaUpgradeTests {
    @Test void v7RetainsLegacyOverridesAndAllowsExplicitNullWithoutResettingTenantVersion() throws Exception {
        String url=System.getenv("SMA_P10_PARAMETER_UPGRADE_DB");
        if(!url.startsWith("jdbc:mysql://") || !URI.create(url.substring(5)).getPath().matches("/sma_test_p10_parameter_upgrade_[a-z0-9_]+")) throw new IllegalArgumentException("Dedicated empty parameter upgrade schema required");
        String user=System.getenv().getOrDefault("SMA_TEST_DB_USERNAME","springboot_modulith_admin"),password=System.getenv().getOrDefault("SMA_TEST_DB_PASSWORD","sma_dev");
        var before=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration/config").table("flyway_schema_history_config").target("6").load();
        assertThat(before.migrate().migrationsExecuted).isEqualTo(6);
        try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()) {
            sql.executeUpdate("""
                INSERT INTO config_parameter(id,tenant_id,param_key,group_key,name,value_type,param_value,builtin,public_readable,secret,sort,created_at,created_by,updated_at,updated_by,deleted)
                VALUES (91001,81,'customer.private','basic','Retained','STRING','retained-private-value',0,0,1,7,UTC_TIMESTAMP(3),1,UTC_TIMESTAMP(3),1,0)
                """);
            sql.executeUpdate("""
                INSERT INTO config_i18n_text(id,tenant_id,entity_type,entity_id,locale,text,created_at,created_by,updated_at,updated_by,deleted)
                VALUES (91002,81,'parameter',91001,'en-US','Retained translation',UTC_TIMESTAMP(3),1,UTC_TIMESTAMP(3),1,0)
                """);
            sql.executeUpdate("""
                INSERT INTO config_write_lock(id,tenant_id,created_at,created_by,updated_at,updated_by,deleted)
                VALUES (81,81,UTC_TIMESTAMP(3),1,UTC_TIMESTAMP(3),1,0)
                """);
        }
        var after=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration/config").table("flyway_schema_history_config").target("7").load();
        assertThat(after.migrate().migrationsExecuted).isEqualTo(1); assertThat(after.migrate().migrationsExecuted).isZero();
        try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()) {
            try(var rows=sql.executeQuery("SELECT tenant_id,param_value,secret,sort,definition_contract,value_overridden FROM config_parameter WHERE id=91001")) {
                assertThat(rows.next()).isTrue();assertThat(rows.getLong(1)).isEqualTo(81);assertThat(rows.getString(2)).isEqualTo("retained-private-value");
                assertThat(rows.getBoolean(3)).isTrue();assertThat(rows.getInt(4)).isEqualTo(7);assertThat(rows.getString(5)).isNull();assertThat(rows.getBoolean(6)).isTrue();
            }
            try(var rows=sql.executeQuery("SELECT text FROM config_i18n_text WHERE entity_id=91001")) {assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("Retained translation");}
            try(var rows=sql.executeQuery("SELECT parameter_revision FROM config_write_lock WHERE tenant_id=81")) {assertThat(rows.next()).isTrue();assertThat(rows.getLong(1)).isZero();}
            sql.executeUpdate("UPDATE config_write_lock SET parameter_revision=8 WHERE tenant_id=81");
            sql.executeUpdate("UPDATE config_parameter SET param_value=NULL WHERE id=91001");
        }
        assertThat(after.migrate().migrationsExecuted).isZero();
        try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement();var rows=sql.executeQuery("SELECT parameter_revision FROM config_write_lock WHERE tenant_id=81")) {
            assertThat(rows.next()).isTrue();assertThat(rows.getLong(1)).isEqualTo(8);
        }
    }
}
