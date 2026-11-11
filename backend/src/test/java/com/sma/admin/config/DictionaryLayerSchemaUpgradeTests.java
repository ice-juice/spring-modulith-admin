package com.sma.admin.config;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Advances the retained Config V9 fixture to V10 once; legacy dictionaries are kept for reference and rollback review. */
@EnabledIfEnvironmentVariable(named="SMA_P10_PARAMETER_UPGRADE_DB",matches=".+")
class DictionaryLayerSchemaUpgradeTests {
    @Test void v10AddsTenantLayersWithoutRewritingLegacyDictionaryRowsOrVersions() throws Exception {
        String url=System.getenv("SMA_P10_PARAMETER_UPGRADE_DB");
        if(!url.startsWith("jdbc:mysql://")||!URI.create(url.substring(5)).getPath().matches("/sma_test_p10_parameter_upgrade_[a-z0-9_]+"))throw new IllegalArgumentException("Dedicated retained Config V9 schema required");
        String user=System.getenv().getOrDefault("SMA_TEST_DB_USERNAME","springboot_modulith_admin"),password=System.getenv().getOrDefault("SMA_TEST_DB_PASSWORD","sma_dev");
        try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()){
            sql.executeUpdate("""
                INSERT INTO config_dictionary_type(id,tenant_id,type_key,name,builtin,enabled,tree,sort,created_at,created_by,updated_at,updated_by,deleted)
                VALUES (94001,81,'sex','Retained sex',1,1,0,3,UTC_TIMESTAMP(3),1,UTC_TIMESTAMP(3),1,0) ON DUPLICATE KEY UPDATE id=id
                """);
            sql.executeUpdate("""
                INSERT INTO config_dictionary_item(id,tenant_id,type_id,parent_id,item_value,name,builtin,enabled,sort,created_at,created_by,updated_at,updated_by,deleted)
                VALUES (94002,81,94001,0,'1','Retained male',1,0,4,UTC_TIMESTAMP(3),1,UTC_TIMESTAMP(3),1,0) ON DUPLICATE KEY UPDATE id=id
                """);
        }
        var upgrade=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration/config").table("flyway_schema_history_config").target("10").load();
        assertThat(upgrade.info().current().getVersion().toString()).isEqualTo("9");
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);assertThat(upgrade.migrate().migrationsExecuted).isZero();
        try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()){
            try(var row=sql.executeQuery("SELECT parameter_revision,dictionary_revision FROM config_write_lock WHERE tenant_id=81")){
                assertThat(row.next()).isTrue();assertThat(row.getLong(1)).isEqualTo(8);assertThat(row.getLong(2)).isZero();
            }
            try(var row=sql.executeQuery("SELECT name,enabled,sort FROM config_dictionary_item WHERE id=94002")){
                assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("Retained male");assertThat(row.getBoolean(2)).isFalse();assertThat(row.getInt(3)).isEqualTo(4);
            }
            try(var row=sql.executeQuery("SELECT (SELECT COUNT(*) FROM config_dictionary_layer)+(SELECT COUNT(*) FROM config_dictionary_entry)")){assertThat(row.next()).isTrue();assertThat(row.getLong(1)).isZero();}
            try(var row=sql.executeQuery("SELECT COLLATION_NAME FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='config_dictionary_entry' AND column_name='code'")){
                assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("utf8mb4_0900_bin");
            }
        }
    }
}

