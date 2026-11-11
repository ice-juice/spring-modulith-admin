package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.service.CacheRevisionStore;
import com.sma.admin.common.security.*;

class CacheRuntimeControlTests {
    private static final String REGION="config.parameters";
    enum Provider { LOCAL, NONE, REDIS }

    @ParameterizedTest @EnumSource(Provider.class)
    void pauseBypassesStorageAndResumeCannotResurrectOldDataWhileOtherTenantsAreUntouched(Provider provider) {
        try(var f=new Fixture(provider)) {
            assertThat(f.read(f.a,"key","old")).isEqualTo("old");f.read(f.b,"key","old");
            TenantScope.within(2,()->f.read(f.a,"key","other tenant"));
            f.change(true,null,false);
            assertThat(f.read(f.a,"key","first bypass")).isEqualTo("first bypass");
            assertThat(f.read(f.a,"key","second bypass")).isEqualTo("second bypass");
            assertThat(f.a.snapshot(REGION).size()).isZero();
            f.clock.advance(2);f.b.reconcile();
            assertThat(f.read(f.b,"key","remote bypass")).isEqualTo("remote bypass");
            assertThat(f.read(f.node(),"key","cold bypass")).isEqualTo("cold bypass");
            TenantScope.within(2,()->{
                assertThat(f.read(f.a,"key",provider==Provider.NONE?"other tenant":"wrong")).isEqualTo("other tenant");return null;
            });
            f.change(false,null,false);
            assertThat(f.read(f.a,"key","resumed")).isEqualTo("resumed");
            assertThat(f.a.snapshot(REGION).size()).isEqualTo(provider==Provider.NONE?0:1);
        }
    }

    @ParameterizedTest @EnumSource(Provider.class)
    void runtimeLimitsArePerTenantAndShortenedTtlReplacesLongLivedEntries(Provider provider) {
        try(var f=new Fixture(provider)) {
            f.read(f.a,"old","old");
            var policy=new CachePolicy(Duration.ofMillis(150),Duration.ZERO,1,4096,1024);
            f.change(false,policy,false);
            assertThat(f.read(f.a,"one","new")).isEqualTo("new");
            f.read(f.a,"two","quota fallback");
            assertThat(f.a.snapshot(REGION).size()).isEqualTo(provider==Provider.NONE?0:1);
            TenantScope.within(2,()->{
                f.read(f.a,"a","other a");f.read(f.a,"b","other b");
                assertThat(f.a.snapshot(REGION).size()).isEqualTo(provider==Provider.NONE?0:2);return null;
            });
            f.clock.advance(1);
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(()->
                    assertThat(f.read(f.a,"one","expired source")).isEqualTo("expired source"));
            assertThat(f.read(f.a,"old","fresh old key")).isEqualTo("fresh old key");
        }
    }

