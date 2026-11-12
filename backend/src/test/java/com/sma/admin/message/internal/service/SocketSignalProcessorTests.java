package com.sma.admin.message.internal.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import com.sma.admin.support.DeploymentWorkFixture;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;
import org.springframework.security.access.AccessDeniedException;

class SocketSignalProcessorTests {
    private final DeploymentWorkFixture admission=new DeploymentWorkFixture();
    private final SocketSignalProperties settings=new SocketSignalProperties(null,null,null,null,null);
    private final SocketHub.Audience audience=new SocketHub.Audience(91,"admin",77);
    private final TenantWorkQuery tenants=mock(TenantWorkQuery.class);
    private SocketHub hub(){var hub=mock(SocketHub.class);when(hub.subscriptions()).thenReturn(Set.of(audience));return hub;}
    private SocketSignalStore.Signal hint(long id,long revision){return new SocketSignalStore.Signal(id,"admin",77,"notice",100,revision,null);}

    @Test void noQualificationOrQueuedDrainDoesNotReadPushOrReserveWork(){
        var store=mock(SocketSignalStore.class);var hub=hub();var worker=mock(ExecutorService.class);var queue=new LinkedBlockingQueue<Runnable>();
        doAnswer(i->{queue.add(i.getArgument(0));return null;}).when(worker).execute(any());
        when(store.read("admin",Set.of(77L))).thenReturn(List.of(hint(1,1)));
        try(var processor=new SocketSignalProcessor(hub,store,settings,tenants,admission,worker)){
            admission.accepting=false;processor.tick();processor.pollOnce();assertThat(queue).isEmpty();verifyNoInteractions(store,tenants);verify(hub,never()).subscriptions();
            admission.accepting=true;processor.tick();assertThat(queue).hasSize(1);assertThat(admission.active).hasValue(0);
            admission.accepting=false;queue.remove().run();verifyNoInteractions(store,tenants);verify(hub,never()).subscriptions();
            admission.accepting=true;processor.tick();queue.remove().run();verify(hub).push(91,"admin",Set.of(77L),"notice",100);assertThat(admission.active).hasValue(0);
        }
    }

    @Test void refusalAfterReadKeepsCurrentBatchAndDoesNotMarkObserved(){
        var store=mock(SocketSignalStore.class);var hub=hub();var other=new SocketHub.Audience(92,"customer",77);
        when(hub.subscriptions()).thenReturn(Set.of(audience,other));
        when(store.read("admin",Set.of(77L))).thenAnswer(i->{assertThat(admission.active).hasValue(1);admission.accepting=false;return List.of(hint(1,1));});
        try(var processor=new SocketSignalProcessor(hub,store,settings,tenants,admission)){
            processor.pollOnce();assertThat(processor.observedCount()).isZero();verify(hub,never()).push(anyLong(),anyString(),anySet(),anyString(),anyLong());
            verifyNoInteractions(tenants);assertThat(admission.active).hasValue(0);
            admission.accepting=true;doReturn(List.of(hint(1,1))).when(store).read("admin",Set.of(77L));processor.pollOnce();
            verify(store,times(2)).read("admin",Set.of(77L));verify(store,never()).read(eq("customer"),anySet());
            verify(hub).push(91,"admin",Set.of(77L),"notice",100);assertThat(processor.observedCount()).isOne();
        }
    }

