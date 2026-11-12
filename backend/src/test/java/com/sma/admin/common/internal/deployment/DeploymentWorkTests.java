package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.availability.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.*;
import com.sma.admin.common.deployment.DeploymentWork;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;

class DeploymentWorkTests {
    private final ApplicationContext context=mock(ApplicationContext.class);

    @Test void capacityIsBoundedAndReleaseIsIdempotentWithoutDroppingOtherWork() {
        var work=new DeploymentWorkCoordinator(context,()->true);
        var permits=new ArrayList<DeploymentWork.Permit>();
        try {
            for(int i=0;i<DeploymentWorkCoordinator.MAX_ACTIVE_WORK;i++)permits.add(work.tryStart().orElseThrow());
            assertThat(work.snapshot().activeWork()).isEqualTo(1024);
            assertThat(work.snapshot().accepting()).isFalse();assertThat(work.snapshot().draining()).isFalse();
            assertThat(work.tryStart()).isEmpty();
            permits.getFirst().close();permits.getFirst().close();
            assertThat(work.snapshot().activeWork()).isEqualTo(1023);assertThat(work.snapshot().accepting()).isTrue();
        } finally { permits.forEach(DeploymentWork.Permit::close); }
        assertThat(work.snapshot().activeWork()).isZero();
    }

    @Test void qualificationCanRecoverButDrainCannotAndDoesNotRevokeAdmittedWork() {
        var qualified=new AtomicBoolean();var work=new DeploymentWorkCoordinator(context,qualified::get);
        assertThat(work.tryStart()).isEmpty();qualified.set(true);
        var admitted=work.tryStart().orElseThrow();qualified.set(false);
        assertThat(work.tryStart()).isEmpty();assertThat(work.snapshot().activeWork()).isOne();
        qualified.set(true);assertThat(work.snapshot().accepting()).isTrue();
        assertThat(work.beginDrain()).isTrue();assertThat(work.beginDrain()).isFalse();
        assertThat(work.tryStart()).isEmpty();assertThat(work.snapshot()).isEqualTo(new DeploymentWork.Snapshot(false,true,1));
        admitted.close();assertThat(work.snapshot()).isEqualTo(new DeploymentWork.Snapshot(false,true,0));
        var unavailable=new DeploymentWorkCoordinator(context,()->{throw new IllegalStateException("private-key-path");});
        assertThat(unavailable.tryStart()).isEmpty();assertThat(unavailable.snapshot().toString()).doesNotContain("private-key-path");
    }

