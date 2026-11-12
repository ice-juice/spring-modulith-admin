package com.sma.admin.common.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.sma.admin.common.internal.persistence.CacheRevisionMapper;
import com.sma.admin.common.internal.persistence.BusinessOutboxMapper;
import com.sma.admin.common.internal.persistence.RequestExecutionMapper;
import com.sma.admin.common.internal.persistence.RequestMutexMapper;
import com.sma.admin.common.internal.security.RequestGovernanceProperties;
import com.sma.admin.common.internal.service.BusinessLedgerResourceFactoryService;
import com.sma.admin.common.internal.service.BusinessLedgerService;
import com.sma.admin.common.request.*;
import com.sma.admin.common.persistence.DataSourceIdentity;
import com.sma.admin.common.internal.persistence.IdentifiedDataSource;
import com.sma.admin.common.security.DataAccess;
import com.sma.admin.support.Users;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

/** Platform schema A and task-owned business schema B; no other database is touched. */
@EnabledIfEnvironmentVariable(named = "SMA_P11_BUSINESS_DB_URL", matches = ".+")
class BusinessLedgerMysqlTests {
    private final PersistenceConfiguration persistence = new PersistenceConfiguration();
    private final RequestGovernanceProperties properties = new RequestGovernanceProperties(
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "v1", "", 100000L, 8);

