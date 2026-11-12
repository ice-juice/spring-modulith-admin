package com.sma.admin.tenant.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sma.admin.common.security.DataAccess;
import com.sma.admin.support.Users;
import com.sma.admin.tenant.internal.persistence.StorageResourceEntity;
import com.sma.admin.tenant.storage.StorageCredentialResolver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

/** Rebuilds only the exact disposable X1b business schema. */
@EnabledIfEnvironmentVariable(named = "SMA_P11_BUSINESS_DB_URL", matches = ".+")
class StorageMigrationMysqlTests {
    @SuppressWarnings("unchecked")
    @Test void sharedPhysicalSchemaMigratesOnceAndAnExistingTableSurvives() throws Exception {
        String url = System.getenv("SMA_P11_BUSINESS_DB_URL");
        if (url == null || !url.startsWith("jdbc:mysql://")
                || !URI.create(url.substring(5)).getPath().equals("/sma_test_p11_x1b_business_20261112"))
            throw new IllegalArgumentException("S7a requires its exact disposable business test schema");
        var configuration = new HikariConfig();
        configuration.setJdbcUrl(url);
        configuration.setUsername(System.getenv().getOrDefault("SMA_TEST_DB_USERNAME", "springboot_modulith_admin"));
        configuration.setPassword(System.getenv().getOrDefault("SMA_TEST_DB_PASSWORD", "sma_dev"));
        configuration.setMaximumPoolSize(3);
        configuration.setMinimumIdle(0);
        try (var pool = new HikariDataSource(configuration)) {
            Flyway.configure().dataSource(pool).locations("classpath:db/business-ledger")
                    .table("flyway_schema_history_business_ledger").cleanDisabled(false).load().clean();
            var sql = new JdbcTemplate(pool);
            sql.execute("CREATE TABLE shop_existing (id BIGINT PRIMARY KEY) ENGINE=InnoDB");
            sql.update("INSERT INTO shop_existing(id) VALUES (1)");
            var row = new StorageResourceEntity();
            row.setResourceId("s7a-shared"); row.setRevision(1L); row.setState("REGISTERED");
            row.setDbHost("127.0.0.1"); row.setDbPort(URI.create(url.substring(5)).getPort());
            row.setDatabaseName("sma_test_p11_x1b_business_20261112"); row.setTlsMode("DISABLED");
            row.setSecretRef("secret/s7a"); row.setPoolMax(2); row.setConnectTimeoutMillis(1000);
            var directory = mock(StorageDirectory.class);
            when(directory.resource("s7a-shared", 1)).thenReturn(row);
            var credentials = (ObjectProvider<StorageCredentialResolver>)mock(ObjectProvider.class);
            when(credentials.getIfAvailable()).thenReturn(ref -> new StorageCredentialResolver.Credentials(
                    System.getenv().getOrDefault("SMA_TEST_DB_USERNAME", "springboot_modulith_admin"),
                    System.getenv().getOrDefault("SMA_TEST_DB_PASSWORD", "sma_dev")));
            var migration = new StorageMigrationService(directory,
                    new PlatformOperators(new TenantPlatformProperties(1L, Set.of(997123L), Set.of())), credentials);
            var actor = Users.principal(997123L, 1L, DataAccess.none());
            assertThatThrownBy(()->Users.as(actor,()->{migration.migrate("s7a-shared",1,"shop");return null;}))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("no declared schema");
            Users.as(actor, () -> { migration.migrate("s7a-shared", 1); migration.migrate("s7a-shared", 1); return null; });
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM shop_existing", Long.class)).isEqualTo(1);
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM flyway_schema_history_business_ledger WHERE version='1' AND success=1", Long.class)).isEqualTo(1);
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM common_business_outbox", Long.class)).isZero();
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='tenant_storage_binding'", Long.class)).isZero();
            try (var lock = pool.getConnection(); var acquire = lock.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                acquire.setString(1, StorageMigrationService.lockKey(row));
                try (var result = acquire.executeQuery()) { assertThat(result.next()).isTrue(); assertThat(result.getInt(1)).isEqualTo(1); }
                assertThatThrownBy(() -> Users.as(actor, () -> { migration.migrate("s7a-shared", 1); return null; }))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("already running");
                try (var release = lock.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                    release.setString(1, StorageMigrationService.lockKey(row));
                    release.executeQuery().close();
                }
            }
            Users.as(actor, () -> { migration.migrate("s7a-shared", 1); return null; });
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM flyway_schema_history_business_ledger WHERE version='1' AND success=1", Long.class)).isEqualTo(1);
            var schemas=mock(BusinessSchemaDirectory.class);
            when(schemas.require("shop")).thenReturn(java.util.List.of(new BusinessSchemaDirectory.Schema("shop","shop")));
            var businessMigration=new StorageMigrationService(directory,new PlatformOperators(new TenantPlatformProperties(1L,Set.of(997123L),Set.of())),credentials,schemas);
            Users.as(actor,()->{businessMigration.migrate("s7a-shared",1,"shop");return null;});
            sql.update("INSERT INTO shop_baseline_probe(id,tenant_id,code) VALUES(1,11,'kept')");
            Users.as(actor,()->{businessMigration.migrate("s7a-shared",1,"shop");return null;});
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM shop_baseline_probe WHERE code='kept'",Long.class)).isEqualTo(1);
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM flyway_schema_history_business_shop WHERE version='1' AND success=1",Long.class)).isEqualTo(1);
            when(schemas.require("missing")).thenReturn(java.util.List.of(new BusinessSchemaDirectory.Schema("missing","missing")));
            assertThatThrownBy(()->Users.as(actor,()->{businessMigration.migrate("s7a-shared",1,"missing");return null;})).isInstanceOf(RuntimeException.class);
            Flyway.configure().dataSource(pool).locations("classpath:db/business-ledger")
                    .table("flyway_schema_history_business_ledger").cleanDisabled(false).load().clean();
            Users.as(actor, () -> { migration.migrate("s7a-shared", 1); return null; });
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM flyway_schema_history_business_ledger WHERE version='1' AND success=1", Long.class)).isEqualTo(1);
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='shop_existing'", Long.class)).isZero();
            sql.execute("DROP TABLE common_business_outbox");
            assertThatThrownBy(() -> Users.as(actor, () -> { migration.migrate("s7a-shared", 1); return null; }))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("schema is incomplete");
            var disposable = Flyway.configure().dataSource(pool).locations("classpath:db/business-ledger")
                    .table("flyway_schema_history_business_ledger").cleanDisabled(false).load();
            disposable.clean(); disposable.migrate(); disposable.validate();
            assertThatThrownBy(() -> Users.as(Users.principal(997124L, 2L, DataAccess.none()),
                    () -> { migration.migrate("s7a-shared", 1); return null; })).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }
    }
}
