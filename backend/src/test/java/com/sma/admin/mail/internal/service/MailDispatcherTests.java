package com.sma.admin.mail.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.security.*;
import com.sma.admin.mail.internal.persistence.MailProfileRevisionEntity;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MailDispatcherTests {
    @Test void invalidationAfterPreparationCancelsWithoutInvokingTransport(){
        var work=mock(MailWorkService.class);var sender=mock(SmtpMailSender.class);var directory=mock(TenantEntryQuery.class);
        when(directory.securityState(951)).thenReturn(Optional.of(new TenantEntryQuery.SecurityState(951,1,true,true)));
        var claim=new MailWorkService.Claim(951,41,1,"worker");
        when(work.claim(anyString())).thenReturn(Optional.of(claim));
        when(work.begin(claim)).thenReturn(Optional.of(new MailWorkService.Prepared(claim,"id",new MailPayload("to@example.test","subject","body",false),new MailProfileRevisionEntity())));
        when(work.stillValid(claim)).thenReturn(false);
        var cycle=new MailDispatchCycle(work,sender,directory,new MailDispatchProperties(true,1,1,10));assertThat(cycle.runTenant(951)).isZero();
        verifyNoInteractions(sender);verify(work).finish(claim,MailWorkService.Outcome.CANCELLED);
    }
    @Test void disabledDispatcherDoesNotDiscoverTenantsOrSendAnything(){
        var directory=mock(TenantWorkQuery.class);var cycle=mock(MailDispatchCycle.class);
        var dispatcher=new MailDispatcher(directory,cycle,new MailDispatchProperties(false,1,1,1),5000);
        try{dispatcher.poll();verifyNoInteractions(directory,cycle);}finally{dispatcher.close();}
    }
    @Test void newDispatcherRediscoversPersistentTenantsAndRestoresWorkerScope()throws Exception{
        var directory=mock(TenantWorkQuery.class);var cycle=mock(MailDispatchCycle.class);
        when(directory.page(0,10)).thenReturn(List.of(new TenantWorkQuery.Entry(951,true),new TenantWorkQuery.Entry(952,false)));
        var calls=new CountDownLatch(4);when(cycle.runTenant(anyLong())).thenAnswer(i->{calls.countDown();return 0;});
        for(int i=0;i<2;i++){
            var dispatcher=new MailDispatcher(directory,cycle,new MailDispatchProperties(true,1,1,10),5000);
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
        when(sender.send(prepared)).thenAnswer(i->{
            assertThat(ActorContext.require().kind()).isEqualTo(ActorRef.Kind.SYSTEM_JOB);assertThat(ActorContext.tenantId()).isEqualTo(951);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return MailWorkService.Outcome.ACCEPTED;
        });when(work.finish(claim,MailWorkService.Outcome.ACCEPTED)).thenReturn(true);
        var cycle=new MailDispatchCycle(work,sender,directory,new MailDispatchProperties(true,1,1,10));
        assertThat(cycle.runTenant(951)).isEqualTo(1);
        assertThatThrownBy(ActorContext::require).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        when(directory.securityState(951)).thenReturn(Optional.of(new TenantEntryQuery.SecurityState(951,2,false,false)));
        assertThat(cycle.runTenant(951)).isZero();verify(sender,times(1)).send(any());verify(work,times(2)).recover();
    }
    @Test void lostFinishCommitDoesNotCallSenderAgainAndOuterContextIsRestored(){
        var work=mock(MailWorkService.class);var sender=mock(SmtpMailSender.class);var directory=mock(TenantEntryQuery.class);
        when(directory.securityState(951)).thenReturn(Optional.of(new TenantEntryQuery.SecurityState(951,1,true,true)));
        var claim=new MailWorkService.Claim(951,41,1,"worker");
        var prepared=new MailWorkService.Prepared(claim,"id@example.test",new MailPayload("to@example.test","s","b",false),new MailProfileRevisionEntity());
        when(work.claim(anyString())).thenReturn(Optional.of(claim));when(work.begin(claim)).thenReturn(Optional.of(prepared));
        when(work.stillValid(claim)).thenReturn(true);
        when(sender.send(prepared)).thenReturn(MailWorkService.Outcome.ACCEPTED);
        when(work.finish(claim,MailWorkService.Outcome.ACCEPTED)).thenThrow(new IllegalStateException("database unavailable"));
        var cycle=new MailDispatchCycle(work,sender,directory,new MailDispatchProperties(true,1,10,10));
        assertThatThrownBy(()->cycle.runTenant(951)).isInstanceOf(IllegalStateException.class);
        verify(sender,times(1)).send(any());assertThatThrownBy(ActorContext::require).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}
