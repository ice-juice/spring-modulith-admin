package com.sma.admin.defense.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.*;
import com.sma.admin.common.deployment.*;
import com.sma.admin.common.security.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class SecurityRetentionTests {
    private static final InheritableThreadLocal<String> MARKER=new InheritableThreadLocal<>();
    private DefenseMaintenanceProperties settings(int batch){return new DefenseMaintenanceProperties(true,Duration.ofSeconds(1),batch);}
    static class Admission implements DeploymentWork {
        volatile boolean accepting=true;int denyAt=-1;final AtomicInteger calls=new AtomicInteger(),active=new AtomicInteger();
        @Override public Optional<Permit> tryStart(){if(!accepting||calls.incrementAndGet()==denyAt)return Optional.empty();active.incrementAndGet();var closed=new AtomicBoolean();return Optional.of(()->{if(closed.compareAndSet(false,true))active.decrementAndGet();});}
        @Override public Snapshot snapshot(){return new Snapshot(accepting,!accepting,active.get());}
    }
    private List<TenantWorkQuery.Entry> entries(){return List.of(new TenantWorkQuery.Entry(1,true),new TenantWorkQuery.Entry(2,false),new TenantWorkQuery.Entry(3,true));}
    @Test void boundedSweepIncludesPausedTenantAndDenialDoesNotSkipUnprocessedEntry(){
        var admission=new Admission();admission.denyAt=4;var cursors=new ArrayList<Long>();var cleaned=new ArrayList<Long>();
        TenantWorkQuery directory=(after,limit)->{assertThat(admission.active).hasValue(1);cursors.add(after);return entries().stream().filter(e->e.tenantId()>after).limit(limit).toList();};
        var cycle=new SecurityRetentionCycle(settings(2),admission,directory,()->0,id->{assertThat(admission.active).hasValue(1);cleaned.add(id);return 1;});
        cycle.cycle();assertThat(cleaned).containsExactly(1L);assertThat(admission.active).hasValue(0);
        admission.denyAt=-1;cycle.cycle();assertThat(cleaned).containsExactly(1L,2L,3L);
        cycle.cycle();cycle.cycle();assertThat(cursors).containsExactly(0L,1L,3L,0L);assertThat(cleaned).containsExactly(1L,2L,3L,1L,2L);
        assertThat(cycle.denied.sum()).isEqualTo(1);assertThat(cycle.tenantDeleted.sum()).isEqualTo(5);assertThat(admission.active).hasValue(0);
    }
    @Test void platformOrTenantFailureDoesNotStarveOtherTenantsAndIsRetriedOnLaterSweep(){
        var admission=new Admission();var attempted=new ArrayList<Long>();var pages=new ArrayList<Long>();
        TenantWorkQuery directory=(after,limit)->{pages.add(after);return entries().stream().filter(e->e.tenantId()>after).limit(limit).toList();};
        var cycle=new SecurityRetentionCycle(settings(3),admission,directory,()->{throw new IllegalStateException("platform failure");},id->{attempted.add(id);if(id==1)throw new IllegalStateException("tenant failure");return 2;});
        cycle.cycle();cycle.cycle();cycle.cycle();
        assertThat(pages).containsExactly(0L,3L,0L);assertThat(attempted).containsExactly(1L,2L,3L,1L,2L,3L);
        assertThat(cycle.failures.sum()).isEqualTo(5);assertThat(cycle.tenantDeleted.sum()).isEqualTo(8);assertThat(admission.active).hasValue(0);
    }
    @Test void invalidOrUnavailableDirectoryNeverAdvancesCursorOrStartsTenantCleanup(){
        var admission=new Admission();var directory=mock(TenantWorkQuery.class);var cleaned=new AtomicInteger();
        when(directory.page(0,2)).thenReturn(List.of(new TenantWorkQuery.Entry(2,true),new TenantWorkQuery.Entry(1,true))).thenThrow(new IllegalStateException("directory unavailable"));
        var cycle=new SecurityRetentionCycle(settings(2),admission,directory,()->0,id->cleaned.incrementAndGet());
        cycle.cycle();cycle.cycle();verify(directory,times(2)).page(0,2);assertThat(cleaned).hasValue(0);assertThat(cycle.failures.sum()).isEqualTo(2);assertThat(admission.active).hasValue(0);
    }
    @Test void queuedWorkReservesNoPermitAndRechecksAdmissionWithoutAccumulatingCallbacks(){
        var admission=new Admission();var directory=mock(TenantWorkQuery.class);var calls=new AtomicInteger();var executor=new QueuedExecutor();
        when(directory.page(0,1)).thenReturn(List.of());var worker=new SecurityRetentionCycle(settings(1),admission,directory,()->calls.incrementAndGet(),id->0);
        var service=new SecurityRetentionService(settings(1),admission,worker,executor,new SimpleMeterRegistry());
        try{
            for(int i=0;i<10;i++)service.tick();assertThat(executor.tasks).hasSize(1);assertThat(admission.active).hasValue(0);assertThat(calls).hasValue(0);
            admission.accepting=false;executor.runNext();assertThat(calls).hasValue(0);verifyNoInteractions(directory);
            admission.accepting=true;service.tick();executor.runNext();assertThat(calls).hasValue(1);assertThat(admission.active).hasValue(0);
            service.tick();service.close();executor.runNext();assertThat(calls).hasValue(1);assertThat(executor.cancelled).isFalse();
        }finally{service.close();}
    }
    @Test void disabledOrUnavailableAdmissionDoesNotTouchDirectoryOrStore(){
        var admission=mock(DeploymentWork.class);var directory=mock(TenantWorkQuery.class);var store=mock(SecurityEventStore.class);
        var disabled=new SecurityRetentionService(new DefenseMaintenanceProperties(false,null,null),admission,directory,store,new SimpleMeterRegistry());
        try{disabled.tick();verifyNoInteractions(admission,directory,store);}finally{disabled.close();}
        when(admission.snapshot()).thenThrow(new IllegalStateException("unavailable"));
        var active=new SecurityRetentionService(settings(1),admission,directory,store,new SimpleMeterRegistry());
        try{active.tick();verifyNoInteractions(directory,store);}finally{active.close();}
    }
    @Test void httpJobsAndOuterTransactionsCannotStartMaintenance(){
        var admission=new Admission();var directory=mock(TenantWorkQuery.class);var store=mock(SecurityEventStore.class);
        var service=new SecurityRetentionService(settings(1),admission,directory,store,new SimpleMeterRegistry());
        try{
            var prior=RequestContextHolder.getRequestAttributes();RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new org.springframework.mock.web.MockHttpServletRequest()));
            try{assertThatThrownBy(service::tick).isInstanceOf(AccessDeniedException.class);}finally{RequestContextHolder.setRequestAttributes(prior);}
            assertThatThrownBy(()->ActorContext.withinJob(1,"maintenance-fixture",()->{service.tick();return null;})).isInstanceOf(AccessDeniedException.class);
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try{assertThatThrownBy(service::tick).isInstanceOf(IllegalStateException.class);}finally{TransactionSynchronizationManager.setActualTransactionActive(false);}
            verifyNoInteractions(directory,store);assertThat(admission.calls).hasValue(0);
        }finally{service.close();}
    }
    @Test void actualWorkerDoesNotInheritCallerContextAndHoldsPermitUntilStoreReturns()throws Exception{
        var admission=new Admission();var directory=mock(TenantWorkQuery.class);var store=mock(SecurityEventStore.class);var done=new CountDownLatch(1);
        when(directory.page(0,1)).thenReturn(List.of(new TenantWorkQuery.Entry(1,false)));
        when(store.pruneTenant(1)).thenAnswer(call->{assertThat(admission.active).hasValue(1);assertThat(MARKER.get()).isNull();
            assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(RequestContextHolder.getRequestAttributes()).isNull();assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(Thread.currentThread().getName()).startsWith("sma-security-retention-");done.countDown();return 2;});
        MARKER.set("must-not-propagate");var meters=new SimpleMeterRegistry();var service=new SecurityRetentionService(settings(1),admission,directory,store,meters);
        try{service.tick();assertThat(done.await(5,TimeUnit.SECONDS)).isTrue();awaitIdle(admission,meters);assertThat(meters.get("sma.defense.maintenance").tag("result","tenant-deleted").functionCounter().count()).isEqualTo(2);}
        finally{MARKER.remove();service.close();}
    }
    @Test void closeTimeoutDoesNotCancelInFlightCleanupOrReleaseItsPermit()throws Exception{
        var admission=new Admission();var directory=mock(TenantWorkQuery.class);var store=mock(SecurityEventStore.class);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(store.prunePlatform()).thenAnswer(call->{entered.countDown();release.await();return 1;});
        var meters=new SimpleMeterRegistry();var service=new SecurityRetentionService(settings(1),admission,directory,store,meters);
        try{service.tick();assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();service.close();assertThat(admission.active).hasValue(1);verifyNoInteractions(directory);}
        finally{release.countDown();}
        awaitIdle(admission,meters);service.close();verifyNoInteractions(directory);assertThat(meters.get("sma.defense.maintenance").tag("result","platform-deleted").functionCounter().count()).isEqualTo(1);
    }
    private void awaitIdle(Admission admission,SimpleMeterRegistry meters)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while((admission.active.get()!=0||meters.get("sma.defense.maintenance.running").gauge().value()!=0)&&System.nanoTime()<deadline)Thread.sleep(10);
        assertThat(admission.active).hasValue(0);assertThat(meters.get("sma.defense.maintenance.running").gauge().value()).isZero();
    }
    static class QueuedExecutor extends AbstractExecutorService {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>();boolean shutdown,cancelled;
        @Override public void execute(Runnable task){if(shutdown)throw new RejectedExecutionException();tasks.add(task);}
        void runNext(){tasks.remove().run();}
        @Override public void shutdown(){shutdown=true;}
        @Override public List<Runnable> shutdownNow(){cancelled=true;shutdown=true;return List.copyOf(tasks);}
        @Override public boolean isShutdown(){return shutdown;}
        @Override public boolean isTerminated(){return shutdown&&tasks.isEmpty();}
        @Override public boolean awaitTermination(long timeout,TimeUnit unit){return isTerminated();}
    }
}
