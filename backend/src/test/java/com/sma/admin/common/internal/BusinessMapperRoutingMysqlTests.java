package com.sma.admin.common.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.internal.fixture.ShopOrderEntity;
import com.sma.admin.common.internal.fixture.ShopOrderMapper;
import com.sma.admin.common.internal.persistence.BusinessRoutingDataSource;
import com.sma.admin.common.internal.persistence.BusinessRouteScope;
import com.sma.admin.common.internal.security.RequestGovernanceProperties;
import com.sma.admin.common.internal.service.BusinessLedgerResourceFactoryService;
import com.sma.admin.common.internal.service.BusinessLedgerService;
import com.sma.admin.common.request.*;
import com.sma.admin.common.security.DataAccess;
import com.sma.admin.support.Users;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Two physical business MySQL schemas plus a separate platform directory schema. */
@EnabledIfEnvironmentVariable(named="SMA_P10_SECOND_BUSINESS_DB_URL",matches=".+")
class BusinessMapperRoutingMysqlTests {
    @SuppressWarnings("unchecked")
    @Test void businessMapperJoinsThePinnedLedgerTransactionAcrossTwoPhysicalDatabases() throws Exception {
        String platformUrl=System.getenv("SMA_TEST_DB_URL");
        String firstUrl=System.getenv("SMA_P11_BUSINESS_DB_URL");
        String secondUrl=System.getenv("SMA_P10_SECOND_BUSINESS_DB_URL");
        check(platformUrl,"/sma_test_p11_x1b_platform_20261112");
        check(firstUrl,"/sma_test_p11_x1b_business_20261112");
        check(secondUrl,"/sma_test_p11_x1a_20261112");
        try(var platform=pool(platformUrl); var first=pool(firstUrl); var second=pool(secondUrl)) {
            var firstFlyway=flyway(first,false); firstFlyway.migrate(); firstFlyway.validate();
            var secondFlyway=flyway(second,true); secondFlyway.clean(); secondFlyway.migrate(); secondFlyway.validate();
            var firstSql=new JdbcTemplate(first); var secondSql=new JdbcTemplate(second);
            for(var sql:new JdbcTemplate[]{firstSql,secondSql})
                sql.execute("CREATE TABLE IF NOT EXISTS s6c_shop_order (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, code VARCHAR(64) NOT NULL, "
                        + "created_at DATETIME(3) NOT NULL, created_by BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, "
                        + "updated_by BIGINT NOT NULL, deleted TINYINT(1) NOT NULL DEFAULT 0, UNIQUE KEY uk_s6c_shop_code (tenant_id,code)) "
                        + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci");
            var persistence=new PersistenceConfiguration();
            var interceptor=persistence.mybatisPlusInterceptor(); var audit=persistence.auditFields();
            var telemetry=new SqlTelemetry(0);
            var identifiers=new com.sma.admin.common.internal.persistence.DatabaseIdentifierGenerator(platform);
            var businessFactory=PersistenceConfiguration.factory(new BusinessRoutingDataSource(),interceptor,audit,telemetry,identifiers);
            businessFactory.getConfiguration().addMapper(ShopOrderMapper.class);
            var mapper=new SqlSessionTemplate(businessFactory).getMapper(ShopOrderMapper.class);
            var resources=new BusinessLedgerResourceFactoryService(new RequestGovernanceProperties(
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","v1","",100000L,8),interceptor,audit,telemetry,identifiers);
            var a=resources.preparePhysical("s6c-first",1,first);
            var b=resources.preparePhysical("s6c-second",1,second);
            var firstBinding=new AtomicReference<>(a);
            long firstTenant=ThreadLocalRandom.current().nextLong(20_000_000L,80_000_000L);
            long secondTenant=firstTenant+1;
            var released=new AtomicInteger();
            var providers=(ObjectProvider<BusinessLedgerProvider>)mock(ObjectProvider.class);
            when(providers.getIfAvailable()).thenReturn((tenant,group)->{
                assertThat(group).isEqualTo("shop");
                var selected=tenant==firstTenant?firstBinding.get():tenant==secondTenant?b:null;
                return selected==null?null:selected.open(released::incrementAndGet);
            });
            var ledger=new BusinessLedgerService(providers);
            var firstActor=Users.principal(997123L,firstTenant,DataAccess.none());
            Users.as(firstActor,()->{
                try(var session=ledger.open("shop")) {
                    var result=session.executeLocal(command("save",firstTenant),new RequestExecution.Work<Long>() {
                        @Override public void authorize() {}
                        @Override public Long execute() {
                            var row=new ShopOrderEntity(); row.setCode("first-"+firstTenant);
                            mapper.insert(row);
                            session.invalidate("shop.items");
                            return row.getId();
                        }
                    });
                    assertThat(result.result()).isPositive();
                    assertThat(result.result()).isLessThan(new JdbcTemplate(platform).queryForObject(
                            "SELECT next_id FROM common_id_block WHERE id=1",Long.class));
                    assertThat(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId()).isLessThan(new JdbcTemplate(platform).queryForObject(
                            "SELECT next_id FROM common_id_block WHERE id=1",Long.class));
                    assertThat(session.query(()->mapper.selectCount(new LambdaQueryWrapper<ShopOrderEntity>()
                            .eq(ShopOrderEntity::getCode,"first-"+firstTenant)))).isEqualTo(1L);
                }
                return null;
            });
            var secondActor=Users.principal(997124L,secondTenant,DataAccess.none());
            Users.as(secondActor,()->{
                try(var session=ledger.open("shop")) {
                    assertThatThrownBy(()->session.executeLocal(command("rollback",secondTenant),new RequestExecution.Work<Void>() {
                        @Override public void authorize() {}
                        @Override public Void execute() {
                            var row=new ShopOrderEntity(); row.setCode("rollback-"+secondTenant);
                            mapper.insert(row);
                            session.invalidate("shop.items");
                            throw new IllegalStateException("fixture rollback");
                        }
                    })).isInstanceOf(IllegalStateException.class).hasMessageContaining("fixture rollback");
                }
                return null;
            });
            Users.as(firstActor,()->{
                try(var pinned=ledger.open("shop")) {
                    firstBinding.set(b); // Fixture models a directory switch after the session has pinned A.
                    pinned.executeLocal(command("pinned",firstTenant),new RequestExecution.Work<Void>() {
                        @Override public void authorize() {}
                        @Override public Void execute() {
                            var row=new ShopOrderEntity(); row.setCode("pinned-"+firstTenant);
                            mapper.insert(row); return null;
                        }
                    });
                }
                try(var moved=ledger.open("shop")) {
                    moved.executeLocal(command("moved",firstTenant),new RequestExecution.Work<Void>() {
                        @Override public void authorize() {}
                        @Override public Void execute() {
                            var row=new ShopOrderEntity(); row.setCode("moved-"+firstTenant);
                            mapper.insert(row); return null;
                        }
                    });
                }
                return null;
            });
            assertThat(firstSql.queryForObject("SELECT COUNT(*) FROM s6c_shop_order WHERE tenant_id=?",Long.class,firstTenant)).isEqualTo(2);
            Users.as(firstActor,()->{
                assertThat(telemetry.statements()).anySatisfy(statement->{
                    assertThat(statement.id()).endsWith("ShopOrderMapper.insert");
                    assertThat(statement.resourceId()).isEqualTo("s6c-first");
                    assertThat(statement.resourceRevision()).isEqualTo(1);
                });
                return null;
            });
            Users.as(secondActor,()->{
                assertThat(telemetry.statements()).anySatisfy(statement->{
                    assertThat(statement.id()).endsWith("ShopOrderMapper.insert");
                    assertThat(statement.resourceId()).isEqualTo("s6c-second");
                });
                return null;
            });
            assertThat(firstSql.queryForObject("SELECT COUNT(*) FROM s6c_shop_order WHERE tenant_id=?",Long.class,secondTenant)).isZero();
            assertThat(secondSql.queryForObject("SELECT COUNT(*) FROM s6c_shop_order WHERE tenant_id=?",Long.class,firstTenant)).isEqualTo(1);
            assertThat(secondSql.queryForObject("SELECT COUNT(*) FROM s6c_shop_order WHERE tenant_id=?",Long.class,secondTenant)).isZero();
            assertThat(secondSql.queryForObject("SELECT COUNT(*) FROM common_cache_revision WHERE tenant_id=?",Long.class,secondTenant)).isZero();
            assertThat(new JdbcTemplate(platform).queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='s6c_shop_order'",Long.class)).isZero();
            assertThat(released.get()).isEqualTo(4);
            Users.as(firstActor,()->{
                assertThatThrownBy(()->BusinessRouteScope.within(first,()->new TransactionTemplate(
                        new DataSourceTransactionManager(platform)).execute(status->mapper.selectCount(null))))
                        .satisfies(failure->{
                            Throwable root=failure;
                            while(root.getCause()!=null) root=root.getCause();
                            assertThat(root).isInstanceOf(IllegalStateException.class)
                                    .hasMessageContaining("different resource transaction");
                        });
                return null;
            });
            assertThatThrownBy(()->mapper.selectCount(null)).hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("Business mapper used without a pinned storage session");
        }
    }
    private static RequestCommand command(String operation,long tenant) {
        return new RequestCommand("shop."+operation,"shop.order","order-"+tenant,
                "stable-key-"+operation+"-"+tenant,operation.getBytes(StandardCharsets.UTF_8),"");
    }
    private static Flyway flyway(HikariDataSource pool,boolean disposable) {
        return Flyway.configure().dataSource(pool).locations("classpath:db/business-ledger")
                .table("flyway_schema_history_business_ledger").cleanDisabled(!disposable).load();
    }
    private static void check(String url,String path) {
        if(url==null || !url.startsWith("jdbc:mysql://") || !URI.create(url.substring(5)).getPath().equals(path))
            throw new IllegalArgumentException("S6c requires its exact dedicated MySQL schema");
    }
    private static HikariDataSource pool(String url) {
        var configuration=new HikariConfig(); configuration.setJdbcUrl(url);
        configuration.setUsername(System.getenv().getOrDefault("SMA_TEST_DB_USERNAME","springboot_modulith_admin"));
        configuration.setPassword(System.getenv().getOrDefault("SMA_TEST_DB_PASSWORD","sma_dev"));
        configuration.setMaximumPoolSize(3); configuration.setMinimumIdle(0);
        return new HikariDataSource(configuration);
    }
}