    @Test void businessExecutionAndInvalidationUseOnlyTheBusinessDatabase() throws Exception {
        String url = requireBusinessUrl(System.getenv("SMA_P11_BUSINESS_DB_URL"));
        String platformUrl = requirePlatformUrl(System.getenv("SMA_TEST_DB_URL"));
        long tenant = java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000L);
        try (var platformPool = pool(platformUrl); var pool = pool(url)) {
            migrateFixture(platformPool, false);
            var platform = new IdentifiedDataSource(platformPool, new DataSourceIdentity("x1-platform", 1));
            var platformSql = new JdbcTemplate(platform);
            var platformTransactions = new DataSourceTransactionManager(platform);
            long before = platformSql.queryForObject("SELECT COUNT(*) FROM common_request_execution WHERE tenant_id=?", Long.class, tenant);
            var source = new IdentifiedDataSource(pool, new DataSourceIdentity("x1-business", 1));
            migrateFixture(pool, true);
            var businessSql = new JdbcTemplate(source);
            businessSql.execute("CREATE TABLE IF NOT EXISTS p11_business_fixture (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL)");
            businessSql.update("DELETE FROM p11_business_fixture WHERE tenant_id=?", tenant);
            var manager = new DataSourceTransactionManager(source);
            var factory = mapperFactory(source);
            var resource = new BusinessLedgerResourceFactoryService(properties).prepare("x1-business", 1, source, factory, manager);
            var releases = new AtomicInteger();
            @SuppressWarnings("unchecked") var providers = (ObjectProvider<BusinessLedgerProvider>) mock(ObjectProvider.class);
            when(providers.getIfAvailable()).thenReturn((id, group) -> {
                assertThat(id).isEqualTo(tenant); assertThat(group).isEqualTo("shop");
                return resource.open(releases::incrementAndGet);
            });
            var ledger = new BusinessLedgerService(providers);
            var actor = Users.principal(997123, tenant, DataAccess.none());
            Users.as(actor, () -> {
                try (var session = ledger.open("shop")) {
                    byte[] payload = "created".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    assertThat(session.authoritativeRevision("shop.items")).isZero();
                    var command = command("save", "business-key-00001");
                    var result = session.executeLocal(command, work(() -> {
                        businessSql.update("INSERT INTO p11_business_fixture(id,tenant_id) VALUES (?,?)", tenant * 10 + 1, tenant);
                        session.invalidate("shop.items");
                        session.enqueue("shop.order.created", "event-key-00000001", payload);
                        return "stored";
                    }));
                    assertThat(result.replayed()).isFalse();
                    assertThat(result.receipt().state()).isEqualTo(RequestReceipt.State.SUCCEEDED);
                    assertThat(session.authoritativeRevision("shop.items")).isEqualTo(1);
                    assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status -> session.authoritativeRevision("shop.items")))
                            .isInstanceOf(IllegalStateException.class).hasMessageContaining("outside a transaction");
                    assertThat(session.executeLocal(command, work(() -> fail("replay must not execute business work"))).replayed()).isTrue();
                    long stableId = new TransactionTemplate(manager).execute(status ->
                            session.enqueue("shop.order.created", "event-key-00000001", payload));
                    long repeatedId = new TransactionTemplate(manager).execute(status ->
                            session.enqueue("shop.order.created", "event-key-00000001", payload));
                    assertThat(repeatedId).isEqualTo(stableId);
                    assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status ->
                            session.enqueue("shop.order.created", "event-key-00000001", "changed".getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                            .isInstanceOf(IllegalStateException.class).hasMessageContaining("different content");
                    assertThatThrownBy(() -> new TransactionTemplate(platformTransactions).execute(status ->
                            session.enqueue("shop.order.created", "event-key-00000002", payload)))
                            .isInstanceOf(IllegalStateException.class).hasMessageContaining("this resource");
                    assertThatThrownBy(() -> new TransactionTemplate(platformTransactions).execute(status -> {
                        session.invalidate("shop.items"); return null;
                    })).isInstanceOf(IllegalStateException.class).hasMessageContaining("this resource");
                    assertThatThrownBy(() -> new TransactionTemplate(platformTransactions).execute(status ->
                            session.executeLocal(command("nested", "business-key-00002"), work(() -> "invalid"))))
                            .isInstanceOf(IllegalStateException.class).hasMessageContaining("outermost");
                    var failed = command("rollback", "business-key-00003");
                    assertThatThrownBy(() -> session.executeLocal(failed, work(() -> {
                        businessSql.update("INSERT INTO p11_business_fixture(id,tenant_id) VALUES (?,?)", tenant * 10 + 2, tenant);
                        session.invalidate("shop.items");
                        session.enqueue("shop.order.created", "event-key-00000003", payload);
                        throw new IllegalStateException("rollback fixture");
                    }))).isInstanceOf(IllegalStateException.class).hasMessageContaining("rollback fixture");
                    assertThat(session.receiptByKey(failed.key()).state()).isEqualTo(RequestReceipt.State.FAILED_FINAL);
                    assertThat(businessSql.queryForObject("SELECT COUNT(*) FROM common_business_outbox WHERE tenant_id=?", Long.class, tenant)).isEqualTo(1);
                    var instant = new AtomicReference<>(Instant.parse("2026-11-12T00:00:00Z"));
                    Clock clock = new Clock() {
                        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
                        @Override public Clock withZone(ZoneId zone) { return this; }
                        @Override public Instant instant() { return instant.get(); }
                    };
                    var rollup = new BusinessRevisionRollup(clock, Duration.ofSeconds(5), 8);
                    assertThat(rollup.observe("x1-business", 1, "shop.items").stale()).isTrue();
                    assertThat(rollup.sample("x1-business", 1, "shop.items", session).revision()).isEqualTo(1);
                    assertThat(rollup.observe("x1-business", 1, "shop.items").stale()).isFalse();
                    assertThat(rollup.observe("x1-business", 2, "shop.items").stale()).isTrue();
                    instant.set(instant.get().plusSeconds(6));
                    assertThat(rollup.observe("x1-business", 1, "shop.items").stale()).isTrue();
                    var unavailable = mock(BusinessLedger.Session.class);
                    when(unavailable.authoritativeRevision("shop.items")).thenThrow(new IllegalStateException("resource unavailable"));
                    assertThat(rollup.sample("x1-business", 1, "shop.items", unavailable).stale()).isTrue();
                    assertThat(rollup.sample("x1-business", 1, "shop.items", session).stale()).isFalse();
                }
                return null;
            });
            assertThat(releases.get()).isEqualTo(1);
            assertThat(businessSql.queryForObject("SELECT COUNT(*) FROM p11_business_fixture WHERE tenant_id=?", Long.class, tenant)).isEqualTo(1);
            assertThat(businessSql.queryForObject("SELECT COUNT(*) FROM common_request_execution WHERE tenant_id=? AND state='SUCCEEDED'", Long.class, tenant)).isEqualTo(1);
            assertThat(businessSql.queryForObject("SELECT revision FROM common_cache_revision WHERE tenant_id=? AND region='shop.items'", Long.class, tenant)).isEqualTo(1);
            assertThat(businessSql.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='common_request_budget'", Long.class)).isZero();
            assertThat(businessSql.queryForObject("SELECT COUNT(*) FROM flyway_schema_history_business_ledger WHERE success=1", Long.class)).isEqualTo(2);
            assertThat(platformSql.queryForObject("SELECT COUNT(*) FROM common_request_execution WHERE tenant_id=?", Long.class, tenant)).isEqualTo(before);
            assertThat(platformSql.queryForObject("SELECT COUNT(*) FROM common_cache_revision WHERE tenant_id=?", Long.class, tenant)).isZero();
            assertThat(platformSql.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='common_business_outbox'", Long.class)).isZero();
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            try (var workers = Executors.newFixedThreadPool(2)) {
                var first = workers.submit(() -> Users.as(actor, () -> {
                    try (var session = ledger.open("shop")) {
                        return session.executeLocal(command("concurrent", "business-key-00004"), work(() -> {
                            entered.countDown();
                            try {
                                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timed out");
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted);
                            }
                            return "first";
                        }));
                    }
                }));
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                var second = workers.submit(() -> Users.as(actor, () -> {
                    try (var session = ledger.open("shop")) {
                        return session.executeLocal(command("concurrent", "business-key-00005"), work(() -> "second"));
                    }
                }));
                try {
                    assertThatThrownBy(() -> second.get(5, TimeUnit.SECONDS))
                            .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(RequestRejected.class);
                } finally {
                    release.countDown();
                }
                assertThat(first.get(10, TimeUnit.SECONDS).result()).isEqualTo("first");
            }
        }
    }

    private static RequestCommand command(String operation, String key) {
        return new RequestCommand("shop." + operation, "shop.order", "order-1", key, operation.getBytes(java.nio.charset.StandardCharsets.UTF_8), "");
    }
    private static <T> RequestExecution.Work<T> work(java.util.function.Supplier<T> action) {
        return new RequestExecution.Work<>() {
            @Override public void authorize() {}
            @Override public T execute() { return action.get(); }
        };
    }
    private static String requireBusinessUrl(String value) {
        if (value == null || !value.startsWith("jdbc:mysql://")
                || !URI.create(value.substring(5)).getPath().equals("/sma_test_p11_x1b_business_20261112"))
            throw new IllegalArgumentException("X1c requires the dedicated, disposable X1b business test schema");
        return value;
    }
    private static String requirePlatformUrl(String value) {
        if (value == null || !value.startsWith("jdbc:mysql://")
                || !URI.create(value.substring(5)).getPath().matches("/sma_test_p11_x1b_platform_[a-z0-9_]+"))
            throw new IllegalArgumentException("X1b requires a dedicated platform test schema");
        return value;
    }
    private static HikariDataSource pool(String url) {
        var config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(System.getenv().getOrDefault("SMA_TEST_DB_USERNAME", "springboot_modulith_admin"));
        config.setPassword(System.getenv().getOrDefault("SMA_TEST_DB_PASSWORD", "sma_dev"));
        config.setMaximumPoolSize(3); config.setMinimumIdle(0);
        return new HikariDataSource(config);
    }
    private static void migrateFixture(DataSource source, boolean business) throws Exception {
        if (business) {
            var flyway = Flyway.configure().dataSource(source).locations("classpath:db/business-ledger").cleanDisabled(false)
                    .table("flyway_schema_history_business_ledger").load();
            flyway.clean();
            flyway.migrate();
            flyway.validate();
            return;
        }
        try (var connection = source.getConnection()) {
            boolean installed;
            try (var existing = connection.getMetaData().getTables(connection.getCatalog(), null, "common_request_execution", null)) {
                installed = existing.next();
            }
            boolean controlInstalled;
            try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "common_cache_revision", "control_version")) {
                controlInstalled = columns.next();
            }
            for (int version = controlInstalled ? 6 : installed ? 5 : 1; version <= 5; version++) {
                var name = switch (version) {
                    case 1 -> "V1__cache_revisions.sql";
                    case 2 -> "V2__request_governance.sql";
                    case 3 -> "V3__request_fingerprint_key_version.sql";
                    case 4 -> "V4__request_intent_key_scope.sql";
                    default -> "V5__cache_control_state.sql";
                };
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/common/" + name));
            }
        }
    }
    private SqlSessionFactory mapperFactory(DataSource source) throws Exception {
        var bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(source);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        bean.setConfiguration(configuration);
        bean.setGlobalConfig(new GlobalConfig().setMetaObjectHandler(persistence.auditFields()));
        bean.setPlugins(persistence.mybatisPlusInterceptor());
        bean.afterPropertiesSet();
        var factory = bean.getObject();
        factory.getConfiguration().addMapper(RequestExecutionMapper.class);
        factory.getConfiguration().addMapper(RequestMutexMapper.class);
        factory.getConfiguration().addMapper(CacheRevisionMapper.class);
        factory.getConfiguration().addMapper(BusinessOutboxMapper.class);
        return factory;
    }
}