    @Test void drainAndAnInProgressAdmissionHaveOneLinearizationPoint() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var work=new DeploymentWorkCoordinator(context,()->{
            entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("Admission did not release");}
            catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}return true;
        });
        var pool=Executors.newFixedThreadPool(2);
        try {
            var acquiring=pool.submit(work::tryStart);assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            var draining=pool.submit(work::beginDrain);
            assertThatThrownBy(()->draining.get(100,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();var permit=acquiring.get(2,TimeUnit.SECONDS).orElseThrow();
            assertThat(draining.get(2,TimeUnit.SECONDS)).isTrue();
            assertThat(work.tryStart()).isEmpty();assertThat(work.snapshot().activeWork()).isOne();
            var closes=new ArrayList<Future<?>>();for(int i=0;i<16;i++)closes.add(pool.submit(permit::close));
            for(var close:closes)close.get(2,TimeUnit.SECONDS);
            assertThat(work.snapshot().activeWork()).isZero();
        } finally { release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(3,TimeUnit.SECONDS)).isTrue(); }
    }

    @Test void workersMayUseTrustedJobScopeButHttpOrdinaryIdentityAndTransactionsCannotAcquire() {
        var work=new DeploymentWorkCoordinator(context,()->true);
        ActorContext.withinJob(77,"trusted-dispatch",()->{
            try(var permit=work.tryStart().orElseThrow()){assertThat(work.snapshot().activeWork()).isOne();}
            assertThatThrownBy(work::beginDrain).isInstanceOf(AccessDeniedException.class);return null;
        });
        Users.as(Users.principal(7,77,DataAccess.unrestricted()),()->{
            assertThatThrownBy(work::tryStart).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(work::beginDrain).isInstanceOf(AccessDeniedException.class);return null;
        });
        var previous=RequestContextHolder.getRequestAttributes();
        try {
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            assertThatThrownBy(work::tryStart).isInstanceOf(AccessDeniedException.class);
            ActorContext.withinJob(77,"http-cannot-become-worker",()->{
                assertThatThrownBy(work::tryStart).isInstanceOf(AccessDeniedException.class);return null;
            });
            assertThat(work.snapshot().activeWork()).isZero();
        } finally { RequestContextHolder.setRequestAttributes(previous); }
        try(var request=TenantEntryScope.beginRequest()) {
            assertThatThrownBy(work::tryStart).isInstanceOf(AccessDeniedException.class);
        }
        boolean active=TransactionSynchronizationManager.isActualTransactionActive();
        try {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            assertThatThrownBy(work::tryStart).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(work::beginDrain).isInstanceOf(IllegalStateException.class);
        } finally { TransactionSynchronizationManager.setActualTransactionActive(active); }
        assertThat(work.snapshot()).isEqualTo(new DeploymentWork.Snapshot(true,false,0));
    }

    @Test void ownContextCloseStopsAdmissionThroughTheActualClassProxyAndRetainsInFlightCount() {
        var work=new DeploymentWorkCoordinator(context,()->true);var factory=new ProxyFactory(work);
        factory.setProxyTargetClass(true);factory.addAdvice((org.aopalliance.intercept.MethodInterceptor)call->call.proceed());
        var proxy=(DeploymentWorkCoordinator)factory.getProxy();var permit=proxy.tryStart().orElseThrow();
        proxy.onApplicationEvent(new ContextClosedEvent(mock(ApplicationContext.class)));
        assertThat(proxy.snapshot().accepting()).isTrue();
        proxy.onApplicationEvent(new ContextClosedEvent(context));
        assertThat(proxy.snapshot()).isEqualTo(new DeploymentWork.Snapshot(false,true,1));
        permit.close();assertThat(work.snapshot().activeWork()).isZero();
    }

    @Test void springBindingRequiresAvailabilityAndOptionalGateWithoutCircularConstruction() {
        var availability=mock(ApplicationAvailability.class);
        var runner=new ApplicationContextRunner().withUserConfiguration(DeploymentWorkConfiguration.class)
                .withBean(ApplicationAvailability.class,()->availability);
        runner.run(c->{assertThat(c).hasNotFailed().hasSingleBean(DeploymentWork.class);assertThat(c.getBean(DeploymentWork.class).tryStart()).isEmpty();});
        when(availability.getLivenessState()).thenReturn(LivenessState.CORRECT);
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        var captured=new AtomicReference<DeploymentWork>();
        runner.run(c->{var work=c.getBean(DeploymentWork.class);captured.set(work);try(var permit=work.tryStart().orElseThrow()){assertThat(work.snapshot().activeWork()).isOne();}});
        assertThat(captured.get().snapshot().draining()).isTrue();
        runner.withPropertyValues("sma.deployment.traffic.enabled=true").run(c->assertThat(c.getBean(DeploymentWork.class).tryStart()).isEmpty());
        var gate=mock(DeploymentTrafficGate.class);when(gate.status()).thenReturn(new DeploymentTrafficGate.Status("ACCEPTING"));
        runner.withPropertyValues("sma.deployment.traffic.enabled=true").withBean(DeploymentTrafficGate.class,()->gate).run(c->{
            var work=c.getBean(DeploymentWork.class);try(var permit=work.tryStart().orElseThrow()){
                when(gate.status()).thenReturn(new DeploymentTrafficGate.Status("WAITING_STORAGE"));
                assertThat(work.tryStart()).isEmpty();assertThat(work.snapshot().activeWork()).isOne();
            }
        });
    }

    @Test void workerAndHttpAdmissionShareTheSameIrreversibleDrainState() {
        var availability=mock(ApplicationAvailability.class);when(availability.getLivenessState()).thenReturn(LivenessState.CORRECT);
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        var evidence=mock(DeploymentEvidenceRuntime.class);when(evidence.status()).thenReturn(new DeploymentEvidenceRuntime.Status("MATCH"));
        var storage=mock(SharedStorageRuntime.class);when(storage.status()).thenReturn(new SharedStorageRuntime.Status("READY"));
        var gateRef=new AtomicReference<DeploymentTrafficGate>();
        var work=new DeploymentWorkCoordinator(context,()->gateRef.get().status().accepts());
        var gate=new DeploymentTrafficGate(availability,evidence,storage,context,work);gateRef.set(gate);
        var permit=work.tryStart().orElseThrow();assertThat(gate.status().accepts()).isTrue();
        assertThat(work.beginDrain()).isTrue();assertThat(gate.status().state()).isEqualTo("DRAINING");
        assertThat(work.tryStart()).isEmpty();assertThat(work.snapshot().activeWork()).isOne();
        permit.close();assertThat(work.snapshot().activeWork()).isZero();
    }
}
