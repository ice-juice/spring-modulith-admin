package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Upgrades a dedicated schema from the already delivered tenant V2 without Flyway clean or history repair. */
@EnabledIfEnvironmentVariable(named="SMA_P10_BRAND_UPGRADE_DB",matches=".+")
class BrandMigrationTests {
    @Test void v3PreservesExistingV2DraftAndPublishedPointerAndIsRepeatable() throws Exception {
        String url=System.getenv("SMA_P10_BRAND_UPGRADE_DB");
        if(!url.startsWith("jdbc:mysql://") || !URI.create(url.substring(5)).getPath().matches("/sma_test_p10_[a-z0-9_]+"))
            throw new IllegalArgumentException("Brand migration test requires an isolated P10 schema");
        String user=System.getenv().getOrDefault("SMA_TEST_DB_USERNAME","springboot_modulith_admin"),password=System.getenv().getOrDefault("SMA_TEST_DB_PASSWORD","sma_dev");
        var base=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration/tenant").table("flyway_schema_history_tenant").target("2").load();
        assertThat(base.migrate().migrationsExecuted).isEqualTo(2);
        try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO tenant_brand_head (id,tenant_id,portal,draft_json,draft_version,published_revision_id,next_revision,created_at,created_by,updated_at,updated_by,deleted)
                VALUES (71001,71000,'admin','{"identity":{"systemName":"Existing brand"}}',7,NULL,1,UTC_TIMESTAMP(3),1,UTC_TIMESTAMP(3),1,0)
                """);
        }
        var next=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration/tenant").table("flyway_schema_history_tenant").target("3").load();
        assertThat(next.migrate().migrationsExecuted).isEqualTo(1);assertThat(next.migrate().migrationsExecuted).isZero();
        try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement();
                var rows=statement.executeQuery("SELECT draft_json,draft_version,published_revision_id FROM tenant_brand_head WHERE id=71001")) {
            assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).contains("Existing brand");
            assertThat(rows.getLong(2)).isEqualTo(7);assertThat(rows.getObject(3)).isNull();
        }
    }
}
