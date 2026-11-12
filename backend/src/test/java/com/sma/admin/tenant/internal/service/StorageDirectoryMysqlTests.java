package com.sma.admin.tenant.internal.service;

import static org.assertj.core.api.Assertions.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.sma.admin.common.security.DataAccess;
import com.sma.admin.common.BizException;
import com.sma.admin.tenant.TenantErrorCode;
import com.sma.admin.support.Users;
import com.sma.admin.tenant.internal.persistence.StorageBindingMapper;
import com.sma.admin.tenant.internal.persistence.StorageResourceMapper;
import com.sma.admin.tenant.internal.persistence.TenantRecordEntity;
import com.sma.admin.tenant.internal.persistence.TenantRecordMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import org.apache.ibatis.reflection.MetaObject;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Exact task-owned platform schema; the test may rebuild only this schema. */
@EnabledIfEnvironmentVariable(named = "SMA_TEST_DB_URL", matches = "jdbc:mysql://[^/]+/sma_test_p11_x1b_platform_20261112(\\?.*)?")
class StorageDirectoryMysqlTests {
    @Test void oldDirectoryUpgradesAndOnlyPlatformOperatorsCanRegisterPendingBindings() throws Exception {
        String url = System.getenv("SMA_TEST_DB_URL");
        if (url == null || !url.startsWith("jdbc:mysql://")
                || !URI.create(url.substring(5)).getPath().equals("/sma_test_p11_x1b_platform_20261112"))
            throw new IllegalArgumentException("S6a may clean only its exact dedicated platform test schema");
        var poolConfig = new HikariConfig();
        poolConfig.setJdbcUrl(url);
        poolConfig.setUsername(System.getenv().getOrDefault("SMA_TEST_DB_USERNAME", "springboot_modulith_admin"));
        poolConfig.setPassword(System.getenv().getOrDefault("SMA_TEST_DB_PASSWORD", "sma_dev"));
        poolConfig.setMaximumPoolSize(3); poolConfig.setMinimumIdle(0);
        try (var pool = new HikariDataSource(poolConfig)) {
            var old = Flyway.configure().dataSource(pool).locations("classpath:db/migration/tenant")
                    .table("flyway_schema_history_tenant").target("5").cleanDisabled(false).load();
            old.clean(); old.migrate();
            var sql = new JdbcTemplate(pool);
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='tenant_storage_resource'", Long.class)).isZero();
            long tenantId = 8_700_000L;
            sql.update("INSERT INTO tenant_tenant(id,code,name,status,security_generation,created_at,created_by,updated_at,updated_by,deleted) "
                    + "VALUES (?,?,?,?,1,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)", tenantId, "s6a-fixture", "S6A fixture", "PROVISIONING");
            var latest = Flyway.configure().dataSource(pool).locations("classpath:db/migration/tenant")
                    .table("flyway_schema_history_tenant").load();
            latest.migrate(); latest.validate();
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM tenant_tenant WHERE id=?", Long.class, tenantId)).isEqualTo(1);

            var bean = new MybatisSqlSessionFactoryBean();
            bean.setDataSource(pool);
            var configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            bean.setConfiguration(configuration);
            bean.setGlobalConfig(new GlobalConfig().setMetaObjectHandler(new MetaObjectHandler() {
                @Override public void insertFill(MetaObject meta) {
                    meta.setValue("createdAt", Instant.now()); meta.setValue("createdBy", 997123L);
                    meta.setValue("updatedAt", Instant.now()); meta.setValue("updatedBy", 997123L);
                    meta.setValue("deleted", false);
                }
                @Override public void updateFill(MetaObject meta) { meta.setValue("updatedAt", Instant.now()); }
            }));
            bean.afterPropertiesSet();
            var factory = bean.getObject();
            factory.getConfiguration().addMapper(TenantRecordMapper.class);
            factory.getConfiguration().addMapper(StorageResourceMapper.class);
            factory.getConfiguration().addMapper(StorageBindingMapper.class);
            var mapper = new SqlSessionTemplate(factory);
            var directory = new StorageDirectory(mapper.getMapper(StorageResourceMapper.class),
                    mapper.getMapper(StorageBindingMapper.class), mapper.getMapper(TenantRecordMapper.class),
                    new PlatformOperators(new TenantPlatformProperties(1L, Set.of(997123L), Set.of())),
                    new TenantStorageProperties(Set.of("127.0.0.1"), true));
            var transactions = new TransactionTemplate(new DataSourceTransactionManager(pool));
            var operator = Users.principal(997123L, 1L, DataAccess.none());
            var foreign = Users.principal(997124L, tenantId, DataAccess.none());
            var resource = new StorageDirectory.ResourceInput("s6-business", 1, "S6 business",
                    "127.0.0.1", 3306, "sma_test_p11_x1b_business_20261112", "DISABLED",
                    "secret/test-business", 2, 1000);
            assertThatThrownBy(() -> Users.as(foreign, () -> directory.register(resource)))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            Users.as(operator, () -> {
                assertThatThrownBy(() -> directory.register(new StorageDirectory.ResourceInput("s6-business", 1,
                        "bad", "169.254.169.254", 3306, "business_db", "VERIFY_IDENTITY", "secret/test", 2, 1000)))
                        .isInstanceOfSatisfying(BizException.class, error -> assertThat(error.getErrorCode()).isEqualTo(TenantErrorCode.INVALID));
                assertThatThrownBy(() -> directory.register(new StorageDirectory.ResourceInput("s6-business", 3,
                        "gap", "127.0.0.1", 3306, "business_db", "DISABLED", "secret/test", 2, 1000)))
                        .isInstanceOfSatisfying(BizException.class, error -> assertThat(error.getErrorCode()).isEqualTo(TenantErrorCode.STATE));
                var stored = transactions.execute(status -> directory.register(resource));
                assertThat(stored.getState()).isEqualTo("REGISTERED");
                assertThatThrownBy(() -> transactions.execute(status -> directory.register(resource)))
                        .isInstanceOfSatisfying(BizException.class, error -> assertThat(error.getErrorCode()).isEqualTo(TenantErrorCode.DUPLICATE));
                var pending = transactions.execute(status -> directory.bindInitial(
                        new StorageDirectory.BindingInput(tenantId, "shop", "s6-business", 1, "v1")));
                assertThat(pending.getState()).isEqualTo("PENDING");
                assertThat(pending.getBindingVersion()).isEqualTo(1L);
                assertThatThrownBy(() -> transactions.execute(status -> directory.bindInitial(
                        new StorageDirectory.BindingInput(tenantId, "shop", "s6-business", 1, "v1"))))
                        .isInstanceOfSatisfying(BizException.class, error -> assertThat(error.getErrorCode()).isEqualTo(TenantErrorCode.DUPLICATE));
                return null;
            });
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM tenant_storage_binding WHERE owner_tenant_id=? AND state='PENDING'", Long.class, tenantId)).isEqualTo(1);
            assertThat(sql.queryForObject("SELECT COUNT(*) FROM tenant_storage_resource WHERE resource_id=?", Long.class, "s6-business")).isEqualTo(1);
        }
    }
}
