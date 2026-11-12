package com.sma.admin.tenant.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sma.admin.common.deployment.DeploymentNodes;
import com.sma.admin.common.request.BusinessLedger;
import com.sma.admin.common.security.DataAccess;
import com.sma.admin.support.Users;
import com.sma.admin.tenant.internal.persistence.StorageBindingEntity;
import com.sma.admin.tenant.internal.persistence.StorageResourceEntity;
import com.sma.admin.tenant.storage.StorageMigrationEvidence;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.time.Instant;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/** The V8 gate and peer evidence are exercised against a disposable platform MySQL schema. */
@EnabledIfEnvironmentVariable(named = "SMA_TEST_DB_URL", matches = "jdbc:mysql://[^/]+/sma_test_p11_x1b_platform_20261112(\\?.*)?")
class StorageSwitchMysqlTests {
    @Test
    @EnabledIfEnvironmentVariable(named = "SMA_P10_SECOND_BUSINESS_DB_URL", matches = ".+")
    void physicalCopyProofUsesTwoActualBusinessDatabases() throws Exception {
        var sourceUrl = System.getenv("SMA_P11_BUSINESS_DB_URL");
        var targetUrl = System.getenv("SMA_P10_SECOND_BUSINESS_DB_URL");
        if (sourceUrl == null || !URI.create(sourceUrl.substring(5)).getPath()
                .equals("/sma_test_p11_x1b_business_20261112")
                || targetUrl == null || !URI.create(targetUrl.substring(5)).getPath()
                .equals("/sma_test_p11_x1a_20261112"))
            throw new IllegalArgumentException("S7c requires exact disposable source and target business schemas");
        try (var sourcePool = pool(sourceUrl); var targetPool = pool(targetUrl)) {
            var source = new JdbcTemplate(sourcePool); var target = new JdbcTemplate(targetPool);
            for (var db : List.of(source, target)) {
                db.execute("CREATE TABLE IF NOT EXISTS s7c_copy_probe (id BIGINT PRIMARY KEY, value_text VARCHAR(64) NOT NULL, outbox_id BIGINT NOT NULL)");
                db.update("DELETE FROM s7c_copy_probe WHERE id BETWEEN 770001 AND 770099");
            }
            source.update("INSERT INTO s7c_copy_probe VALUES (770001,'before-stop',4)");
            assertThatThrownBy(() -> physical(source, target))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("copy differs");
            target.update("INSERT INTO s7c_copy_probe VALUES (770001,'before-stop',4)");
            var initial = physical(source, target);
            assertThat(initial.sourceRows()).isEqualTo(1);
            source.update("INSERT INTO s7c_copy_probe VALUES (770002,'final-sync',5)");
            assertThatThrownBy(() -> physical(source, target))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("copy differs");
            target.update("INSERT INTO s7c_copy_probe VALUES (770002,'final-sync',5)");
            var finalCopy = physical(source, target);
            assertThat(finalCopy.sourceRows()).isEqualTo(2);
            assertThat(finalCopy.sourceOutbox()).isEqualTo(5);
            target.update("UPDATE s7c_copy_probe SET value_text='corrupt' WHERE id=770002");
            assertThatThrownBy(() -> physical(source, target))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("copy differs");
        }
    }