    @Test void admittedPushRemainsCountedUntilReturnAndFollowingSignalIsNotStarted()throws Exception{
        var store=mock(SocketSignalStore.class);var hub=hub();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var ended=new SocketSignalStore.Signal(2,"*",77,"session-ended",800,1,"FORCED");
        when(store.read("admin",Set.of(77L))).thenReturn(List.of(hint(1,1),ended));
        doAnswer(i->{assertThat(admission.active).hasValue(1);entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Push not released");return null;})
                .when(hub).push(91,"admin",Set.of(77L),"notice",100);
        try(var processor=new SocketSignalProcessor(hub,store,settings,tenants,admission);var runner=Executors.newSingleThreadExecutor()){
            var task=runner.submit(processor::pollOnce);
            try{assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();admission.accepting=false;assertThat(admission.active).hasValue(1);}
            finally{release.countDown();}
            task.get(3,TimeUnit.SECONDS);assertThat(admission.active).hasValue(0);assertThat(processor.observedCount()).isOne();
            verify(hub,never()).revalidate(any(),anyLong(),anyString());verifyNoInteractions(tenants);
            admission.accepting=true;processor.pollOnce();verify(hub,times(1)).push(91,"admin",Set.of(77L),"notice",100);
            verify(hub).revalidate(audience,800,"FORCED");assertThat(processor.observedCount()).isEqualTo(2);
        }
    }

    @Test void maintenanceRefusalDoesNotAdvanceDirectoryCursorOrDelayNextAttempt(){
        var store=mock(SocketSignalStore.class);var hub=hub();when(hub.subscriptions()).thenReturn(Set.of());
        when(tenants.page(0,1)).thenAnswer(i->{assertThat(admission.active).hasValue(1);admission.accepting=false;return List.of(new TenantWorkQuery.Entry(91,false));});
        try(var processor=new SocketSignalProcessor(hub,store,settings,tenants,admission)){
            processor.pollOnce();verifyNoInteractions(store);assertThat(admission.active).hasValue(0);
            admission.accepting=true;doReturn(List.of(new TenantWorkQuery.Entry(91,false))).when(tenants).page(0,1);
            when(store.purge()).thenAnswer(i->{assertThat(admission.active).hasValue(1);assertThat(ActorContext.tenantId()).isEqualTo(91);return 1;});
            processor.pollOnce();verify(tenants,times(2)).page(0,1);verify(store).purge();assertThat(admission.active).hasValue(0);
        }
    }

    @Test void entriesRejectHttpJobAndOuterTransactionAndClosedProcessorStaysIdle(){
        var store=mock(SocketSignalStore.class);var hub=hub();
        try(var processor=new SocketSignalProcessor(hub,store,settings,tenants,admission)){
            com.sma.admin.support.MaintenanceCallerAssertions.hostOnly(processor::tick,processor::pollOnce);
            processor.close();processor.tick();processor.pollOnce();verifyNoInteractions(store,tenants);verify(hub,never()).subscriptions();
        }
    }

    @Test void actualWorkerDoesNotInheritPublisherThreadLocals()throws Exception{
        var store=mock(SocketSignalStore.class);var hub=hub();var called=new CountDownLatch(1);var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var inherited=new InheritableThreadLocal<String>();inherited.set("caller");
        when(store.read("admin",Set.of(77L))).thenAnswer(i->{
            try{assertThat(inherited.get()).isNull();assertThat(admission.active).hasValue(1);}
            catch(Throwable error){failure.set(error);}finally{called.countDown();}return List.of();
        });
        try(var processor=new SocketSignalProcessor(hub,store,settings,tenants,admission)){
            processor.tick();assertThat(called.await(3,TimeUnit.SECONDS)).isTrue();assertThat(failure.get()).isNull();
        }finally{inherited.remove();}
    }

    @Test void eachNodeIndependentlyReadsTheSameHintAndUnchangedPollDoesNotRedeliver(){
        var store=mock(SocketSignalStore.class);var a=hub();var b=hub();
        when(store.read("admin",Set.of(77L))).thenAnswer(call->{assertThat(ActorContext.tenantId()).isEqualTo(91);return List.of(hint(1,1));});
        try(var first=new SocketSignalProcessor(a,store,settings,tenants,admission);var second=new SocketSignalProcessor(b,store,settings,tenants,admission)){
            first.pollOnce();second.pollOnce();first.pollOnce();second.pollOnce();
            verify(a,times(1)).push(91,"admin",Set.of(77L),"notice",100);
            verify(b,times(1)).push(91,"admin",Set.of(77L),"notice",100);
            assertThat(first.observedCount()).isEqualTo(1);assertThat(second.observedCount()).isEqualTo(1);
        }
    }
    @Test void newRevisionRecreatedRowAndReconnectAllRemainObservable(){
        var store=mock(SocketSignalStore.class);var hub=hub();
        when(store.read("admin",Set.of(77L))).thenReturn(List.of(hint(1,1)),List.of(hint(1,2)),List.of(hint(2,1)),List.of(hint(2,1)));
        try(var processor=new SocketSignalProcessor(hub,store,settings,tenants,admission)){
            processor.pollOnce();processor.pollOnce();processor.pollOnce();
            when(hub.subscriptions()).thenReturn(Set.of());processor.pollOnce();assertThat(processor.observedCount()).isZero();
            when(hub.subscriptions()).thenReturn(Set.of(audience));processor.pollOnce();
            verify(hub,times(4)).push(91,"admin",Set.of(77L),"notice",100);
        }
    }
    @Test void readOrDeliveryFailureDoesNotAcknowledgeAndSessionHintOnlyRevalidates(){
        var store=mock(SocketSignalStore.class);var hub=hub();
        when(store.read("admin",Set.of(77L))).thenThrow(new IllegalStateException("offline")).thenReturn(List.of(hint(1,1)));
        doThrow(new IllegalStateException("authority unavailable")).doNothing().when(hub).push(91,"admin",Set.of(77L),"notice",100);
        try(var processor=new SocketSignalProcessor(hub,store,settings,tenants,admission)){
            processor.pollOnce();processor.pollOnce();assertThat(processor.observedCount()).isZero();
            processor.pollOnce();assertThat(processor.observedCount()).isEqualTo(1);
            when(store.read("admin",Set.of(77L))).thenReturn(List.of(new SocketSignalStore.Signal(2,"*",77,"session-ended",800,1,"FORCED")));
            processor.pollOnce();verify(hub).revalidate(audience,800,"FORCED");
            verify(hub,times(2)).push(91,"admin",Set.of(77L),"notice",100);
        }
    }
    @Test void fairRecipientBatchesRestoreTheirOwnTenantAndHttpCannotDriveDispatcher(){
        var store=mock(SocketSignalStore.class);var hub=hub();var other=new SocketHub.Audience(92,"customer",77);
        when(hub.subscriptions()).thenReturn(Set.of(audience,other));
        when(store.read("admin",Set.of(77L))).thenAnswer(call->{assertThat(ActorContext.tenantId()).isEqualTo(91);return List.of(hint(1,1));});
        when(store.read("customer",Set.of(77L))).thenAnswer(call->{assertThat(ActorContext.tenantId()).isEqualTo(92);return List.of(new SocketSignalStore.Signal(2,"customer",77,"todo",200,1,null));});
        try(var processor=new SocketSignalProcessor(hub,store,settings,tenants,admission)){
            processor.pollOnce();processor.pollOnce();verify(hub).push(91,"admin",Set.of(77L),"notice",100);verify(hub).push(92,"customer",Set.of(77L),"todo",200);
            Users.as(Users.principal(91,91,DataAccess.unrestricted()),()->{assertThatThrownBy(processor::tick).isInstanceOf(AccessDeniedException.class);return null;});
        }
    }
}
