package com.sma.admin.message.internal.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;
import org.springframework.security.access.AccessDeniedException;

class SocketSignalProcessorTests {
    private final SocketSignalProperties settings=new SocketSignalProperties(null,null,null,null,null);
    private final SocketHub.Audience audience=new SocketHub.Audience(91,"admin",77);
    private final TenantWorkQuery tenants=mock(TenantWorkQuery.class);
    private SocketHub hub(){var hub=mock(SocketHub.class);when(hub.subscriptions()).thenReturn(Set.of(audience));return hub;}
    private SocketSignalStore.Signal hint(long id,long revision){return new SocketSignalStore.Signal(id,"admin",77,"notice",100,revision,null);}

    @Test void eachNodeIndependentlyReadsTheSameHintAndUnchangedPollDoesNotRedeliver(){
        var store=mock(SocketSignalStore.class);var a=hub();var b=hub();
        when(store.read("admin",Set.of(77L))).thenAnswer(call->{assertThat(ActorContext.tenantId()).isEqualTo(91);return List.of(hint(1,1));});
        try(var first=new SocketSignalProcessor(a,store,settings,tenants);var second=new SocketSignalProcessor(b,store,settings,tenants)){
            first.pollOnce();second.pollOnce();first.pollOnce();second.pollOnce();
            verify(a,times(1)).push(91,"admin",Set.of(77L),"notice",100);
            verify(b,times(1)).push(91,"admin",Set.of(77L),"notice",100);
            assertThat(first.observedCount()).isEqualTo(1);assertThat(second.observedCount()).isEqualTo(1);
        }
    }
    @Test void newRevisionRecreatedRowAndReconnectAllRemainObservable(){
        var store=mock(SocketSignalStore.class);var hub=hub();
        when(store.read("admin",Set.of(77L))).thenReturn(List.of(hint(1,1)),List.of(hint(1,2)),List.of(hint(2,1)),List.of(hint(2,1)));
        try(var processor=new SocketSignalProcessor(hub,store,settings,tenants)){
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
        try(var processor=new SocketSignalProcessor(hub,store,settings,tenants)){
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
        try(var processor=new SocketSignalProcessor(hub,store,settings,tenants)){
            processor.pollOnce();processor.pollOnce();verify(hub).push(91,"admin",Set.of(77L),"notice",100);verify(hub).push(92,"customer",Set.of(77L),"todo",200);
            Users.as(Users.principal(91,91,DataAccess.unrestricted()),()->{assertThatThrownBy(processor::tick).isInstanceOf(AccessDeniedException.class);return null;});
        }
    }
}