    @Test void durableAdmissionStopAndPeerProof() {
        String url = System.getenv("SMA_TEST_DB_URL");
        if (url == null || !url.startsWith("jdbc:mysql://")
                || !URI.create(url.substring(5)).getPath().equals("/sma_test_p11_x1b_platform_20261112"))
            throw new IllegalArgumentException("S7c requires its exact disposable platform test schema");
        try (var pool = pool(url)) {
            var flyway = Flyway.configure().dataSource(pool).locations("classpath:db/migration/tenant")
                    .table("flyway_schema_history_tenant").baselineVersion("5").cleanDisabled(false).load();
            flyway.clean(); flyway.baseline(); flyway.migrate(); flyway.validate();
            var sql = new JdbcTemplate(pool);
            sql.update("INSERT INTO tenant_storage_binding (id,owner_tenant_id,storage_group,resource_id,"
                    + "resource_revision,schema_compatibility,binding_version,state,created_at,updated_at) "
                    + "VALUES (1001,11,'shop','source',1,'v1',2,'ACTIVE',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))");
            sql.update("INSERT INTO tenant_storage_resource (id,resource_id,revision,display_name,db_host,db_port,"
                    + "database_name,tls_mode,secret_ref,pool_max,connect_timeout_millis,state,created_at,updated_at) "
                    + "VALUES (1002,'target',1,'Target','127.0.0.1',3306,'sma_test_p11_x1b_business_20261112',"
                    + "'DISABLED','secret/test',2,1000,'REGISTERED',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))");
            sql.update("INSERT INTO tenant_storage_resource (id,resource_id,revision,display_name,db_host,db_port,"
                    + "database_name,tls_mode,secret_ref,pool_max,connect_timeout_millis,state,created_at,updated_at) "
                    + "VALUES (1003,'source',1,'Source','127.0.0.1',3306,'sma_test_p11_x1b_business_20261112',"
                    + "'DISABLED','secret/test',2,1000,'REGISTERED',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))");
            var binding = new StorageBindingEntity();
            binding.setId(1001L); binding.setOwnerTenantId(11L); binding.setStorageGroup("shop");
            binding.setResourceId("source"); binding.setResourceRevision(1L);
            binding.setBindingVersion(2L); binding.setSchemaCompatibility("v1"); binding.setState("ACTIVE");
            var resource = new StorageResourceEntity(); resource.setState("REGISTERED");
            var directory = mock(StorageDirectory.class);
            when(directory.bindingForSwitch(1001L)).thenAnswer(call -> {
                var row = sql.queryForMap("SELECT state,resource_id,resource_revision,binding_version "
                        + "FROM tenant_storage_binding WHERE id=1001");
                binding.setState((String) row.get("state"));
                binding.setResourceId((String) row.get("resource_id"));
                binding.setResourceRevision(((Number) row.get("resource_revision")).longValue());
                binding.setBindingVersion(((Number) row.get("binding_version")).longValue());
                return binding;
            });
            when(directory.resource("target", 1)).thenReturn(resource);
            when(directory.resource("source", 1)).thenReturn(resource);
            var peerA = new DeploymentNodes.Member("a", "11111111-1111-1111-1111-111111111111", 1,
                    Instant.now().plusSeconds(30), "ONLINE");
            var peerB = new DeploymentNodes.Member("b", "22222222-2222-2222-2222-222222222222", 1,
                    Instant.now().plusSeconds(30), "ONLINE");
            var nodes = mock(DeploymentNodes.class);
            when(nodes.local()).thenReturn(peerA);
            when(nodes.snapshot()).thenAnswer(call -> new DeploymentNodes.Snapshot("a".repeat(64), 1,
                    Instant.now(), List.of(peerA, peerB)));
            var tracker = new StorageSessionTracker();
            var peerNodes = mock(DeploymentNodes.class);
            when(peerNodes.local()).thenReturn(peerB);
            var platform = new TenantPlatformProperties(1L, Set.of(997123L), Set.of());
            var operators = new PlatformOperators(platform);
            var state = transactional(new StorageSwitchState(sql, directory, operators, nodes, tracker, platform), pool);
            var peerState = transactional(new StorageSwitchState(sql, directory, operators, peerNodes,
                    new StorageSessionTracker(), platform), pool);
            var leases = transactional(new StorageSessionLeaseStore(sql), pool);
            var user = Users.principal(997123L, 1L, DataAccess.none());

            var localLease = leases.register(binding, peerA);
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM tenant_storage_session_lease", Long.class)).isEqualTo(1);
            var task = Users.as(user, () -> state.begin(1001, 2, "target", 1, "s7c-switch-000001"));
            assertThat(task.state()).isEqualTo("PREPARING");
            assertThat(Users.as(user, () -> state.begin(1001, 2, "target", 1, "s7c-switch-000001")).id())
                    .isEqualTo(task.id());
            var proof = new StorageMigrationEvidence.Snapshot("backup/s7c-000001", "b".repeat(64),
                    "b".repeat(64), 8, 8, 4, 4,
                    Instant.now());
            Users.as(user, () -> state.copied(task.id(), proof));
            Users.as(user, () -> state.stop(task.id()));
            assertThat(sql.queryForObject("SELECT state FROM tenant_storage_binding WHERE id=1001", String.class))
                    .isEqualTo("DRAINING");
            assertThatThrownBy(() -> leases.register(binding, peerA)).isInstanceOf(IllegalStateException.class);
            state.acknowledgeLocal(task.id());
            assertThat(sql.queryForObject("SELECT drained_at FROM tenant_storage_switch_node WHERE switch_id=? "
                    + "AND node_id='a'", java.sql.Timestamp.class, task.id())).isNull();
            leases.release(localLease);
            state.acknowledgeLocal(task.id());
            assertThat(sql.queryForObject("SELECT drained_at FROM tenant_storage_switch_node WHERE switch_id=? "
                    + "AND node_id='a'", java.sql.Timestamp.class, task.id())).isNotNull();
            assertThatThrownBy(() -> Users.as(user, () -> state.ready(task.id(), proof)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("drain proof");
            peerState.acknowledgeLocal(task.id());
            assertThatThrownBy(() -> Users.as(user, () -> state.ready(task.id(), proof)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("predates");
            var finalProof = new StorageMigrationEvidence.Snapshot("backup/s7c-000001", "c".repeat(64),
                    "c".repeat(64), 8, 8, 5, 5,
                    Instant.now());
            assertThat(Users.as(user, () -> state.ready(task.id(), finalProof)).state()).isEqualTo("READY");
            assertThat(sql.queryForObject("SELECT state FROM tenant_storage_binding WHERE id=1001", String.class))
                    .isEqualTo("DRAINING");
            assertThatThrownBy(() -> Users.as(user, () -> state.commit(task.id(), proof)))
                    .isInstanceOf(IllegalStateException.class);
            var commitProof = new StorageMigrationEvidence.Snapshot("backup/s7c-000001", "c".repeat(64),
                    "c".repeat(64), 8, 8, 5, 5, Instant.now());
            doThrow(new IllegalStateException("replaced process")).when(nodes).requireCurrent(any());
            assertThatThrownBy(() -> Users.as(user, () -> state.commit(task.id(), commitProof)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("replaced process");
            doNothing().when(nodes).requireCurrent(any());
            assertThat(sql.queryForObject("SELECT state FROM tenant_storage_binding WHERE id=1001", String.class))
                    .isEqualTo("DRAINING");
            assertThat(Users.as(user, () -> state.commit(task.id(), commitProof)).state()).isEqualTo("SWITCHED");
            assertThat(sql.queryForObject("SELECT resource_id FROM tenant_storage_binding WHERE id=1001", String.class))
                    .isEqualTo("target");
            assertThat(sql.queryForObject("SELECT binding_version FROM tenant_storage_binding WHERE id=1001", Long.class))
                    .isEqualTo(3);
            assertThatThrownBy(() -> Users.as(user, () -> state.abort(task.id())))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("reverse migration");
            var reverse = Users.as(user, () -> state.begin(1001, 3, "source", 1, "s7c-reverse-000001"));
            Users.as(user, () -> state.copied(reverse.id(), proof));
            Users.as(user, () -> state.stop(reverse.id()));
            assertThat(Users.as(user, () -> state.abort(reverse.id())).state()).isEqualTo("ABORTED");
            assertThat(sql.queryForObject("SELECT resource_id FROM tenant_storage_binding WHERE id=1001", String.class))
                    .isEqualTo("target");
            assertThat(sql.queryForObject("SELECT binding_version FROM tenant_storage_binding WHERE id=1001", Long.class))
                    .isEqualTo(4);
        }
    }

    private static HikariDataSource pool(String url) {
        var config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(System.getenv().getOrDefault("SMA_TEST_DB_USERNAME", "springboot_modulith_admin"));
        config.setPassword(System.getenv().getOrDefault("SMA_TEST_DB_PASSWORD", "sma_dev"));
        config.setMaximumPoolSize(3); config.setMinimumIdle(0);
        return new HikariDataSource(config);
    }

    private static StorageMigrationEvidence.Snapshot physical(JdbcTemplate source, JdbcTemplate target) {
        var rowsA = source.queryForList("SELECT id,value_text,outbox_id FROM s7c_copy_probe "
                + "WHERE id BETWEEN 770001 AND 770099 ORDER BY id");
        var rowsB = target.queryForList("SELECT id,value_text,outbox_id FROM s7c_copy_probe "
                + "WHERE id BETWEEN 770001 AND 770099 ORDER BY id");
        var a = digest(rowsA); var b = digest(rowsB);
        if (!a.equals(b)) throw new IllegalStateException("Physical copy differs");
        var outbox = rowsA.stream().mapToLong(row -> ((Number) row.get("outbox_id")).longValue()).max().orElse(0);
        return new StorageMigrationEvidence.Snapshot("backup/test-s7c-copy", a, b, rowsA.size(), rowsB.size(),
                outbox, outbox, Instant.now());
    }

    private static String digest(List<java.util.Map<String, Object>> rows) {
        try {
            var sha = MessageDigest.getInstance("SHA-256");
            for (var row : rows)
                sha.update((row.get("id") + ":" + row.get("value_text") + ":" + row.get("outbox_id") + "\n")
                        .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sha.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static <T> T transactional(T service, HikariDataSource pool) {
        var proxy = new ProxyFactory(service);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(pool),
                new AnnotationTransactionAttributeSource()));
        @SuppressWarnings("unchecked") T result = (T) proxy.getProxy();
        return result;
    }
}
