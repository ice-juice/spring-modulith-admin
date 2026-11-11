package com.sma.admin.common.internal.cache;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.common.internal.service.CacheNodeLeaseStore;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;

class CacheNodeRuntimeTests {
    @Test void disabledRuntimeDoesNotDiscoverOrMutateAnythingAndRequestContextsCannotTriggerIt(){
        var identity=new CacheNodeIdentity(new CacheNodeProperties("test","a",List.of("a"),false));
        var leases=mock(CacheNodeLeaseStore.class);var processor=mock(CacheConfirmationProcessor.class);var tenants=mock(TenantWorkQuery.class);
        var runtime=new CacheNodeRuntime(identity,leases,processor,tenants);
        try{
            runtime.heartbeatTick();runtime.confirmationTick();assertThat(runtime.status().state()).isEqualTo("DISABLED");
            Users.as(new SmaPrincipal(101,"operator",97501,"admin",null,false,DataAccess.none(),Set.of("monitor:cache:view"),"UTC"),()->{
                assertThatThrownBy(runtime::heartbeatTick).isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(runtime::confirmationTick).isInstanceOf(AccessDeniedException.class);return null;
            });verifyNoInteractions(leases,processor,tenants);
        }finally{runtime.close();}
    }
    @Test void slowConfirmationNeverHoldsTheHeartbeatThreadAndEachQueueStaysBounded() throws Exception {
        var identity=new CacheNodeIdentity(new CacheNodeProperties("test","a",List.of("a"),true));
        var leases=mock(CacheNodeLeaseStore.class);var processor=mock(CacheConfirmationProcessor.class);var tenants=mock(TenantWorkQuery.class);
        var count=new AtomicInteger();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(leases.heartbeat(identity)).thenAnswer(call->{count.incrementAndGet();return new CacheNodeLeaseStore.Lease("a",identity.processId(),1,Instant.now().plusSeconds(30));});
        when(tenants.page(0,16)).thenReturn(List.of(new TenantWorkQuery.Entry(97501,true)));
        when(processor.processTenant(1)).thenAnswer(call->{assertThat(ActorContext.tenantId()).isEqualTo(97501);entered.countDown();release.await(5,TimeUnit.SECONDS);return 1;});
        var runtime=new CacheNodeRuntime(identity,leases,processor,tenants);
        try{
            runtime.heartbeatTick();await().atMost(Duration.ofSeconds(3)).until(()->runtime.status().state().equals("OWNED"));
            runtime.confirmationTick();assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
            for(int i=0;i<30;i++)runtime.confirmationTick();runtime.heartbeatTick();
            await().atMost(Duration.ofSeconds(3)).until(()->count.get()==2);
            verify(processor,times(1)).processTenant(1);
        }finally{release.countDown();runtime.close();}
        assertThat(runtime.status().state()).isEqualTo("STOPPED");
    }
}
