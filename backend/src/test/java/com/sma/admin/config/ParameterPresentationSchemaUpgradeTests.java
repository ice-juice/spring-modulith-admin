package com.sma.admin.config;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Advances the retained S5c-1 Config V7 fixture; never cleans a database or changes an applied script. */
@EnabledIfEnvironmentVariable(named="SMA_P10_PARAMETER_UPGRADE_DB",matches=".+")
class ParameterPresentationSchemaUpgradeTests {
    @Test void v8KeepsExistingPresentationExplicitAndDoesNotChangeValueOrRevision() throws Exception {
        String url=System.getenv("SMA_P10_PARAMETER_UPGRADE_DB");
        if(!url.startsWith("jdbc:mysql://")||!URI.create(url.substring(5)).getPath().matches("/sma_test_p10_parameter_upgrade_[a-z0-9_]+"))throw new IllegalArgumentException("Dedicated retained Config V7 schema required");
        String user=System.getenv().getOrDefault("SMA_TEST_DB_USERNAME","springboot_modulith_admin"),password=System.getenv().getOrDefault("SMA_TEST_DB_PASSWORD","sma_dev");
        var upgrade=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration/config").table("flyway_schema_history_config").target("8").load();
        assertThat(upgrade.info().current().getVersion().toString()).isEqualTo("7");
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);assertThat(upgrade.migrate().migrationsExecuted).isZero();
        try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()) {
            try(var row=sql.executeQuery("SELECT name,param_value,secret,presentation_overridden,value_overridden FROM config_parameter WHERE id=91001")) {
                assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("Retained");assertThat(row.getString(2)).isNull();
                assertThat(row.getBoolean(3)).isTrue();assertThat(row.getBoolean(4)).isTrue();assertThat(row.getBoolean(5)).isTrue();
            }
            try(var row=sql.executeQuery("SELECT text FROM config_i18n_text WHERE entity_id=91001")){assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("Retained translation");}
            try(var row=sql.executeQuery("SELECT parameter_revision FROM config_write_lock WHERE tenant_id=81")){assertThat(row.next()).isTrue();assertThat(row.getLong(1)).isEqualTo(8);}
        }
    }
}
