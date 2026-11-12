package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.internal.service.CacheTaskExecutor;
import com.sma.admin.common.deployment.DeploymentWork;
import com.sma.admin.common.security.*;

class CacheTaskDispatcherTests {
    private static CacheNodeIdentity identity(){return new CacheNodeIdentity(new CacheNodeProperties(null,null,null,false));}
    private static DeploymentWork accepting(){
        var work=mock(DeploymentWork.class);when(work.snapshot()).thenReturn(new DeploymentWork.Snapshot(true,false,0));
        when(work.tryStart()).thenAnswer(call->Optional.of(mock(DeploymentWork.Permit.class)));return work;
    }
    @Test void disabledDispatcherDoesNotDiscoverOrClaimWork(){
        var executor=mock(CacheTaskExecutor.class);var tenants=mock(TenantWorkQuery.class);
        var dispatcher=new CacheTaskDispatcher(executor,tenants,new CacheTaskDispatchProperties(false,null,null,null),identity(),accepting());
        try{dispatcher.tick();verifyNoInteractions(executor,tenants);}finally{dispatcher.close();}
    }
    @Test void slowDispatchDoesNotBlockTheSharedSchedulerOrBuildAnUnboundedQueue() throws Exception {
        var executor=mock(CacheTaskExecutor.class);var tenants=mock(TenantWorkQuery.class);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var claimed=new CountDownLatch(1);
        when(tenants.page(0,1)).thenAnswer(call->{entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("test timeout");return List.of(new TenantWorkQuery.Entry(97200,false));});
        when(executor.claim(anyString())).thenAnswer(call->{assertThat(ActorContext.require().kind()).isEqualTo(ActorRef.Kind.SYSTEM_JOB);assertThat(ActorContext.tenantId()).isEqualTo(97200);claimed.countDown();return Optional.empty();});
        var dispatcher=new CacheTaskDispatcher(executor,tenants,new CacheTaskDispatchProperties(true,1,1,null),identity(),accepting());
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
    @Test void unqualifiedSchedulerDoesNotDiscoverClaimOrReserveWork(){
        var executor=mock(CacheTaskExecutor.class);var tenants=mock(TenantWorkQuery.class);var work=mock(DeploymentWork.class);
        when(work.snapshot()).thenReturn(new DeploymentWork.Snapshot(false,false,0));
        var dispatcher=new CacheTaskDispatcher(executor,tenants,new CacheTaskDispatchProperties(true,1,1,null),identity(),work);
        try{dispatcher.tick();verifyNoInteractions(executor,tenants);verify(work,never()).tryStart();}finally{dispatcher.close();}
    }
    @Test void callbackQueuedBeforeDrainMustRecheckBeforeAnyDirectoryOrClaim()throws Exception{
        var executor=mock(CacheTaskExecutor.class);var tenants=mock(TenantWorkQuery.class);var work=accepting();
        var occupied=new CountDownLatch(1);var release=new CountDownLatch(1);
        var pool=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1));
        pool.execute(()->{occupied.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        var dispatcher=new CacheTaskDispatcher(executor,tenants,new CacheTaskDispatchProperties(true,1,1,null),identity(),work,pool);
        try{
            assertThat(occupied.await(2,TimeUnit.SECONDS)).isTrue();dispatcher.tick();
            verify(work,never()).tryStart(); // Queueing is not work admission.
            when(work.tryStart()).thenReturn(Optional.empty());release.countDown();pool.shutdown();
            assertThat(pool.awaitTermination(3,TimeUnit.SECONDS)).isTrue();
            verifyNoInteractions(executor,tenants);verify(work).tryStart();
        }finally{release.countDown();dispatcher.close();}
    }
    @Test void qualificationLostDuringDiscoveryDoesNotClaimAndRecoveryRevisitsTheTenant(){
        var executor=mock(CacheTaskExecutor.class);var tenants=mock(TenantWorkQuery.class);var work=accepting();
        var ready=new AtomicBoolean(true);var discovered=new AtomicBoolean();
        when(work.snapshot()).thenAnswer(call->new DeploymentWork.Snapshot(ready.get(),false,0));
        when(work.tryStart()).thenAnswer(call->ready.get()?Optional.of(mock(DeploymentWork.Permit.class)):Optional.empty());
        when(tenants.page(0,1)).thenAnswer(call->{if(discovered.compareAndSet(false,true))ready.set(false);return List.of(new TenantWorkQuery.Entry(97200,true));});
        when(executor.claim(anyString())).thenReturn(Optional.empty());
        var dispatcher=new CacheTaskDispatcher(executor,tenants,new CacheTaskDispatchProperties(true,1,1,null),identity(),work);
        try{
            dispatcher.tick();await().atMost(Duration.ofSeconds(3)).untilAsserted(()->verify(work,times(2)).tryStart());verifyNoInteractions(executor);
            ready.set(true);await().atMost(Duration.ofSeconds(3)).untilAsserted(()->{dispatcher.tick();verify(executor,atLeastOnce()).claim(anyString());});
            verify(tenants,atLeast(2)).page(0,1);
        }finally{dispatcher.close();}
    }
    @Test void admittedExecutionKeepsItsPermitUntilReturnAndDoesNotClaimAnotherItem()throws Exception{
        var executor=mock(CacheTaskExecutor.class);var tenants=mock(TenantWorkQuery.class);var work=accepting();
        var discovery=mock(DeploymentWork.Permit.class);var execution=mock(DeploymentWork.Permit.class);
        when(work.tryStart()).thenReturn(Optional.of(discovery),Optional.of(execution),Optional.empty());
        when(tenants.page(0,1)).thenReturn(List.of(new TenantWorkQuery.Entry(97200,true)));
        var lease=new CacheTaskExecutor.Lease(UUID.randomUUID().toString(),1,identity().processId());when(executor.claim(anyString())).thenReturn(Optional.of(lease));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(executor.executeOne(lease)).thenAnswer(call->{entered.countDown();assertThat(release.await(3,TimeUnit.SECONDS)).isTrue();return true;});
        var dispatcher=new CacheTaskDispatcher(executor,tenants,new CacheTaskDispatchProperties(true,1,4,null),identity(),work);
        try{
            dispatcher.tick();assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();verify(discovery).close();verify(execution,never()).close();
            when(work.snapshot()).thenReturn(new DeploymentWork.Snapshot(false,true,1));dispatcher.tick();
            release.countDown();await().atMost(Duration.ofSeconds(3)).untilAsserted(()->verify(execution).close());
            verify(executor,times(1)).claim(anyString());verify(executor,times(1)).executeOne(lease);
        }finally{release.countDown();dispatcher.close();}
    }
    @Test void uncertainExecutionReleasesLocalTrackingWithoutRetryingOrAlteringTheReceipt(){
        var executor=mock(CacheTaskExecutor.class);var tenants=mock(TenantWorkQuery.class);var work=accepting();
        var discovery=mock(DeploymentWork.Permit.class);var execution=mock(DeploymentWork.Permit.class);
        when(work.tryStart()).thenReturn(Optional.of(discovery),Optional.of(execution));
        when(tenants.page(0,1)).thenReturn(List.of(new TenantWorkQuery.Entry(97200,true)));
        var lease=new CacheTaskExecutor.Lease(UUID.randomUUID().toString(),1,identity().processId());when(executor.claim(anyString())).thenReturn(Optional.of(lease));
        when(executor.executeOne(lease)).thenThrow(new IllegalStateException("uncertain native result"));
        var dispatcher=new CacheTaskDispatcher(executor,tenants,new CacheTaskDispatchProperties(true,1,4,null),identity(),work);
        try{
            dispatcher.tick();await().atMost(Duration.ofSeconds(3)).untilAsserted(()->verify(execution).close());
            verify(executor,times(1)).claim(anyString());verify(executor,times(1)).executeOne(lease);verifyNoMoreInteractions(executor);
        }finally{dispatcher.close();}
    }
    @Test void httpCannotLaunderAHostDispatchThroughTheExecutor(){
        var executor=mock(CacheTaskExecutor.class);var tenants=mock(TenantWorkQuery.class);var work=accepting();
        var dispatcher=new CacheTaskDispatcher(executor,tenants,new CacheTaskDispatchProperties(true,1,1,null),identity(),work);
        var previous=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        try{
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(new org.springframework.mock.web.MockHttpServletRequest()));
            assertThatThrownBy(dispatcher::tick).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            verifyNoInteractions(executor,tenants,work);
        }finally{org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(previous);dispatcher.close();}
    }
}
