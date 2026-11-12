package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.*;
import com.sma.admin.common.deployment.DeploymentEvidence;
import com.sma.admin.common.internal.cache.*;
import com.sma.admin.common.internal.service.*;

class DeploymentEvidenceRuntimeTests {
    private static final DeploymentEvidence.Challenge CHALLENGE=new DeploymentEvidence.Challenge("a".repeat(64),"b".repeat(64),"c".repeat(64));
    private final CacheNodeIdentity identity=new CacheNodeIdentity(new CacheNodeProperties("evidence-runtime","a",List.of("a","b"),true));
    private final CacheNodeRuntime membership=mock(CacheNodeRuntime.class);
    private final DeploymentEvidenceStore store=mock(DeploymentEvidenceStore.class);
    private final DeploymentEvidenceRegistry registry=mock(DeploymentEvidenceRegistry.class);
    private final AtomicLong clock=new AtomicLong(1);
    private final CacheNodeLeaseStore.Lease lease=new CacheNodeLeaseStore.Lease("a",identity.processId(),1,Instant.now().plusSeconds(30));
    private DeploymentEvidenceRuntime runtime(){
        when(membership.currentLease()).thenReturn(lease);
        when(store.challenge(identity,lease)).thenReturn(new DeploymentEvidenceStore.Ticket(CHALLENGE,identity.processId(),1));
        when(registry.collect(CHALLENGE)).thenReturn(new DeploymentEvidenceRegistry.Snapshot("d".repeat(64),Set.of()));
        when(store.report(eq(identity),any(),any())).thenReturn(new DeploymentEvidenceStore.Verdict(DeploymentEvidenceStore.State.MATCH,Instant.now().plusSeconds(15)));
        return new DeploymentEvidenceRuntime(identity,membership,store,registry,clock::get);
    }
    @Test void requiresFreshLeaseAndMonotonicEvidenceRatherThanWallClockOrOldProcess(){
        var runtime=runtime();try{
            runtime.tick();await().atMost(Duration.ofSeconds(3)).until(()->runtime.status().matches());
            clock.addAndGet(TimeUnit.SECONDS.toNanos(11));assertThat(runtime.status().state()).isEqualTo("STALE");
            runtime.tick();await().atMost(Duration.ofSeconds(3)).until(()->runtime.status().matches());
            when(membership.currentLease()).thenReturn(null);assertThat(runtime.status().state()).isEqualTo("WAITING_LEASE");
            when(membership.currentLease()).thenReturn(new CacheNodeLeaseStore.Lease("a",identity.processId(),2,Instant.now().plusSeconds(30)));
            assertThat(runtime.status().matches()).isFalse();
        }finally{runtime.close();}assertThat(runtime.status().state()).isEqualTo("STOPPED");
    }
    @Test void slowSourceNeverQueuesMoreWorkOrPublishesLateQualification()throws Exception{
        var runtime=runtime();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(registry.collect(CHALLENGE)).thenAnswer(call->{entered.countDown();release.await(3,TimeUnit.SECONDS);return new DeploymentEvidenceRegistry.Snapshot("d".repeat(64),Set.of());});
        try{
            runtime.tick();assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            for(int i=0;i<100;i++)runtime.tick();verify(registry,times(1)).collect(CHALLENGE);
            clock.addAndGet(TimeUnit.SECONDS.toNanos(6));release.countDown();
            await().atMost(Duration.ofSeconds(3)).until(()->runtime.status().state().equals("STALE"));
            verify(store,never()).report(any(),any(),any());
        }finally{release.countDown();runtime.close();}
    }
    @Test void errorsAreRedactedAndHttpCannotTriggerWorker(){
        var runtime=runtime();when(registry.collect(CHALLENGE)).thenThrow(new IllegalStateException("private-key-or-endpoint"));
        try{
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            try{assertThatThrownBy(runtime::tick).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(store,registry);}
            finally{RequestContextHolder.resetRequestAttributes();}
            runtime.tick();await().atMost(Duration.ofSeconds(3)).until(()->runtime.status().state().equals("UNAVAILABLE"));
            assertThat(runtime.status().toString()).doesNotContain("private-key-or-endpoint");
        }finally{runtime.close();}
    }
    @Test void optInRequiresValidatedSchemaAndEmitsOnlySafeHealthStatus(){
        var runner=new ApplicationContextRunner().withUserConfiguration(DeploymentEvidenceConfiguration.class)
                .withBean(CacheNodeIdentity.class,()->identity).withBean(CacheNodeRuntime.class,()->membership)
                .withBean(DeploymentEvidenceStore.class,()->store).withBean(DeploymentEvidenceRegistry.class,()->registry)
                .withBean(SchemaValidationConfiguration.Properties.class,()->new SchemaValidationConfiguration.Properties(SchemaValidationConfiguration.Properties.SchemaMode.VALIDATE));
        runner.run(context->assertThat(context).doesNotHaveBean(DeploymentEvidenceRuntime.class));
        runner.withPropertyValues("sma.deployment.evidence.enabled=true").run(context->{
            assertThat(context).hasNotFailed().hasSingleBean(DeploymentEvidenceRuntime.class);
            var health=context.getBean("deploymentEvidence",HealthIndicator.class).health();
            assertThat(health.getStatus().getCode()).isEqualTo("DOWN");assertThat(health.getDetails()).containsOnlyKeys("state");
        });
        runner.withPropertyValues("sma.deployment.evidence.enabled=true","spring.main.lazy-initialization=true").run(context->assertThat(context).hasFailed());
        var bad=new ApplicationContextRunner().withUserConfiguration(DeploymentEvidenceConfiguration.class)
                .withPropertyValues("sma.deployment.evidence.enabled=true")
                .withBean(CacheNodeIdentity.class,()->identity).withBean(CacheNodeRuntime.class,()->membership)
                .withBean(DeploymentEvidenceStore.class,()->store).withBean(DeploymentEvidenceRegistry.class,()->registry)
                .withBean(SchemaValidationConfiguration.Properties.class,()->new SchemaValidationConfiguration.Properties(null));
        bad.run(context->assertThat(context).hasFailed());
    }
    @Test void disabledMembershipAndSingleNodeManifestCannotEnableEvidence(){
        var factory=new DeploymentEvidenceConfiguration();
        var schema=new SchemaValidationConfiguration.Properties(SchemaValidationConfiguration.Properties.SchemaMode.VALIDATE);
        for(var invalid:List.of(new CacheNodeProperties("test","a",List.of("a","b"),false),
                new CacheNodeProperties("test","a",List.of("a"),true))){
            assertThatThrownBy(()->factory.deploymentEvidenceRuntime(new CacheNodeIdentity(invalid),membership,store,registry,schema,new org.springframework.mock.env.MockEnvironment()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(membership,store,registry);
    }
}
