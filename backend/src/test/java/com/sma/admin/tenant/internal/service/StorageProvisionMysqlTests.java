package com.sma.admin.tenant.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.sma.admin.common.security.DataAccess;
import com.sma.admin.support.Users;
import com.sma.admin.tenant.internal.persistence.StorageBindingMapper;
import com.sma.admin.tenant.internal.persistence.StorageOperationMapper;
import com.sma.admin.tenant.internal.persistence.StorageResourceMapper;
import com.sma.admin.tenant.internal.persistence.TenantRecordEntity;
import com.sma.admin.tenant.internal.persistence.TenantRecordMapper;
import com.sma.admin.tenant.storage.StorageCredentialResolver;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;

/** Exact task owned platform directory schema; no business pool is activated by this state test. */
@EnabledIfEnvironmentVariable(named = "SMA_TEST_DB_URL", matches = "jdbc:mysql://[^/]+/sma_test_p11_x1b_platform_20261112(\\?.*)?")
@EnabledIfEnvironmentVariable(named = "SMA_P11_BUSINESS_DB_URL", matches = ".+")
class StorageProvisionMysqlTests {
    @Test void durableClaimFenceAndActivationAreAtomic() throws Exception {
        String url = System.getenv("SMA_TEST_DB_URL");
        if (url == null || !url.startsWith("jdbc:mysql://")
                || !URI.create(url.substring(5)).getPath().equals("/sma_test_p11_x1b_platform_20261112"))
            throw new IllegalArgumentException("S7b requires its exact disposable platform test schema");
        var configuration = new HikariConfig();
        configuration.setJdbcUrl(url);
        configuration.setUsername(System.getenv().getOrDefault("SMA_TEST_DB_USERNAME", "springboot_modulith_admin"));
        configuration.setPassword(System.getenv().getOrDefault("SMA_TEST_DB_PASSWORD", "sma_dev"));
        configuration.setMaximumPoolSize(3); configuration.setMinimumIdle(0);
        try (var pool = new HikariDataSource(configuration)) {
            var flyway = Flyway.configure().dataSource(pool).locations("classpath:db/migration/tenant")
                    .table("flyway_schema_history_tenant").cleanDisabled(false).load();
            flyway.clean(); flyway.migrate(); flyway.validate();
            var bean = new MybatisSqlSessionFactoryBean();
            bean.setDataSource(pool);
            var mybatis = new MybatisConfiguration(); mybatis.setMapUnderscoreToCamelCase(true);
            bean.setConfiguration(mybatis);
            bean.setGlobalConfig(new GlobalConfig().setMetaObjectHandler(new MetaObjectHandler() {
                @Override public void insertFill(MetaObject meta) {
                    meta.setValue("createdAt", Instant.now()); meta.setValue("createdBy", 997123L);
                    meta.setValue("updatedAt", Instant.now()); meta.setValue("updatedBy", 997123L);
                    meta.setValue("deleted", false);
                    if (meta.hasSetter("tenantId")) meta.setValue("tenantId", 1L);
                }
                @Override public void updateFill(MetaObject meta) { meta.setValue("updatedAt", Instant.now()); }
            }));
            bean.afterPropertiesSet();
            var factory = bean.getObject();
            factory.getConfiguration().addMapper(TenantRecordMapper.class);
            factory.getConfiguration().addMapper(StorageResourceMapper.class);
            factory.getConfiguration().addMapper(StorageBindingMapper.class);
            factory.getConfiguration().addMapper(StorageOperationMapper.class);
            var mappers = new SqlSessionTemplate(factory);
            var tenants = mappers.getMapper(TenantRecordMapper.class);
            var resources = mappers.getMapper(StorageResourceMapper.class);
            var bindings = mappers.getMapper(StorageBindingMapper.class);
            var operations = mappers.getMapper(StorageOperationMapper.class);
            var operators = new PlatformOperators(new TenantPlatformProperties(1L, Set.of(997123L), Set.of()));
            var directory = new StorageDirectory(resources, bindings, tenants, operators,
                    new TenantStorageProperties(Set.of("127.0.0.1"), true));
            var state = new StorageProvisionState(bindings, operations, operators);
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(pool));
            var platform = Users.principal(997123L, 1L, DataAccess.none());
            long bindingId = Users.as(platform, () -> transaction.execute(status -> {
                var target = new TenantRecordEntity();
                target.setCode("s7b-target"); target.setName("S7B target");
                target.setStatus("PROVISIONING"); target.setSecurityGeneration(1L); tenants.insert(target);
                directory.register(new StorageDirectory.ResourceInput("s7b-resource", 1, "S7B resource",
                        "127.0.0.1", URI.create(System.getenv("SMA_P11_BUSINESS_DB_URL").substring(5)).getPort(), "sma_test_p11_x1b_business_20261112", "DISABLED",
                        "secret/s7b", 2, 1000));
                return directory.bindInitial(new StorageDirectory.BindingInput(target.getId(), "shop",
                        "s7b-resource", 1, "v1")).getId();
            }));
            var first = Users.as(platform, () -> transaction.execute(status -> state.begin(bindingId, 1, "s7b-operation-0001")));
            assertThat(first.run()).isTrue();
            assertThatThrownBy(() -> Users.as(platform, () -> transaction.execute(status -> state.begin(bindingId, 1, "s7b-operation-0001"))))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("already running");
            assertThatThrownBy(() -> Users.as(platform, () -> transaction.execute(status -> state.begin(bindingId, 1, "s7b-operation-0002"))))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("unfinished");
            assertThatThrownBy(() -> Users.as(Users.principal(997124L, 2L, DataAccess.none()),
                    () -> transaction.execute(status -> state.begin(bindingId, 1, "s7b-operation-0001"))))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            Users.as(platform, () -> { transaction.executeWithoutResult(status -> state.failed(first)); return null; });
            assertThat(Users.as(platform, () -> state.state("s7b-operation-0001"))).isEqualTo("NEEDS_REVIEW");
            assertThatThrownBy(() -> Users.as(platform, () -> transaction.execute(status -> state.begin(bindingId, 1, "s7b-operation-0001"))))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("operator review");
            var resumed = Users.as(platform, () -> transaction.execute(status -> state.retryAfterReview(bindingId, 1, "s7b-operation-0001")));
            assertThat(resumed.fence()).isEqualTo(2);
            Users.as(platform, () -> { transaction.executeWithoutResult(status -> state.finish(resumed)); return null; });
            var sql = new JdbcTemplate(pool);
            assertThat(sql.queryForObject("SELECT state FROM tenant_storage_binding WHERE id=?", String.class, bindingId)).isEqualTo("ACTIVE");
            assertThat(sql.queryForObject("SELECT binding_version FROM tenant_storage_binding WHERE id=?", Long.class, bindingId)).isEqualTo(2);
            assertThat(sql.queryForObject("SELECT state FROM tenant_storage_operation WHERE operation_id='s7b-operation-0001'", String.class)).isEqualTo("SUCCEEDED");
            var replay = Users.as(platform, () -> transaction.execute(status -> state.begin(bindingId, 1, "s7b-operation-0001")));
            assertThat(replay.run()).isFalse();
            assertThatThrownBy(() -> Users.as(platform, () -> transaction.execute(status -> state.begin(bindingId, 1, "s7b-operation-0003"))))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("not pending");

            var secondBinding = Users.as(platform, () -> transaction.execute(status -> {
                var target = new TenantRecordEntity();
                target.setCode("s7b-second"); target.setName("S7B second");
                target.setStatus("PROVISIONING"); target.setSecurityGeneration(1L); tenants.insert(target);
                return directory.bindInitial(new StorageDirectory.BindingInput(target.getId(), "shop",
                        "s7b-resource", 1, "v1")).getId();
            }));
            var provider = (ObjectProvider<StorageCredentialResolver>)mock(ObjectProvider.class);
            var proxy = new ProxyFactory(state);
            proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(pool),
                    new AnnotationTransactionAttributeSource()));
            var transactionalState = (StorageProvisionState)proxy.getProxy();
            var schemas=mock(BusinessSchemaDirectory.class);
            when(schemas.require("shop")).thenReturn(java.util.List.of(new BusinessSchemaDirectory.Schema("shop","shop")));
            var service = new StorageProvisionService(transactionalState,
                    new StorageMigrationService(directory, operators, provider,schemas));
            assertThatThrownBy(() -> Users.as(platform, () -> service.provision(secondBinding, 1, "s7b-operation-0004")))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("resolver");
            assertThat(sql.queryForObject("SELECT state FROM tenant_storage_binding WHERE id=?", String.class, secondBinding)).isEqualTo("PENDING");
            assertThat(sql.queryForObject("SELECT state FROM tenant_storage_operation WHERE operation_id='s7b-operation-0004'", String.class)).isEqualTo("NEEDS_REVIEW");
            when(provider.getIfAvailable()).thenReturn(ref -> new StorageCredentialResolver.Credentials(
                    System.getenv().getOrDefault("SMA_TEST_DB_USERNAME", "springboot_modulith_admin"),
                    System.getenv().getOrDefault("SMA_TEST_DB_PASSWORD", "sma_dev")));
            assertThatThrownBy(() -> Users.as(platform, () -> service.provision(secondBinding, 1, "s7b-operation-0004")))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("operator review");
            assertThat(Users.as(platform, () -> service.retryAfterReview(secondBinding, 1, "s7b-operation-0004"))).isEqualTo("SUCCEEDED");
            assertThat(sql.queryForObject("SELECT state FROM tenant_storage_binding WHERE id=?", String.class, secondBinding)).isEqualTo("ACTIVE");
            assertThat(Users.as(platform, () -> service.provision(secondBinding, 1, "s7b-operation-0004"))).isEqualTo("SUCCEEDED");
            var businessConfiguration=new HikariConfig();businessConfiguration.setJdbcUrl(System.getenv("SMA_P11_BUSINESS_DB_URL"));
            businessConfiguration.setUsername(configuration.getUsername());businessConfiguration.setPassword(configuration.getPassword());businessConfiguration.setMaximumPoolSize(1);
            try(var businessPool=new HikariDataSource(businessConfiguration)){
                var businessSql=new JdbcTemplate(businessPool);
                assertThat(businessSql.queryForObject("SELECT COUNT(*) FROM flyway_schema_history_business_shop WHERE version='1' AND success=1",Long.class)).isEqualTo(1);
                assertThat(businessSql.queryForObject("SELECT COUNT(*) FROM shop_baseline_probe",Long.class)).isZero();
                assertThat(sql.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='shop_baseline_probe'",Long.class)).isZero();
            }
        }
    }
}
