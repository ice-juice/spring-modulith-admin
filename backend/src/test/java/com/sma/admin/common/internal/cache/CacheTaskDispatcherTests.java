package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.internal.service.CacheTaskExecutor;
import com.sma.admin.common.security.*;

class CacheTaskDispatcherTests {
    @Test void disabledDispatcherDoesNotDiscoverOrClaimWork(){
        var executor=mock(CacheTaskExecutor.class);var tenants=mock(TenantWorkQuery.class);
        var dispatcher=new CacheTaskDispatcher(executor,tenants,new CacheTaskDispatchProperties(false,null,null,null));
        try{dispatcher.tick();verifyNoInteractions(executor,tenants);}finally{dispatcher.close();}
    }
    @Test void slowDispatchDoesNotBlockTheSharedSchedulerOrBuildAnUnboundedQueue() throws Exception {
        var executor=mock(CacheTaskExecutor.class);var tenants=mock(TenantWorkQuery.class);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var claimed=new CountDownLatch(1);
        when(tenants.page(0,1)).thenAnswer(call->{entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("test timeout");return List.of(new TenantWorkQuery.Entry(97200,false));});
        when(executor.claim(anyString())).thenAnswer(call->{assertThat(ActorContext.require().kind()).isEqualTo(ActorRef.Kind.SYSTEM_JOB);assertThat(ActorContext.tenantId()).isEqualTo(97200);claimed.countDown();return Optional.empty();});
        var dispatcher=new CacheTaskDispatcher(executor,tenants,new CacheTaskDispatchProperties(true,1,1,null));
        try{
            dispatcher.tick();assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
            for(int i=0;i<20;i++)dispatcher.tick();verify(tenants,times(1)).page(0,1);
            release.countDown();assertThat(claimed.await(3,TimeUnit.SECONDS)).isTrue();
        }finally{release.countDown();dispatcher.close();}
    }
    @Test void leaseAndBatchSizesHaveHardBounds(){
        assertThat(new CacheTaskDispatchProperties(null,null,null,null).lease()).isEqualTo(Duration.ofSeconds(30));
        assertThatThrownBy(()->new CacheTaskDispatchProperties(true,33,1,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CacheTaskDispatchProperties(true,1,9,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CacheTaskDispatchProperties(true,1,1,Duration.ofSeconds(29))).isInstanceOf(IllegalArgumentException.class);
    }
}
