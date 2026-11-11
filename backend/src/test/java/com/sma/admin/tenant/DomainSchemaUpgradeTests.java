package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Dedicated empty schema only: build the delivered V4 shape, retain real rows, and append V5 without clean/repair. */
@EnabledIfEnvironmentVariable(named="SMA_P10_DOMAIN_UPGRADE_DB",matches=".+")
class DomainSchemaUpgradeTests {
    @Test void v5RetainsExistingBrandAndDomainsAndDoesNotInventTlsEvidence() throws Exception {
        String url=System.getenv("SMA_P10_DOMAIN_UPGRADE_DB");
        if(!url.startsWith("jdbc:mysql://") || !URI.create(url.substring(5)).getPath().matches("/sma_test_p10_domain_upgrade_[a-z0-9_]+"))
            throw new IllegalArgumentException("Domain upgrade requires its dedicated schema");
        String user=System.getenv().getOrDefault("SMA_TEST_DB_USERNAME","springboot_modulith_admin"),password=System.getenv().getOrDefault("SMA_TEST_DB_PASSWORD","sma_dev");
        var previous=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration/tenant").table("flyway_schema_history_tenant").target("4").load();
        assertThat(previous.migrate().migrationsExecuted).isEqualTo(4);
        try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO tenant_domain(id,owner_tenant_id,hostname,portal,status,primary_domain,verification_status,tls_status,provenance,created_at,created_by,updated_at,updated_by,deleted)
                VALUES (81001,1,'localhost','admin','ACTIVE',0,'VERIFIED','LOCAL','DEPLOYMENT',UTC_TIMESTAMP(3),1,UTC_TIMESTAMP(3),1,0),
                       (81002,2,'old.example.test','admin','PENDING',0,'PENDING','PENDING','REQUESTED',UTC_TIMESTAMP(3),1,UTC_TIMESTAMP(3),1,0)
                """);
            statement.executeUpdate("""
                INSERT INTO tenant_brand_head(id,tenant_id,portal,draft_json,draft_version,published_revision_id,next_revision,created_at,created_by,updated_at,updated_by,deleted)
                VALUES (81003,2,'admin','{"identity":{"systemName":"Retained before domain upgrade"}}',7,NULL,1,UTC_TIMESTAMP(3),1,UTC_TIMESTAMP(3),1,0)
                """);
        }
        var next=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration/tenant").table("flyway_schema_history_tenant").target("5").load();
        assertThat(next.migrate().migrationsExecuted).isEqualTo(1);assertThat(next.migrate().migrationsExecuted).isZero();
        try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()) {
            try(var rows=statement.executeQuery("SELECT dns_status,configuration_version,challenge_token,tls_valid_until,status FROM tenant_domain ORDER BY id")) {
                assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("LOCAL");assertThat(rows.getLong(2)).isEqualTo(1);assertThat(rows.getObject(3)).isNull();assertThat(rows.getObject(4)).isNull();assertThat(rows.getString(5)).isEqualTo("ACTIVE");
                assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("PENDING");assertThat(rows.getObject(4)).isNull();assertThat(rows.getString(5)).isEqualTo("PENDING");
            }
            try(var rows=statement.executeQuery("SELECT draft_json,draft_version FROM tenant_brand_head WHERE id=81003")) {
                assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).contains("Retained before domain upgrade");assertThat(rows.getLong(2)).isEqualTo(7);
            }
            try(var rows=statement.executeQuery("SELECT COUNT(*) FROM tenant_domain_check")) {assertThat(rows.next()).isTrue();assertThat(rows.getLong(1)).isZero();}
        }
    }
}
