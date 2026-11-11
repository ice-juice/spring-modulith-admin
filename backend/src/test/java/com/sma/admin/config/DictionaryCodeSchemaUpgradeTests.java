package com.sma.admin.config;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Advances the retained Config V8 fixture to V9 once; existing customer parameters remain untouched. */
@EnabledIfEnvironmentVariable(named="SMA_P10_PARAMETER_UPGRADE_DB",matches=".+")
class DictionaryCodeSchemaUpgradeTests {
    @Test void v9AddsOnlyNamespaceMetadataWithoutRewritingExistingRows() throws Exception {
        String url=System.getenv("SMA_P10_PARAMETER_UPGRADE_DB");
        if(!url.startsWith("jdbc:mysql://")||!URI.create(url.substring(5)).getPath().matches("/sma_test_p10_parameter_upgrade_[a-z0-9_]+"))throw new IllegalArgumentException("Dedicated retained Config V8 schema required");
        String user=System.getenv().getOrDefault("SMA_TEST_DB_USERNAME","springboot_modulith_admin"),password=System.getenv().getOrDefault("SMA_TEST_DB_PASSWORD","sma_dev");
        try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()){
            sql.executeUpdate("""
                INSERT INTO config_shared_head(id,kind,config_key,owner_module,definition_json,contract_hash,source_fingerprint,draft_json,draft_label_json,
                  draft_version,published_revision_id,next_revision,history_bytes,created_at,created_by,updated_at,updated_by,deleted)
                VALUES (93001,'DICTIONARY','config_upgrade_probe','config','{}',REPEAT('a',64),REPEAT('b',64),'{}','{"text":"Probe"}',3,NULL,1,0,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)
                ON DUPLICATE KEY UPDATE id=id
                """);
        }
        var upgrade=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration/config").table("flyway_schema_history_config").target("9").load();
        assertThat(upgrade.info().current().getVersion().toString()).isEqualTo("8");
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);assertThat(upgrade.migrate().migrationsExecuted).isZero();
        try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()){
            try(var row=sql.executeQuery("SELECT draft_version,dictionary_code_count FROM config_shared_head WHERE id=93001")){
                assertThat(row.next()).isTrue();assertThat(row.getLong(1)).isEqualTo(3);assertThat(row.getLong(2)).isZero();
            }
            try(var row=sql.executeQuery("SELECT name,param_value,secret,presentation_overridden FROM config_parameter WHERE id=91001")){
                assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("Retained");assertThat(row.getString(2)).isNull();
                assertThat(row.getBoolean(3)).isTrue();assertThat(row.getBoolean(4)).isTrue();
            }
            try(var row=sql.executeQuery("SELECT COUNT(*) FROM config_dictionary_code")){assertThat(row.next()).isTrue();assertThat(row.getLong(1)).isZero();}
            try(var row=sql.executeQuery("SELECT parameter_revision FROM config_write_lock WHERE tenant_id=81")){assertThat(row.next()).isTrue();assertThat(row.getLong(1)).isEqualTo(8);}
        }
    }
}
