package com.sma.admin.mail.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import com.sma.admin.common.deployment.DeploymentWork;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.security.*;
import com.sma.admin.mail.internal.persistence.MailProfileRevisionEntity;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MailDispatcherTests {
    private final Admission admission=new Admission();
    static final class Admission implements DeploymentWork {
        volatile boolean accepting=true;
        final AtomicInteger active=new AtomicInteger();
        public Optional<Permit> tryStart(){
            if(!accepting)return Optional.empty();active.incrementAndGet();var closed=new AtomicBoolean();
            return Optional.of(()->{if(closed.compareAndSet(false,true))active.decrementAndGet();});
        }
        public Snapshot snapshot(){return new Snapshot(accepting,!accepting,active.get());}
    }
    @Test void invalidationAfterPreparationCancelsWithoutInvokingTransport(){
        var work=mock(MailWorkService.class);var sender=mock(SmtpMailSender.class);var directory=mock(TenantEntryQuery.class);
        when(directory.securityState(951)).thenReturn(Optional.of(new TenantEntryQuery.SecurityState(951,1,true,true)));
        var claim=new MailWorkService.Claim(951,41,1,"worker");
        when(work.claim(anyString())).thenReturn(Optional.of(claim));
        when(work.begin(claim)).thenReturn(Optional.of(new MailWorkService.Prepared(claim,"id",new MailPayload("to@example.test","subject","body",false),new MailProfileRevisionEntity())));
        when(work.stillValid(claim)).thenReturn(false);
        var cycle=new MailDispatchCycle(work,sender,directory,new MailDispatchProperties(true,1,1,10),admission);assertThat(cycle.runTenant(951)).isZero();
        verifyNoInteractions(sender);verify(work).finish(claim,MailWorkService.Outcome.CANCELLED);
    }
    @Test void disabledDispatcherDoesNotDiscoverTenantsOrSendAnything(){
        var directory=mock(TenantWorkQuery.class);var cycle=mock(MailDispatchCycle.class);
        var dispatcher=new MailDispatcher(directory,cycle,new MailDispatchProperties(false,1,1,1),5000,admission);
        try{dispatcher.poll();verifyNoInteractions(directory,cycle);}finally{dispatcher.close();}
    }
    @Test void newDispatcherRediscoversPersistentTenantsAndRestoresWorkerScope()throws Exception{
        var directory=mock(TenantWorkQuery.class);var cycle=mock(MailDispatchCycle.class);
        when(directory.page(0,10)).thenReturn(List.of(new TenantWorkQuery.Entry(951,true),new TenantWorkQuery.Entry(952,false)));
        var calls=new CountDownLatch(4);when(cycle.runTenant(anyLong())).thenAnswer(i->{calls.countDown();return 0;});
        for(int i=0;i<2;i++){
            var dispatcher=new MailDispatcher(directory,cycle,new MailDispatchProperties(true,1,1,10),5000,admission);
            try{dispatcher.poll();long expected=2L*(1-i);long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
                while(calls.getCount()>expected && System.nanoTime()<end)Thread.onSpinWait();
                assertThat(calls.getCount()).isLessThanOrEqualTo(expected);
            }finally{dispatcher.close();}
        }
        assertThat(calls.await(1,TimeUnit.SECONDS)).isTrue();verify(directory,times(2)).page(0,10);
        verify(cycle,times(2)).runTenant(951);verify(cycle,times(2)).runTenant(952);
    }
    @Test void cycleUsesSystemActorNoDatabaseTransactionAndDoesNotSendForSuspendedTenant(){
        var work=mock(MailWorkService.class);var sender=mock(SmtpMailSender.class);var directory=mock(TenantEntryQuery.class);
        when(directory.securityState(951)).thenReturn(Optional.of(new TenantEntryQuery.SecurityState(951,1,true,true)));
        var claim=new MailWorkService.Claim(951,41,1,"worker");
        var prepared=new MailWorkService.Prepared(claim,"id@example.test",new MailPayload("to@example.test","s","b",false),new MailProfileRevisionEntity());
        when(work.claim(anyString())).thenReturn(Optional.of(claim));when(work.begin(claim)).thenReturn(Optional.of(prepared));
        when(work.stillValid(claim)).thenReturn(true);
        when(sender.send(eq(prepared),any())).thenAnswer(i->{
            assertThat(ActorContext.require().kind()).isEqualTo(ActorRef.Kind.SYSTEM_JOB);assertThat(ActorContext.tenantId()).isEqualTo(951);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();i.getArgument(1,com.sma.admin.common.deployment.DeploymentWork.Permit.class).close();return MailWorkService.Outcome.ACCEPTED;
        });when(work.finish(claim,MailWorkService.Outcome.ACCEPTED)).thenReturn(true);
        var cycle=new MailDispatchCycle(work,sender,directory,new MailDispatchProperties(true,1,1,10),admission);
        assertThat(cycle.runTenant(951)).isEqualTo(1);
        assertThatThrownBy(ActorContext::require).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        when(directory.securityState(951)).thenReturn(Optional.of(new TenantEntryQuery.SecurityState(951,2,false,false)));
        assertThat(cycle.runTenant(951)).isZero();verify(sender,times(1)).send(any(),any());verify(work,times(2)).recover();
    }
    @Test void lostFinishCommitDoesNotCallSenderAgainAndOuterContextIsRestored(){
        var work=mock(MailWorkService.class);var sender=mock(SmtpMailSender.class);var directory=mock(TenantEntryQuery.class);
        when(directory.securityState(951)).thenReturn(Optional.of(new TenantEntryQuery.SecurityState(951,1,true,true)));
        var claim=new MailWorkService.Claim(951,41,1,"worker");
        var prepared=new MailWorkService.Prepared(claim,"id@example.test",new MailPayload("to@example.test","s","b",false),new MailProfileRevisionEntity());
        when(work.claim(anyString())).thenReturn(Optional.of(claim));when(work.begin(claim)).thenReturn(Optional.of(prepared));
        when(work.stillValid(claim)).thenReturn(true);
        when(sender.send(eq(prepared),any())).thenAnswer(i->{i.getArgument(1,com.sma.admin.common.deployment.DeploymentWork.Permit.class).close();return MailWorkService.Outcome.ACCEPTED;});
        when(work.finish(claim,MailWorkService.Outcome.ACCEPTED)).thenThrow(new IllegalStateException("database unavailable"));
        var cycle=new MailDispatchCycle(work,sender,directory,new MailDispatchProperties(true,1,10,10),admission);
        assertThatThrownBy(()->cycle.runTenant(951)).isInstanceOf(IllegalStateException.class);
        verify(sender,times(1)).send(any(),any());assertThatThrownBy(ActorContext::require).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(admission.active).hasValue(0);
    }

    @Test void unavailableDoesNotDiscoverRecoverOrClaimAndCanRecover(){
        var directory=mock(TenantWorkQuery.class);var work=mock(MailWorkService.class);var sender=mock(SmtpMailSender.class);
        var cycle=new MailDispatchCycle(work,sender,mock(TenantEntryQuery.class),new MailDispatchProperties(true,1,1,10),admission);
        var dispatcher=new MailDispatcher(directory,cycle,new MailDispatchProperties(true,1,1,10),5000,admission);
        try{
            admission.accepting=false;dispatcher.poll();assertThat(cycle.runTenant(951)).isZero();
            verifyNoInteractions(directory,work,sender);
            admission.accepting=true;assertThat(cycle.runTenant(951)).isZero();verify(work).recover();
            assertThat(admission.active).hasValue(0);
        }finally{dispatcher.close();}
    }

    @Test void queuedCallbacksDoNotReservePermitsAndRecheckAfterDrain()throws Exception{
        var queue=new LinkedBlockingQueue<Runnable>();var workers=mock(ExecutorService.class);
        doAnswer(i->{queue.add(i.getArgument(0));return null;}).when(workers).execute(any());
        var directory=mock(TenantWorkQuery.class);var cycle=mock(MailDispatchCycle.class);
        when(directory.page(0,10)).thenReturn(List.of(new TenantWorkQuery.Entry(951,true)));
        var dispatcher=new MailDispatcher(directory,cycle,new MailDispatchProperties(true,1,1,10),5000,admission,workers);
        try{
            dispatcher.poll();assertThat(admission.active).hasValue(0);assertThat(queue).hasSize(1);
            admission.accepting=false;queue.remove().run();verifyNoInteractions(cycle);
            admission.accepting=true;dispatcher.poll();queue.remove().run();verify(cycle).runTenant(951);
        }finally{dispatcher.close();}
    }

    @Test void drainDuringSendKeepsPermitThroughTransportAndFinishButStopsNextClaim(){
        var work=mock(MailWorkService.class);var sender=mock(SmtpMailSender.class);var directory=mock(TenantEntryQuery.class);
        when(directory.securityState(951)).thenReturn(Optional.of(new TenantEntryQuery.SecurityState(951,1,true,true)));
        var claim=new MailWorkService.Claim(951,41,1,"worker");
        var prepared=new MailWorkService.Prepared(claim,"id",new MailPayload("to@example.test","s","b",false),new MailProfileRevisionEntity());
        when(work.claim(anyString())).thenReturn(Optional.of(claim));when(work.begin(claim)).thenReturn(Optional.of(prepared));
        when(work.stillValid(claim)).thenReturn(true);
        var transport=new AtomicReference<DeploymentWork.Permit>();
        when(sender.send(eq(prepared),any())).thenAnswer(i->{
            assertThat(admission.active).hasValue(1);admission.accepting=false;transport.set(i.getArgument(1));
            return MailWorkService.Outcome.UNKNOWN;
        });
        when(work.finish(claim,MailWorkService.Outcome.UNKNOWN)).thenAnswer(i->{assertThat(admission.active).hasValue(1);return true;});
        var cycle=new MailDispatchCycle(work,sender,directory,new MailDispatchProperties(true,1,10,10),admission);
        assertThat(cycle.runTenant(951)).isOne();verify(work,times(1)).claim(anyString());
        assertThat(admission.active).hasValue(1);transport.get().close();transport.get().close();
        assertThat(admission.active).hasValue(0);assertThat(cycle.runTenant(951)).isZero();verify(work,times(1)).recover();
    }

    @Test void httpAndOrdinaryIdentityCannotBecomeHostDispatch(){
        var directory=mock(TenantWorkQuery.class);var work=mock(MailWorkService.class);var sender=mock(SmtpMailSender.class);
        var cycle=new MailDispatchCycle(work,sender,mock(TenantEntryQuery.class),new MailDispatchProperties(true,1,1,10),admission);
        var dispatcher=new MailDispatcher(directory,cycle,new MailDispatchProperties(true,1,1,10),5000,admission);
        try{
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                    new org.springframework.web.context.request.ServletRequestAttributes(new org.springframework.mock.web.MockHttpServletRequest()));
            assertThatThrownBy(dispatcher::poll).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThatThrownBy(()->cycle.runTenant(951)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
            ActorContext.withinJob(951,"not-host",()->{
                assertThatThrownBy(dispatcher::poll).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
                assertThatThrownBy(()->cycle.runTenant(952)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);return null;
            });
            TransactionSynchronizationManager.setActualTransactionActive(true);
            assertThatThrownBy(()->cycle.runTenant(951)).isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(work,sender,directory);
        }finally{
            TransactionSynchronizationManager.setActualTransactionActive(false);
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();dispatcher.close();
        }
    }
}