    @ParameterizedTest @EnumSource(Provider.class)
    void statisticsResetPreservesEntriesAndRejectsDelayedOldStatisticsWindows(Provider provider) throws Exception {
        try(var f=new Fixture(provider)) {
            f.read(f.a,"cached","value");f.read(f.a,"cached","value");
            var before=f.a.snapshot(REGION);
            f.change(false,null,true);
            var after=f.a.snapshot(REGION);
            assertThat(after.size()).isEqualTo(before.size());assertThat(after.hits()).isZero();assertThat(after.misses()).isZero();assertThat(after.bypasses()).isZero();
            var loads=new AtomicInteger();
            assertThat(f.a.get(REGION,"cached",String.class,()->{loads.incrementAndGet();return "value";})).isEqualTo("value");
            assertThat(loads).hasValue(provider==Provider.NONE?1:0);
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
                var request=executor.submit(()->f.a.get(REGION,"in-flight",String.class,()->{
                    entered.countDown();await(release);return "in-flight value";
                }));
                try{assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();f.change(false,null,true);}
                finally{release.countDown();}
                assertThat(request.get(5,TimeUnit.SECONDS)).isEqualTo("in-flight value");
            }
            assertThat(f.a.snapshot(REGION).hits()).isZero();assertThat(f.a.snapshot(REGION).misses()).isZero();assertThat(f.a.snapshot(REGION).bypasses()).isZero();
        }
    }

    @ParameterizedTest @EnumSource(Provider.class)
    void aPauseDuringAnOldLoadPreventsRefillAndRecoveryUsesCurrentPolicy(Provider provider) throws Exception {
        try(var f=new Fixture(provider);var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            var request=executor.submit(()->f.a.get(REGION,"race",String.class,()->{entered.countDown();await(release);return "old";}));
            try{assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();f.change(true,null,false);}
            finally{release.countDown();}
            assertThat(request.get(5,TimeUnit.SECONDS)).isEqualTo("old"); // Already-running source query is not cancelled.
            assertThat(f.a.snapshot(REGION).size()).isZero();
            f.change(false,new CachePolicy(Duration.ofSeconds(1),Duration.ZERO,2,4096,1024),false);
            assertThat(f.read(f.a,"race","current")).isEqualTo("current");
        }
    }

    @Test void staleRedisObserverCannotUseNewGenerationWithItsOldPolicy() {
        try(var f=new Fixture(Provider.REDIS)) {
            f.read(f.a,"old","old");f.read(f.b,"old","old");
            f.change(false,new CachePolicy(Duration.ofSeconds(1),Duration.ZERO,1,4096,1024),false);
            assertThat(f.read(f.b,"stale","source")).isEqualTo("source");
            assertThat(f.a.snapshot(REGION).size()).isZero(); // B has not observed the new policy; it must not fill the new generation.
            f.clock.advance(2);f.b.reconcile();f.read(f.b,"current","new");
            assertThat(f.a.snapshot(REGION).size()).isEqualTo(1);
            assertThat(f.read(f.a,"current","wrong")).isEqualTo("new");
        }
    }

    @Test void runtimePolicyNeverExpandsDeploymentBounds() {
        var deployment=new CachePolicy(Duration.ofSeconds(2),Duration.ofSeconds(1),2,2048,512);
        assertThat(CacheRuntimePolicy.effective(deployment,CachePolicy.defaults())).isEqualTo(deployment);
        var runtime=new CachePolicy(Duration.ofSeconds(1),Duration.ZERO,1,1024,256);
        assertThat(CacheRuntimePolicy.effective(deployment,runtime)).isEqualTo(runtime);
    }

    @Test void delayedOldPollCannotHideACommittedStatisticsResetWithTheSameDataRevision() throws Exception {
        try(var f=new Fixture(Provider.LOCAL);var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            f.read(f.a,"key","cached");f.read(f.a,"key","cached");f.clock.advance(2);
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            doAnswer(call->{var captured=f.authority.getOrDefault(1L,CacheRevisionStore.State.initial());entered.countDown();await(release);return captured;})
                    .when(f.revisions).state(REGION);
            var poll=executor.submit(f.a::reconcile);
            try{assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();f.change(false,null,true);}
            finally{release.countDown();}
            poll.get(5,TimeUnit.SECONDS);
            doAnswer(call->f.authority.getOrDefault(CurrentUser.tenantId(),CacheRevisionStore.State.initial())).when(f.revisions).state(REGION);
            assertThat(f.a.snapshot(REGION).hits()).isZero();
            assertThat(f.a.snapshot(REGION).size()).isEqualTo(1);
            assertThat(f.read(f.a,"key","wrong")).isEqualTo("cached");
            assertThat(f.a.snapshot(REGION).hits()).isEqualTo(1);
        }
    }

    @Test void regressedControlOrStatisticsStateBypassesInsteadOfReusingCachedData() {
        try(var f=new Fixture(Provider.LOCAL)) {
            f.read(f.a,"key","cached");f.change(false,null,true);f.clock.advance(2);
            f.authority.put(1L,CacheRevisionStore.State.initial());
            assertThat(f.read(f.a,"key","source after regression")).isEqualTo("source after regression");
            assertThat(f.a.snapshot(REGION).size()).isEqualTo(-1);
            f.authority.put(1L,new CacheRevisionStore.State(0,2,0,false,null));
            assertThat(f.read(f.a,"key","source after epoch regression")).isEqualTo("source after epoch regression");
            assertThat(f.a.snapshot(REGION).size()).isEqualTo(-1);
        }
    }

    private static void await(CountDownLatch latch){try{if(!latch.await(5,TimeUnit.SECONDS))throw new AssertionError("Test gate timed out");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(interrupted);}}
    private static final class Fixture implements AutoCloseable {
        final Provider provider;
        final MutableClock clock=new MutableClock();
        final Map<Long,CacheRevisionStore.State> authority=new ConcurrentHashMap<>();
        final CacheRevisionStore revisions=mock(CacheRevisionStore.class);
        final CacheRegistry registry=new CacheRegistry(List.of(()->List.of(new CacheDescriptor(REGION,"config","config.cache.parameters","group","scenario",
                CacheDescriptor.Kind.DATA,CacheDescriptor.Scope.TENANT,1,CachePolicy.defaults(),Set.of(CacheDescriptor.Operation.CLEAR),Set.of("save"),"monitor:cache:clear"))));
        final CacheProperties properties;
        final List<LettuceConnectionFactory> factories=new ArrayList<>();
        final List<DurableManagedCache> nodes=new ArrayList<>();
        final List<StringRedisTemplate> templates=new ArrayList<>();
        final String namespace="p08control-"+UUID.randomUUID();
        final DurableManagedCache a,b;
        Fixture(Provider provider){
            this.provider=provider;
            properties=new CacheProperties(CacheProperties.Provider.valueOf(provider.name()),namespace,null,null,null,0.0,null);
            when(revisions.state(REGION)).thenAnswer(call->authority.getOrDefault(CurrentUser.tenantId(),CacheRevisionStore.State.initial()));
            a=node();b=node();
        }
        DurableManagedCache node(){
            ManagedCache delegate;CacheInvalidationTarget target;
            if(provider==Provider.REDIS){
                String portText=System.getenv("SMA_CACHE_REDIS_TEST_PORT");Assumptions.assumeTrue(portText!=null,"Dedicated Redis test port required");
                int port=Integer.parseInt(portText);if(port<10000||port>65535)throw new IllegalArgumentException("Dedicated localhost high port required");
                var factory=new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1",port),LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build());
                factory.afterPropertiesSet();factory.start();factories.add(factory);
                var template=new StringRedisTemplate(factory);templates.add(template);
                var redis=new RedisManagedCache(registry,properties,CacheResilienceProperties.defaults(),new RedisCacheStore(template,namespace));delegate=redis;target=redis;
            } else {var local=new LocalManagedCache(registry,properties,clock);delegate=local;target=local;}
            var node=new DurableManagedCache(delegate,target,revisions,registry,properties,clock);nodes.add(node);return node;
        }
        void change(boolean paused,CachePolicy policy,boolean statisticsOnly){
            long tenant=CurrentUser.tenantId();var old=authority.getOrDefault(tenant,CacheRevisionStore.State.initial());
            var next=new CacheRevisionStore.State(old.revision()+(statisticsOnly?0:1),old.controlVersion()+1,
                    old.statsEpoch()+(statisticsOnly?1:0),paused,statisticsOnly?old.override():policy);
            authority.put(tenant,next);a.controlCommitted(REGION,next);
        }
        String read(DurableManagedCache cache,String key,String value){return cache.get(REGION,key,String.class,()->value);}
        @Override public void close(){
            nodes.forEach(DurableManagedCache::close);
            try{if(!templates.isEmpty()){var store=new RedisCacheStore(templates.getFirst(),namespace);templates.getFirst().delete(List.of(store.epochKey(REGION,1),store.epochKey(REGION,2)));}}
            finally{factories.forEach(LettuceConnectionFactory::destroy);}
        }
    }
    private static class MutableClock extends Clock {
        private volatile Instant now=Instant.parse("2026-11-11T00:00:00Z");
        void advance(long seconds){now=now.plusSeconds(seconds);}
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
}
