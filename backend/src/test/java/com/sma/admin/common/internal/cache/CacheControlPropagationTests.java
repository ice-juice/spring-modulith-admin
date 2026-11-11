package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.service.CacheRevisionStore;
import com.sma.admin.common.internal.service.CacheRevisionStore.Change;
import com.sma.admin.common.security.*;
import com.sma.admin.support.IntegrationTest;

/** Real committed MySQL controls observed independently by two providers, including real Redis. */
@IntegrationTest
class CacheControlPropagationTests {
    private static final String REGION="config.parameters";
    @Autowired CacheRevisionStore store;
    @Autowired CacheRegistry registry;
    @Autowired PlatformTransactionManager transactions;
    enum Provider {LOCAL,NONE,REDIS}

    @ParameterizedTest @EnumSource(Provider.class)
    void missedControlNotificationsRecoverAndRollbackCannotPauseOrResetSharedState(Provider provider) {
        long tenant=9501+provider.ordinal();
        try(var nodes=new Nodes(provider,tenant)) {
            TenantScope.within(tenant,()->{
                var a=nodes.a;var b=nodes.b;
                assertThat(read(a,"key","cached")).isEqualTo("cached");read(b,"key","cached");
                store.change(REGION,0,Change.RESET_STATS,null); // Deliberately omit fast notifications to both observers.
                nodes.clock.advance(2);a.reconcile();b.reconcile();
                assertThat(a.snapshot(REGION).hits()).isZero();assertThat(b.snapshot(REGION).hits()).isZero();
                assertThat(a.snapshot(REGION).size()).isEqualTo(provider==Provider.NONE?0:1);
                assertThat(store.state(REGION).revision()).isZero();
                new TransactionTemplate(transactions).execute(status->{
                    store.change(REGION,1,Change.PAUSE,null);
                    store.change(REGION,2,Change.POLICY,new CachePolicy(Duration.ofSeconds(1),Duration.ZERO,1,2048,512));
                    assertThat(read(a,"key","transaction source")).isEqualTo("transaction source");
                    status.setRollbackOnly();return null;
                });
                nodes.clock.advance(2);a.reconcile();b.reconcile();
                assertThat(store.state(REGION)).isEqualTo(new CacheRevisionStore.State(0,1,1,false,null));
                assertThat(read(a,"key",provider==Provider.NONE?"cached":"wrong")).isEqualTo("cached");
                store.change(REGION,1,Change.PAUSE,null);
                nodes.clock.advance(2);a.reconcile();b.reconcile();
                assertThat(read(a,"key","a source")).isEqualTo("a source");
                assertThat(read(b,"key","b source")).isEqualTo("b source");
                assertThat(a.snapshot(REGION).size()).isZero();
                var policy=new CachePolicy(Duration.ofSeconds(2),Duration.ZERO,1,4096,1024);
                store.change(REGION,2,Change.POLICY,policy);store.change(REGION,3,Change.RESUME,null);
                nodes.clock.advance(2);a.reconcile();b.reconcile();
                assertThat(read(b,"key","new committed")).isEqualTo("new committed");
                if(provider==Provider.REDIS)assertThat(read(a,"key","wrong")).isEqualTo("new committed");
                assertThat(b.controlState(REGION)).isEqualTo(store.state(REGION));
                assertThat(read(b,"over-quota","source")).isEqualTo("source");
                assertThat(b.snapshot(REGION).size()).isEqualTo(provider==Provider.NONE?0:1);
                return null;
            });
        }
    }
    private String read(ManagedCache cache,String key,String value){return cache.get(REGION,key,String.class,()->value);}
    private final class Nodes implements AutoCloseable {
        final MutableClock clock=new MutableClock();
        final String namespace="p08control-db-"+UUID.randomUUID();
        final List<LettuceConnectionFactory> factories=new ArrayList<>();
        final List<StringRedisTemplate> templates=new ArrayList<>();
        final Provider provider;final long tenant;
        final DurableManagedCache a,b;
        Nodes(Provider provider,long tenant){this.provider=provider;this.tenant=tenant;a=create();b=create();}
        DurableManagedCache create(){
            var properties=new CacheProperties(CacheProperties.Provider.valueOf(provider.name()),namespace,null,null,null,0.0,null);
            ManagedCache delegate;CacheInvalidationTarget target;
            if(provider==Provider.REDIS){
                String portText=System.getenv("SMA_CACHE_REDIS_TEST_PORT");Assumptions.assumeTrue(portText!=null,"Dedicated Redis required");
                int port=Integer.parseInt(portText);if(port<10000||port>65535)throw new IllegalArgumentException("Dedicated high localhost port required");
                var factory=new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1",port),LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build());
                factory.afterPropertiesSet();factory.start();factories.add(factory);
                var template=new StringRedisTemplate(factory);templates.add(template);
                var redis=new RedisManagedCache(registry,properties,CacheResilienceProperties.defaults(),new RedisCacheStore(template,namespace));delegate=redis;target=redis;
            }else{var local=new LocalManagedCache(registry,properties,clock);delegate=local;target=local;}
            return new DurableManagedCache(delegate,target,store,registry,properties,clock);
        }
        public void close(){a.close();b.close();try{if(!templates.isEmpty())templates.getFirst().delete(new RedisCacheStore(templates.getFirst(),namespace).epochKey(REGION,tenant));}finally{factories.forEach(LettuceConnectionFactory::destroy);}}
    }
    private static final class MutableClock extends Clock {
        private Instant now=Instant.parse("2026-11-11T00:00:00Z");
        void advance(long seconds){now=now.plusSeconds(seconds);}
        public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}
    }
}
