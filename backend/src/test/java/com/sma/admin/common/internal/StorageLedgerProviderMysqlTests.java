package com.sma.admin.common.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.sma.admin.common.internal.security.RequestGovernanceProperties;
import com.sma.admin.common.internal.service.BusinessLedgerResourceFactoryService;
import com.sma.admin.common.internal.service.BusinessLedgerService;
import com.sma.admin.common.request.*;
import com.sma.admin.common.security.DataAccess;
import com.sma.admin.support.Users;
import com.sma.admin.tenant.internal.persistence.*;
import com.sma.admin.tenant.internal.service.*;
import com.sma.admin.tenant.storage.StorageCredentialResolver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.mybatis.spring.SqlSessionTemplate;

/** Uses the S6a platform directory and independently migrated X1 business ledger. */
@EnabledIfEnvironmentVariable(named="SMA_P11_BUSINESS_DB_URL",matches=".+")
class StorageLedgerProviderMysqlTests {
    @SuppressWarnings("unchecked")
    @Test void activeBindingSelectsPinnedBusinessPoolAndPendingBindingFailsClosed() throws Exception {
        String platformUrl=System.getenv("SMA_TEST_DB_URL");
        String businessUrl=System.getenv("SMA_P11_BUSINESS_DB_URL");
        if (platformUrl==null || !platformUrl.startsWith("jdbc:mysql://")
                || !URI.create(platformUrl.substring(5)).getPath().equals("/sma_test_p11_x1b_platform_20261112")
                || businessUrl==null || !businessUrl.startsWith("jdbc:mysql://")
                || !URI.create(businessUrl.substring(5)).getPath().equals("/sma_test_p11_x1b_business_20261112"))
            throw new IllegalArgumentException("S6b requires its exact task-owned platform and business schemas");
        try (var platformPool=pool(platformUrl); var businessPool=pool(businessUrl)) {
            var platformSql=new JdbcTemplate(platformPool);
            var businessSql=new JdbcTemplate(businessPool);
            assertThat(platformSql.queryForObject("SELECT COUNT(*) FROM flyway_schema_history_tenant WHERE version='6' AND success=1",Long.class)).isEqualTo(1);
            assertThat(businessSql.queryForObject("SELECT COUNT(*) FROM flyway_schema_history_business_ledger WHERE version='1' AND success=1",Long.class)).isEqualTo(1);
            var persistence=new PersistenceConfiguration();
            var identifiers=new com.sma.admin.common.internal.persistence.DatabaseIdentifierGenerator(platformPool);
            var bean=new MybatisSqlSessionFactoryBean();
            bean.setDataSource(platformPool);
            var configuration=new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
            bean.setConfiguration(configuration);
            bean.setGlobalConfig(new GlobalConfig().setMetaObjectHandler(persistence.auditFields()).setIdentifierGenerator(identifiers));
            bean.setPlugins(persistence.mybatisPlusInterceptor());
            bean.afterPropertiesSet();
            var factory=bean.getObject();
            factory.getConfiguration().addMapper(TenantRecordMapper.class);
            factory.getConfiguration().addMapper(StorageResourceMapper.class);
            factory.getConfiguration().addMapper(StorageBindingMapper.class);
            var mappers=new SqlSessionTemplate(factory);
            var tenants=mappers.getMapper(TenantRecordMapper.class);
            var directory=new StorageDirectory(mappers.getMapper(StorageResourceMapper.class),
                    mappers.getMapper(StorageBindingMapper.class),tenants,
                    new PlatformOperators(new TenantPlatformProperties(1L,Set.of(997123L),Set.of())),
                    new TenantStorageProperties(Set.of("127.0.0.1"),true));
            var operator=Users.principal(997123L,1L,DataAccess.none());
            long tenant=ThreadLocalRandom.current().nextLong(10_000_000L,90_000_000L);
            long otherTenant=tenant+1;
            String resourceId="s6b-"+tenant;
            var platformTransaction=new TransactionTemplate(new DataSourceTransactionManager(platformPool));
            Users.as(operator,()->{
                platformTransaction.execute(status->{
                    var row=new TenantRecordEntity(); row.setId(tenant); row.setCode("s6b-"+tenant);
                    row.setName("S6B fixture"); row.setStatus("ACTIVE"); row.setSecurityGeneration(1L);
                    tenants.insert(row);
                    var other=new TenantRecordEntity(); other.setId(otherTenant); other.setCode("s6b-"+otherTenant);
                    other.setName("S6B other fixture"); other.setStatus("ACTIVE"); other.setSecurityGeneration(1L);
                    tenants.insert(other);
                    directory.register(new StorageDirectory.ResourceInput(resourceId,1,"S6B physical resource",
                            "127.0.0.1",URI.create(businessUrl.substring(5)).getPort(),"sma_test_p11_x1b_business_20261112","DISABLED",
                            "secret/s6b",2,1000));
                    directory.bindInitial(new StorageDirectory.BindingInput(tenant,"shop",resourceId,1,"v1"));
                    return null;
                }); return null;
            });
            var secretProvider=(ObjectProvider<StorageCredentialResolver>)mock(ObjectProvider.class);
            when(secretProvider.getIfAvailable()).thenReturn(ref -> {
                assertThat(ref).isEqualTo("secret/s6b");
                return new StorageCredentialResolver.Credentials(
                        System.getenv().getOrDefault("SMA_TEST_DB_USERNAME","springboot_modulith_admin"),
                        System.getenv().getOrDefault("SMA_TEST_DB_PASSWORD","sma_dev"));
            });
            var ledgerFactory=new BusinessLedgerResourceFactoryService(new RequestGovernanceProperties(
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","v1","",100000L,8),
                    persistence.mybatisPlusInterceptor(),persistence.auditFields(),null,identifiers);
            try (var pools=new StoragePoolRegistry(ledgerFactory,secretProvider,
                    new StoragePoolProperties(2,4,4,Duration.ofMinutes(5)),new com.sma.admin.support.DeploymentWorkFixture())) {
                var provider=new StorageLedgerProvider(directory,pools,tenants);
                var user=Users.principal(800123L,tenant,DataAccess.none());
                var foreign=Users.principal(800124L,otherTenant,DataAccess.none());
                assertThatThrownBy(()->Users.as(user,()->provider.open(tenant,"shop")))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("not provisioned");
                assertThatThrownBy(()->Users.as(foreign,()->provider.open(tenant,"shop")))
                        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
                assertThat(pools.activePools()).isZero();
                // S7 will own this state transition; this isolated fixture activates an already migrated schema.
                platformSql.update("UPDATE tenant_storage_binding SET state='ACTIVE' WHERE owner_tenant_id=? AND storage_group='shop'",tenant);
                var delegates=(ObjectProvider<BusinessLedgerProvider>)mock(ObjectProvider.class);
                when(delegates.getIfAvailable()).thenReturn(provider);
                var ledger=new BusinessLedgerService(delegates);
                assertThatThrownBy(()->Users.as(foreign,()->ledger.open("shop")))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("not bound");
                Users.as(user,()->{
                    try(var session=ledger.open("shop")) {
                        assertThatThrownBy(()->Users.as(foreign,()->session.receiptByKey("stable-key-00000001")))
                                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
                        var command=new RequestCommand("shop.save","shop.order","order-1",
                                "stable-key-00000001", "payload".getBytes(StandardCharsets.UTF_8),"");
                        var result=session.executeLocal(command,new RequestExecution.Work<String>() {
                            @Override public void authorize() {}
                            @Override public String execute() {
                                session.invalidate("shop.items");
                                session.enqueue("shop.order.created","event-s6b-00000001",
                                        "event".getBytes(StandardCharsets.UTF_8));
                                return "stored";
                            }
                        });
                        assertThat(result.result()).isEqualTo("stored");
                        assertThat(session.authoritativeRevision("shop.items")).isEqualTo(1);
                    }
                    return null;
                });
                assertThat(businessSql.queryForObject("SELECT COUNT(*) FROM common_request_execution WHERE tenant_id=? AND state='SUCCEEDED'",Long.class,tenant)).isEqualTo(1);
                assertThat(businessSql.queryForObject("SELECT COUNT(*) FROM common_business_outbox WHERE tenant_id=?",Long.class,tenant)).isEqualTo(1);
                assertThat(platformSql.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='common_business_outbox'",Long.class)).isZero();
                assertThat(pools.activePools()).isEqualTo(1);
            }
        }
    }
    private static HikariDataSource pool(String url) {
        var config=new HikariConfig(); config.setJdbcUrl(url);
        config.setUsername(System.getenv().getOrDefault("SMA_TEST_DB_USERNAME","springboot_modulith_admin"));
        config.setPassword(System.getenv().getOrDefault("SMA_TEST_DB_PASSWORD","sma_dev"));
        config.setMaximumPoolSize(3); config.setMinimumIdle(0);
        return new HikariDataSource(config);
    }
}
