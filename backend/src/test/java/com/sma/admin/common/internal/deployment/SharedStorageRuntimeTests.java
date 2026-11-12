package com.sma.admin.common.internal.deployment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.awaitility.Awaitility.await;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import com.sma.admin.common.internal.cache.*;
import com.sma.admin.common.internal.service.*;

class SharedStorageRuntimeTests {
    private final CacheNodeIdentity identity=new CacheNodeIdentity(new CacheNodeProperties("runtime-storage","a",List.of("a","b"),true));
    private final CacheNodeRuntime membership=mock(CacheNodeRuntime.class);
    private final DeploymentEvidenceRuntime evidence=mock(DeploymentEvidenceRuntime.class);
    private final SharedStorageStore store=mock(SharedStorageStore.class);
    private final SharedStorageProbe probe=mock(SharedStorageProbe.class);
    private final AtomicLong clock=new AtomicLong(1);
    private final SharedStorageProbe.Offer offer=new SharedStorageProbe.Offer(identity.manifestHash(),"a",identity.processId(),1,Map.of("file.legacy","a".repeat(64)));
    private SharedStorageRuntime runtime(){
        when(membership.currentLease()).thenReturn(new CacheNodeLeaseStore.Lease("a",identity.processId(),1,Instant.now().plusSeconds(30)));
        when(evidence.status()).thenReturn(new DeploymentEvidenceRuntime.Status("MATCH"));when(probe.publish()).thenReturn(offer);
        when(probe.verifyOwn()).thenReturn(true);when(probe.verifyAnchor(offer)).thenReturn(true);
        var report=new SharedStorageStore.Report(offer,1,true,true,"b".repeat(64),List.of(),Instant.now().plusSeconds(15));
        when(store.register(eq(identity),any(),eq(offer))).thenReturn(new SharedStorageStore.View(offer,"b".repeat(64),report,List.of()));
        when(store.observe(eq(identity),any(),eq(1L),anyString(),eq(true),eq(true),anyList())).thenReturn(report);
        return new SharedStorageRuntime(identity,membership,evidence,store,e->probe,clock::get);
    }
    @Test void localQualificationExpiresAndConfigurationOrLeaseLossImmediatelyDenies(){
        var runtime=runtime();try{
            runtime.tick();await().atMost(Duration.ofSeconds(3)).until(()->runtime.status().ready());
            clock.addAndGet(Duration.ofSeconds(11).toNanos());assertThat(runtime.status().state()).isEqualTo("STALE");
            when(evidence.status()).thenReturn(new DeploymentEvidenceRuntime.Status("MISMATCH"));assertThat(runtime.status().state()).isEqualTo("WAITING_CONFIGURATION");
            when(evidence.status()).thenReturn(new DeploymentEvidenceRuntime.Status("MATCH"));when(membership.currentLease()).thenReturn(null);
            assertThat(runtime.status().state()).isEqualTo("WAITING_LEASE");
        }finally{runtime.close();}assertThat(runtime.status().state()).isEqualTo("STOPPED");
    }
    @Test void slowIoDoesNotQueueOrAcceptLateSuccessAndPublishesLocalFailure()throws Exception{
        var runtime=runtime();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new java.util.concurrent.atomic.AtomicInteger();
        when(probe.verifyOwn()).thenAnswer(call->{calls.incrementAndGet();entered.countDown();release.await(5,TimeUnit.SECONDS);return true;});
        try{
            runtime.tick();assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();for(int i=0;i<100;i++)runtime.tick();
            assertThat(calls.get()).isEqualTo(1);clock.addAndGet(Duration.ofSeconds(6).toNanos());release.countDown();
            await().atMost(Duration.ofSeconds(3)).untilAsserted(()->verify(store).observe(eq(identity),any(),eq(1L),anyString(),eq(false),eq(false),eq(List.of())));
            assertThat(runtime.status().ready()).isFalse();verify(store,never()).observe(eq(identity),any(),anyLong(),anyString(),eq(true),eq(true),anyList());
        }finally{release.countDown();runtime.close();}
    }
    @Test void requestCannotStartIoAndDefaultConfigurationDoesNotCreateWorkers(){
        var runtime=runtime();try{
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            try{assertThatThrownBy(runtime::tick).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(probe,store);}
            finally{RequestContextHolder.resetRequestAttributes();}
        }finally{runtime.close();}
        new ApplicationContextRunner().withUserConfiguration(SharedStorageConfiguration.class).run(context->assertThat(context).hasNotFailed().doesNotHaveBean(SharedStorageRuntime.class));
        new ApplicationContextRunner().withUserConfiguration(SharedStorageConfiguration.class).withPropertyValues("sma.deployment.storage.enabled=true")
                .withBean(CacheNodeIdentity.class,()->identity).withBean(CacheNodeRuntime.class,()->membership)
                .withBean(SharedStorageStore.class,()->store).withBean(SharedStorageRegistry.class,()->mock(SharedStorageRegistry.class))
                .run(context->{assertThat(context).hasFailed();assertThat(context.getStartupFailure()).hasStackTraceContaining("DeploymentEvidenceRuntime");});
    }
}
