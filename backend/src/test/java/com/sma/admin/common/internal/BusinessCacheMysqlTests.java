package com.sma.admin.common.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.cache.*;
import com.sma.admin.common.internal.fixture.*;
import com.sma.admin.common.internal.persistence.*;
import com.sma.admin.common.internal.security.RequestGovernanceProperties;
import com.sma.admin.common.internal.service.*;
import com.sma.admin.common.request.*;
import com.sma.admin.common.security.DataAccess;
import com.sma.admin.support.*;
import com.zaxxer.hikari.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/** Fixed source sessions, real MySQL rollback and shared Redis with a task-local TCP fault. */
@EnabledIfEnvironmentVariable(named="SMA_P10_SECOND_BUSINESS_DB_URL",matches=".+")
@EnabledIfEnvironmentVariable(named="SMA_CACHE_REDIS_TEST_PORT",matches="[1-9][0-9]{3,4}")
class BusinessCacheMysqlTests {
    @SuppressWarnings("unchecked")
    @Test void sourceLocalCommitsRollbackRebindingAndRedisFailureKeepTenantAndResourceIsolation()throws Exception {
        String platformUrl=TestDatabase.URL,aUrl=System.getenv("SMA_P11_BUSINESS_DB_URL"),bUrl=System.getenv("SMA_P10_SECOND_BUSINESS_DB_URL");
        check(platformUrl,"sma_test_p11_x1b_platform_20261112");check(aUrl,"sma_test_p11_x1b_business_20261112");check(bUrl,"sma_test_p11_x1a_20261112");
        int port=Integer.parseInt(System.getenv("SMA_CACHE_REDIS_TEST_PORT"));
        if(port<10000||port>65535)throw new IllegalArgumentException("Dedicated Redis high port required");
        String namespace="business-test-"+UUID.randomUUID();
        var client=LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).shutdownTimeout(Duration.ZERO).build();
        var direct=new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1",port),client);direct.afterPropertiesSet();
        var cleanup=new StringRedisTemplate(direct);var keys=new HashSet<String>();
        try(var platform=pool(platformUrl);var a=pool(aUrl);var b=pool(bUrl);var proxy=new RedisFaultProxy(port)) {
            var connection=new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1",proxy.port()),client);connection.afterPropertiesSet();
            try {
                var template=new StringRedisTemplate(connection);
                try(var probe=template.getConnectionFactory().getConnection()){assertThat(probe.ping()).isEqualTo("PONG");}
                for(var source:List.of(a,b)) {
                    Flyway.configure().dataSource(source).locations("classpath:db/business-ledger").table("flyway_schema_history_business_ledger").load().migrate();
                    new JdbcTemplate(source).execute("CREATE TABLE IF NOT EXISTS s6c_shop_order(id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,code VARCHAR(64) NOT NULL,created_at DATETIME(3) NOT NULL,created_by BIGINT NOT NULL,updated_at DATETIME(3) NOT NULL,updated_by BIGINT NOT NULL,deleted TINYINT(1) NOT NULL DEFAULT 0,UNIQUE KEY uk_s6c_shop_code(tenant_id,code)) ENGINE=InnoDB");
                }
                var persistence=new PersistenceConfiguration();var identifiers=new DatabaseIdentifierGenerator(platform);
                var factory=PersistenceConfiguration.factory(new BusinessRoutingDataSource(),persistence.mybatisPlusInterceptor(),persistence.auditFields(),new SqlTelemetry(0),identifiers);
                factory.getConfiguration().addMapper(ShopOrderMapper.class);var mapper=new SqlSessionTemplate(factory).getMapper(ShopOrderMapper.class);
                var resources=new BusinessLedgerResourceFactoryService(new RequestGovernanceProperties("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","v1","",100000L,8),
                        persistence.mybatisPlusInterceptor(),persistence.auditFields(),null,identifiers);
                var first=resources.preparePhysical("cache-source-a",1,a);var second=resources.preparePhysical("cache-source-b",1,b);
                var selected=new AtomicReference<>(first);var closes=new AtomicInteger();
                var providers=(ObjectProvider<BusinessLedgerProvider>)mock(ObjectProvider.class);
                when(providers.getIfAvailable()).thenReturn((tenant,group)->selected.get().open(closes::incrementAndGet));
                var ledger=new BusinessLedgerService(providers);
                var descriptor=new CacheDescriptor("shop.items","shop","shop.cache.items","catalog","items",CacheDescriptor.Kind.DATA,
                        CacheDescriptor.Scope.TENANT,1,new CachePolicy(Duration.ofMinutes(1),Duration.ZERO,100,65536,4096),Set.of(CacheDescriptor.Operation.CLEAR),Set.of("save"),"shop:cache:manage");
                var directory=new BusinessCacheRegistry(List.of(()->List.of(new BusinessCacheCatalog.Region("shop",descriptor))),mock(CacheRegistrationOwnership.class),new CacheRegistry(List.of()));
                var redis=(ObjectProvider<StringRedisTemplate>)mock(ObjectProvider.class);when(redis.getObject()).thenReturn(template);
                var properties=new CacheProperties(CacheProperties.Provider.REDIS,namespace,null,null,null,0d,Map.of());
                var resilience=new CacheResilienceProperties(4,2,null,1,Duration.ofMillis(50));
                var cache=new BusinessCacheService(directory,ledger,properties,resilience,redis);
                var peer=new BusinessCacheService(directory,ledger,properties,resilience,redis);
                long tenant=100000000L+Math.abs(UUID.randomUUID().getLeastSignificantBits()%100000000L);
                for(long id:List.of(tenant,tenant+1))for(String source:List.of("cache-source-a","cache-source-b"))keys.add(key(namespace,source,id));
                var loads=new AtomicInteger();Supplier<Long> loader=()->{loads.incrementAndGet();return mapper.selectCount(null);};
                Users.as(Users.principal(71,tenant,DataAccess.unrestricted()),()->{
                    assertThat(cache.get("shop.items","count",Long.class,loader)).isZero();
                    assertThat(peer.get("shop.items","count",Long.class,loader)).isZero();assertThat(loads).hasValue(1);
                    write(ledger,mapper,tenant,false);
                    assertThat(peer.get("shop.items","count",Long.class,loader)).isEqualTo(1);assertThat(loads).hasValue(2);
                    assertThatThrownBy(()->write(ledger,mapper,tenant,true)).hasMessage("rollback probe");
                    assertThat(cache.get("shop.items","count",Long.class,loader)).isEqualTo(1);assertThat(loads).hasValue(2);
                    try(var pinned=ledger.open("shop")) {
                        selected.set(second);
                        assertThat(pinned.query(()->mapper.selectCount(null))).isEqualTo(1);
                        assertThat(cache.get("shop.items","count",Long.class,loader)).isZero();
                    }
                    proxy.disconnect();
                    assertThat(cache.get("shop.items","count",Long.class,loader)).isZero(); // must return the source value despite an existing Redis entry
                    return null;
                });
                assertThat(Users.as(Users.principal(72,tenant+1,DataAccess.unrestricted()),()->cache.get("shop.items","count",Long.class,loader))).isZero();
                assertThat(new JdbcTemplate(a).queryForObject("SELECT revision FROM common_cache_revision WHERE tenant_id=? AND region='shop.items'",Long.class,tenant)).isEqualTo(1);
                assertThat(new JdbcTemplate(platform).queryForObject("SELECT COUNT(*) FROM common_cache_revision WHERE tenant_id=? AND region='shop.items'",Long.class,tenant)).isZero();
                assertThat(closes.get()).isGreaterThanOrEqualTo(10);
            } finally {connection.destroy();}
        } finally {try{if(!keys.isEmpty())cleanup.delete(keys);}finally{direct.destroy();}}
    }
    private static void write(BusinessLedger ledger,ShopOrderMapper mapper,long tenant,boolean rollback){
        try(var session=ledger.open("shop")){
            session.executeLocal(new RequestCommand("shop.save","shop.order","order-"+tenant,"cache-write-"+UUID.randomUUID(),new byte[]{1},""),new RequestExecution.Work<Void>(){
                public void authorize(){}
                public Void execute(){var row=new ShopOrderEntity();row.setCode(UUID.randomUUID().toString());mapper.insert(row);session.invalidate("shop.items");if(rollback)throw new IllegalStateException("rollback probe");return null;}
            });
        }
    }
    private static HikariDataSource pool(String url){var config=new HikariConfig();config.setJdbcUrl(url);config.setUsername(TestDatabase.USERNAME);config.setPassword(TestDatabase.PASSWORD);config.setMinimumIdle(0);config.setMaximumPoolSize(3);return new HikariDataSource(config);}
    private static void check(String url,String schema){if(url==null||!url.startsWith("jdbc:mysql://")||!URI.create(url.substring(5)).getPath().equals("/"+schema))throw new IllegalArgumentException("Exact task schema required");}
    private static String hash(String value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
    private static String key(String namespace,String source,long tenant)throws Exception {
        var scoped="business1-"+hash(namespace+"|shop|"+source+"|1").substring(0,48);
        return "sma:"+scoped+":cache:v2:{"+hash(scoped+":shop.items:"+tenant)+"}:shop.items:t"+tenant;
    }
}
