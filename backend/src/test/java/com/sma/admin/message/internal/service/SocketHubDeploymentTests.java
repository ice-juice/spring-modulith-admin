package com.sma.admin.message.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.*;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.deployment.*;
import com.sma.admin.support.MaintenanceCallerAssertions;

class SocketHubDeploymentTests {
    final SocketTicketService tickets=mock(SocketTicketService.class);
    final AtomicBoolean accepting=new AtomicBoolean(true),draining=new AtomicBoolean();
    final AtomicInteger active=new AtomicInteger();
    final AtomicLong clock=new AtomicLong();
    final DeploymentWork admission=new DeploymentWork(){
        public Optional<Permit> tryStart(){if(!accepting.get()||draining.get())return Optional.empty();active.incrementAndGet();return Optional.of(()->active.decrementAndGet());}
        public Snapshot snapshot(){return new Snapshot(accepting.get()&&!draining.get(),draining.get(),active.get());}
    };
    SocketHub hub(){return new SocketHub(tickets,new SocketProperties(null,null,100),JsonMapper.builder().build(),admission,mock(ExecutorService.class),clock::get);}
    WebSocketSession session(SocketHub hub,String id,long user)throws Exception{
        var socket=mock(WebSocketSession.class);when(socket.getId()).thenReturn(id);when(socket.isOpen()).thenReturn(true);
        var grant=new SocketTicketService.Grant(91,"admin",user,user,Instant.now().plusSeconds(30));
        when(tickets.active(grant)).thenReturn(true);
        when(socket.getAttributes()).thenReturn(Map.of("sma.socket-grant",grant,SocketHub.HANDSHAKE_ATTRIBUTE,hub.beginHandshake().orElseThrow()));
        return socket;
    }
    void push(SocketHub hub){hub.push(91,"admin",Set.of(77L),"notice",1);}
    @Test void reservationCapacityAndLateUpgradeRemainCountedUntilActualCloseCallback()throws Exception{
        try(var hub=new SocketHub(tickets,new SocketProperties(null,null,1),JsonMapper.builder().build(),admission,mock(ExecutorService.class),clock::get)){
            var socket=session(hub,"late",77);assertThat(hub.snapshot()).isEqualTo(new DeploymentConnections.Snapshot(1,0,0));
            assertThat(hub.beginHandshake()).isEmpty();draining.set(true);hub.afterConnectionEstablished(socket);
            assertThat(hub.snapshot()).isEqualTo(new DeploymentConnections.Snapshot(0,1,0));assertThat(hub.subscriptions()).isEmpty();
            hub.maintainOnce();verify(socket).close(CloseStatus.GOING_AWAY);verify(socket,never()).sendMessage(any());verify(tickets,never()).active(any());
            assertThat(hub.snapshot().drained()).isFalse();hub.afterConnectionClosed(socket,CloseStatus.GOING_AWAY);
            assertThat(hub.snapshot().drained()).isTrue();assertThat(hub.beginHandshake()).isEmpty();
        }
    }
    @Test void bufferedReturnDoesNotHideOriginalSenderAndDrainWaitsBefore1001()throws Exception{
        try(var hub=hub();var threads=Executors.newSingleThreadExecutor()){
            var socket=session(hub,"buffered",77);hub.afterConnectionEstablished(socket);clearInvocations(socket,tickets);
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var sends=new AtomicInteger();
            doAnswer(i->{if(sends.incrementAndGet()==1){entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("send not released");}return null;}).when(socket).sendMessage(any());
            var first=threads.submit(()->push(hub));
            try{
                assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();push(hub); // returns after queuing in Spring decorator
                assertThat(sends).hasValue(1);assertThat(hub.snapshot().operations()).isOne();
                draining.set(true);hub.maintainOnce();push(hub);verify(socket,never()).close(any());
                assertThat(hub.snapshot().operations()).isOne();assertThat(hub.subscriptions()).isEmpty();
            }finally{release.countDown();}
            first.get(3,TimeUnit.SECONDS);assertThat(sends).hasValue(2);assertThat(hub.snapshot().operations()).isZero();
            hub.maintainOnce();verify(socket).close(CloseStatus.GOING_AWAY);assertThat(hub.snapshot().connections()).isOne();
            hub.afterConnectionClosed(socket,CloseStatus.GOING_AWAY);assertThat(hub.snapshot().drained()).isTrue();
            verify(socket,never()).sendMessage(argThat(m->m instanceof TextMessage text&&text.getPayload().contains("session-ended")));
        }
    }
    @Test void earlyTransportCallbackDoesNotEraseActiveIoAndFailuresDoNotFakeClosure()throws Exception{
        try(var hub=hub();var threads=Executors.newSingleThreadExecutor()){
            var socket=session(hub,"early",77);hub.afterConnectionEstablished(socket);
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            doAnswer(i->{entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("send not released");throw new java.io.IOException();}).when(socket).sendMessage(any());
            var sending=threads.submit(()->push(hub));
            try{assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();hub.afterConnectionClosed(socket,CloseStatus.SERVER_ERROR);
                assertThat(hub.snapshot()).isEqualTo(new DeploymentConnections.Snapshot(0,0,1));}
            finally{release.countDown();}
            sending.get(3,TimeUnit.SECONDS);assertThat(hub.snapshot().drained()).isTrue();
        }
        try(var hub=hub()){
            var socket=session(hub,"retry-close",77);hub.afterConnectionEstablished(socket);draining.set(true);
            doThrow(new java.io.IOException()).doNothing().when(socket).close(CloseStatus.GOING_AWAY);
            hub.maintainOnce();assertThat(hub.snapshot()).isEqualTo(new DeploymentConnections.Snapshot(0,1,0));
            hub.maintainOnce();verify(socket,times(2)).close(CloseStatus.GOING_AWAY);assertThat(hub.snapshot().drained()).isFalse();
            hub.afterConnectionClosed(socket,CloseStatus.GOING_AWAY);assertThat(hub.snapshot().drained()).isTrue();
        }
    }
    @Test void pingHasPerConnectionAdmissionAndKeepsPermitUntilActualReturn()throws Exception{
        try(var hub=hub();var threads=Executors.newSingleThreadExecutor()){
            var socket=session(hub,"ping",77);hub.afterConnectionEstablished(socket);clearInvocations(socket,tickets);
            clock.set(TimeUnit.SECONDS.toNanos(31));accepting.set(false);hub.maintainOnce();verifyNoInteractions(socket,tickets);
            accepting.set(true);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            doAnswer(i->{assertThat(active).hasValue(1);entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("ping not released");return null;}).when(socket).sendMessage(isA(PingMessage.class));
            var ping=threads.submit(hub::maintainOnce);
            try{assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();draining.set(true);assertThat(active).hasValue(1);assertThat(hub.snapshot().operations()).isOne();}
            finally{release.countDown();}
            ping.get(3,TimeUnit.SECONDS);assertThat(active).hasValue(0);hub.maintainOnce();verify(socket).close(CloseStatus.GOING_AWAY);
            verify(socket,times(1)).sendMessage(isA(PingMessage.class));hub.afterConnectionClosed(socket,CloseStatus.GOING_AWAY);
        }
    }
    @Test void pendingQueueRechecksDrainAndActualWorkerDoesNotInheritContext()throws Exception{
        var executor=mock(ExecutorService.class);var queue=new ArrayDeque<Runnable>();doAnswer(i->{queue.add(i.getArgument(0));return null;}).when(executor).execute(any());
        try(var hub=new SocketHub(tickets,new SocketProperties(null,null,100),JsonMapper.builder().build(),admission,executor,clock::get)){
            var socket=session(hub,"queue",77);hub.afterConnectionEstablished(socket);clearInvocations(socket,tickets);
            clock.set(TimeUnit.SECONDS.toNanos(31));hub.tick();hub.tick();assertThat(queue).hasSize(1);assertThat(active).hasValue(0);
            draining.set(true);queue.remove().run();verify(socket).close(CloseStatus.GOING_AWAY);verify(tickets,never()).active(any());
            hub.afterConnectionClosed(socket,CloseStatus.GOING_AWAY);MaintenanceCallerAssertions.hostOnly(hub::tick,hub::maintainOnce);
        }
        draining.set(false);var inherited=new InheritableThreadLocal<String>();inherited.set("caller");
        try(var hub=new SocketHub(tickets,new SocketProperties(null,null,100),JsonMapper.builder().build(),admission)){
            var socket=session(hub,"thread",77);hub.afterConnectionEstablished(socket);var seen=new CountDownLatch(1);var failure=new AtomicReference<Throwable>();
            doAnswer(i->{try{assertThat(inherited.get()).isNull();assertThat(org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).isNull();}
                catch(Throwable error){failure.set(error);}finally{hub.afterConnectionClosed(socket,CloseStatus.GOING_AWAY);seen.countDown();}return null;}).when(socket).close(CloseStatus.GOING_AWAY);
            draining.set(true);hub.tick();assertThat(seen.await(3,TimeUnit.SECONDS)).isTrue();assertThat(failure.get()).isNull();
        }finally{inherited.remove();}
    }
    @Test void forcedLogoutKeepsOriginalHintAndPolicyStatus()throws Exception{
        try(var hub=hub()){
            var socket=session(hub,"forced",77);hub.afterConnectionEstablished(socket);clearInvocations(socket);
            hub.endSession(92,77,"FORCED");hub.maintainOnce();verify(socket,never()).close(any());
            hub.endSession(91,77,"FORCED");hub.maintainOnce();
            verify(socket).sendMessage(argThat(m->m instanceof TextMessage t&&t.getPayload().contains("session-ended")&&t.getPayload().contains("FORCED")));
            verify(socket).close(CloseStatus.POLICY_VIOLATION);assertThat(hub.snapshot().connections()).isOne();
            hub.afterConnectionClosed(socket,CloseStatus.POLICY_VIOLATION);
        }
    }
    @Test void failedClosesCannotStarveLaterConnectionsAndEachCycleIsBounded()throws Exception{
        try(var hub=new SocketHub(tickets,new SocketProperties(null,null,300),JsonMapper.builder().build(),admission,mock(ExecutorService.class),clock::get)){
            var sockets=new ArrayList<WebSocketSession>();
            for(int i=0;i<130;i++){
                var socket=session(hub,"budget-"+i,1000+i);hub.afterConnectionEstablished(socket);sockets.add(socket);
                doThrow(new java.io.IOException()).when(socket).close(CloseStatus.GOING_AWAY);
            }
            draining.set(true);hub.maintainOnce();
            verify(sockets.get(127)).close(CloseStatus.GOING_AWAY);verify(sockets.get(128),never()).close(any());
            hub.maintainOnce();verify(sockets.get(128)).close(CloseStatus.GOING_AWAY);verify(sockets.get(129)).close(CloseStatus.GOING_AWAY);
            assertThat(hub.snapshot()).isEqualTo(new DeploymentConnections.Snapshot(0,130,0));
            sockets.forEach(socket->hub.afterConnectionClosed(socket,CloseStatus.GOING_AWAY));
        }
    }
    @Test void qualificationLostDuringUpgradeClosesLateConnectionWithoutSessionQuery()throws Exception{
        try(var hub=hub()){
            var socket=session(hub,"late-unqualified",77);accepting.set(false);hub.afterConnectionEstablished(socket);
            hub.maintainOnce();verify(socket).close(CloseStatus.GOING_AWAY);verify(tickets,never()).active(any());
            hub.afterConnectionClosed(socket,CloseStatus.GOING_AWAY);accepting.set(true);
            var pending=hub.beginHandshake().orElseThrow();assertThat(hub.snapshot().handshakes()).isOne();pending.close();pending.close();assertThat(hub.snapshot().drained()).isTrue();
        }
    }
    @Test void upgradeArrivingAfterFinalDestructionStillClosesAndNeverLosesItsReservation()throws Exception{
        var hub=hub();var socket=session(hub,"after-destroy",77);
        hub.close();assertThat(hub.snapshot().handshakes()).isOne();
        hub.afterConnectionEstablished(socket);verify(socket).close(CloseStatus.GOING_AWAY);verify(tickets,never()).active(any());
        assertThat(hub.snapshot()).isEqualTo(new DeploymentConnections.Snapshot(0,1,0));
        hub.afterConnectionClosed(socket,CloseStatus.GOING_AWAY);assertThat(hub.snapshot().drained()).isTrue();
    }
}
