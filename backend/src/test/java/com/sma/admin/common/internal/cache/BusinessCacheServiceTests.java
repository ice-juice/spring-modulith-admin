package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.request.*;
import com.sma.admin.common.persistence.DataSourceIdentity;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class BusinessCacheServiceTests {
    static CacheDescriptor descriptor(){return new CacheDescriptor("shop.items","shop","shop.cache.items","catalog","items",
            CacheDescriptor.Kind.DATA,CacheDescriptor.Scope.TENANT,1,new CachePolicy(Duration.ofMinutes(1),Duration.ZERO,100,65536,4096),
            Set.of(CacheDescriptor.Operation.CLEAR),Set.of("save"),"shop:cache:manage");}
    static BusinessCacheRegistry directory(){return new BusinessCacheRegistry(List.of(()->List.of(new BusinessCacheCatalog.Region("shop",descriptor()))),
            mock(CacheRegistrationOwnership.class),new CacheRegistry(List.of()));}
    static <T>T actor(long tenant,Supplier<T> action){return Users.as(Users.principal(7,tenant,DataAccess.unrestricted()),action);}
    @SuppressWarnings("unchecked") static ObjectProvider<StringRedisTemplate> redis(){return mock(ObjectProvider.class);}
    private final BusinessLedger ledger=mock(BusinessLedger.class);
    private final AtomicReference<DataSourceIdentity> source=new AtomicReference<>(new DataSourceIdentity("business-a",1));
    private final AtomicLong revision=new AtomicLong();
    private final AtomicInteger closed=new AtomicInteger();
    private final AtomicInteger queried=new AtomicInteger();
    private final AtomicBoolean inQuery=new AtomicBoolean();
    private BusinessCacheService service(CacheProperties.Provider provider,int total,int perTenant) {
        return service(new CacheProperties(provider,"business-test",null,null,null,0d,Map.of()),total,perTenant);
    }
    private BusinessCacheService service(CacheProperties properties,int total,int perTenant) {
        when(ledger.open("shop")).thenAnswer(call->{
            var pinned=source.get();var session=mock(BusinessLedger.Session.class);
            when(session.resourceIdentity()).thenReturn(pinned);
            when(session.authoritativeRevision("shop.items")).thenAnswer(i->revision.get());
            when(session.query(any())).thenAnswer(i->{
                queried.incrementAndGet();inQuery.set(true);try{return ((Supplier<?>)i.getArgument(0)).get();}finally{inQuery.set(false);}
            });
            doAnswer(i->{closed.incrementAndGet();return null;}).when(session).close();return session;
        });
        return new BusinessCacheService(directory(),ledger,properties,
                new CacheResilienceProperties(total,perTenant,null,null,null),redis());
    }
    @Test void sameKeyIsIsolatedByTrustedTenantPhysicalSourceRevisionAndCommittedBusinessRevision(){
        var cache=service(CacheProperties.Provider.LOCAL,8,2);
        actor(11,()->{
            assertThat(cache.get("shop.items","one",String.class,()->{assertThat(inQuery).isTrue();return "original";})).isEqualTo("original");
            assertThat(cache.get("shop.items","one",String.class,()->"unexpected")).isEqualTo("original");
            revision.incrementAndGet();assertThat(cache.get("shop.items","one",String.class,()->"changed")).isEqualTo("changed");
            source.set(new DataSourceIdentity("business-b",1));revision.set(0);
            assertThat(cache.get("shop.items","one",String.class,()->"second")).isEqualTo("second");
            source.set(new DataSourceIdentity("business-b",2));
            assertThat(cache.get("shop.items","one",String.class,()->"new-revision")).isEqualTo("new-revision");return null;
        });
        assertThat(actor(12,()->cache.get("shop.items","one",String.class,()->"foreign"))).isEqualTo("foreign");
        assertThat(queried).hasValue(5);assertThat(closed).hasValue(6);
    }
    @Test void missingRegistrationAnonymousOuterTransactionAndUnknownSourceFailBeforeLoading(){
        var cache=service(CacheProperties.Provider.LOCAL,8,2);
        assertThatThrownBy(()->cache.get("shop.items","one",String.class,()->"bad")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        actor(11,()->{
            assertThatThrownBy(()->cache.get("auth.sessions","one",String.class,()->"bad")).isInstanceOf(IllegalArgumentException.class);
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try{assertThatThrownBy(()->cache.get("shop.items","one",String.class,()->"bad")).isInstanceOf(IllegalStateException.class);}
            finally{TransactionSynchronizationManager.setActualTransactionActive(false);}
            source.set(DataSourceIdentity.UNKNOWN);
            assertThatThrownBy(()->cache.get("shop.items","one",String.class,()->"bad")).isInstanceOf(IllegalStateException.class);return null;
        });
        assertThat(queried).hasValue(0);assertThat(closed).hasValue(1);
    }
    @Test void disabledCacheStillPinsQueriesAndReleasesAfterLoaderFailure(){
        var cache=service(CacheProperties.Provider.NONE,2,1);
        actor(11,()->{
            assertThatThrownBy(()->cache.get("shop.items","one",String.class,()->{throw new IllegalArgumentException("loader");})).hasMessage("loader");
            assertThat(cache.get("shop.items","one",String.class,()->"again")).isEqualTo("again");
            assertThat(cache.get("shop.items","one",String.class,()->"latest")).isEqualTo("latest");return null;
        });assertThat(closed).hasValue(3);
    }
    @Test void oneTenantCannotOccupyAllSourceWorkAndRebindingDoesNotCloseAnInflightSession()throws Exception{
        var cache=service(CacheProperties.Provider.LOCAL,2,1);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var executor=Executors.newSingleThreadExecutor(Thread.ofPlatform().inheritInheritableThreadLocals(false).factory());
        try{
            var first=executor.submit(()->actor(11,()->cache.get("shop.items","blocking",String.class,()->{
                entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("not released");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}return "old";
            })));
            assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
            actor(11,()->{assertThatThrownBy(()->cache.get("shop.items","other",String.class,()->"bad")).isInstanceOf(CacheUnavailableException.class);return null;});
            source.set(new DataSourceIdentity("business-b",1));
            assertThat(actor(12,()->cache.get("shop.items","blocking",String.class,()->"healthy"))).isEqualTo("healthy");
            assertThat(closed).hasValue(1);release.countDown();assertThat(first.get(3,TimeUnit.SECONDS)).isEqualTo("old");
            assertThat(actor(11,()->cache.get("shop.items","blocking",String.class,()->"new"))).isEqualTo("new");
        }finally{release.countDown();executor.shutdownNow();assertThat(executor.awaitTermination(3,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void businessCatalogRejectsProtectedRegionsAndUnrelatedGroups(){
        assertThatThrownBy(()->new BusinessCacheCatalog.Region("other",descriptor())).isInstanceOf(IllegalArgumentException.class);
        var protectedRegion=new CacheDescriptor("shop.ticket","shop","shop.cache.ticket","security","ticket",CacheDescriptor.Kind.PROTECTED,
                CacheDescriptor.Scope.TENANT,1,null,Set.of(),Set.of("consume"),"shop:cache:manage");
        assertThatThrownBy(()->new BusinessCacheCatalog.Region("shop",protectedRegion)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void oneScopeBudgetCannotBeMultipliedAcrossPhysicalSources()throws Exception{
        var cache=service(new CacheProperties(CacheProperties.Provider.LOCAL,"business-test",null,null,1,0d,Map.of()),4,2);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var executor=Executors.newSingleThreadExecutor(Thread.ofPlatform().inheritInheritableThreadLocals(false).factory());
        try{
            var first=executor.submit(()->actor(11,()->cache.get("shop.items","one",String.class,()->{
                entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("not released");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}return "first";
            })));
            assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
            source.set(new DataSourceIdentity("business-b",1));
            actor(12,()->{assertThatThrownBy(()->cache.get("shop.items","one",String.class,()->"overflow"))
                    .isInstanceOf(CacheUnavailableException.class);return null;});
            release.countDown();assertThat(first.get(3,TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(actor(12,()->cache.get("shop.items","one",String.class,()->"second"))).isEqualTo("second");
            assertThat(closed).hasValue(3);
        }finally{release.countDown();executor.shutdownNow();assertThat(executor.awaitTermination(3,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void businessTransactionCannotWriteThePlatformCacheRevision(){
        var revisions=mock(com.sma.admin.common.internal.service.CacheRevisionStore.class);
        var properties=new CacheProperties(null,null,null,null,null,null,Map.of());
        var cache=new DurableManagedCache(mock(ManagedCache.class),mock(CacheInvalidationTarget.class),revisions,
                directory().descriptors(),properties,java.time.Clock.systemUTC());
        com.sma.admin.common.internal.persistence.BusinessRouteScope.within(mock(javax.sql.DataSource.class),()->{
            assertThatThrownBy(()->cache.invalidate("shop.items")).isInstanceOf(IllegalStateException.class).hasMessageContaining("BusinessLedger");return null;
        });verifyNoInteractions(revisions);
    }
}
