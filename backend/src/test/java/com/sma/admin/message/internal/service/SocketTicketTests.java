package com.sma.admin.message.internal.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.admin.auth.SessionQuery;
import com.sma.admin.common.security.*;
import com.sma.admin.message.internal.persistence.*;
import com.sma.admin.support.IntegrationTest;
import com.sma.admin.support.Users;

@IntegrationTest
class SocketTicketTests {
 @Autowired SocketTicketMapper rows;
 @Autowired SocketTicketGuardMapper guards;
 @Autowired PlatformTransactionManager transactions;
 private final SessionQuery sessions=mock(SessionQuery.class);
 private SocketTicketService node(int capacity){return new SocketTicketService(sessions,new SocketProperties(null,null,capacity),rows,guards,transactions);}
 private SmaPrincipal actor(long tenant,String portal){return new SmaPrincipal(101,"ticket-owner",tenant,portal,null,false,DataAccess.own(),Set.of(),"UTC");}
 @Test void ticketsAreSingleUseExpireAndRemainBoundToActiveSession(){
  when(sessions.active(7,101,"admin")).thenReturn(true);
  var service=node(100);var other=node(100);
  Users.as(actor(98301,"admin"),()->{
   var ticket=service.issue(7);assertThat(ticket.ticket()).hasSize(43);
   assertThat(rows.selectList(null)).singleElement().satisfies(row->{assertThat(row.getTokenHash()).matches("[a-f0-9]{64}").isNotEqualTo(ticket.ticket());assertThat(row.getExpiresAt()).isEqualTo(ticket.expiresAt());});
   assertThat(other.consume(ticket.ticket())).get().extracting(SocketTicketService.Grant::tenantId).isEqualTo(98301L);
   assertThat(service.consume(ticket.ticket())).isEmpty();assertThat(service.consume("invalid")).isEmpty();
   var old=service.issue(7);var replacement=other.issue(7);assertThat(service.consume(old.ticket())).isEmpty();
   when(sessions.active(7,101,"admin")).thenReturn(false);assertThat(service.consume(replacement.ticket())).isEmpty();
   when(sessions.active(7,101,"admin")).thenReturn(true);assertThat(other.consume(replacement.ticket())).isEmpty();
   var expired=service.issue(7);expire();assertThat(other.consume(expired.ticket())).isEmpty();return null;
  });
 }
 @Test void independentNodesRaceForExactlyOneCommittedConsumption() throws Exception {
  var actor=actor(98302,"admin");when(sessions.active(8,101,"admin")).thenReturn(true);
  var a=node(100);var b=node(100);String token=Users.as(actor,()->a.issue(8).ticket());
  var start=new CountDownLatch(1);var successes=new AtomicInteger();
  try(var pool=Executors.newFixedThreadPool(8)){
   var futures=new ArrayList<Future<?>>();for(int i=0;i<16;i++){var service=i%2==0?a:b;futures.add(pool.submit(()->{
    try{start.await();}catch(InterruptedException interrupted){throw new IllegalStateException(interrupted);}
    Users.as(actor,()->{if(service.consume(token).isPresent())successes.incrementAndGet();return null;});
   }));}start.countDown();for(var future:futures)future.get(30,TimeUnit.SECONDS);
  }
  assertThat(successes).hasValue(1);Users.as(actor,()->{assertThat(rows.selectCount(null)).isZero();return null;});
 }
 @Test void wrongTenantOrPortalDoesNotConsumeTheRightfulOwnersGrant(){
  when(sessions.active(9,101,"admin")).thenReturn(true);var service=node(100);
  String token=Users.as(actor(98303,"admin"),()->service.issue(9).ticket());
  Users.as(actor(98304,"admin"),()->{assertThat(service.consume(token)).isEmpty();return null;});
  Users.as(actor(98303,"other"),()->{assertThat(service.consume(token)).isEmpty();return null;});
  Users.as(actor(98303,"admin"),()->{assertThat(service.consume(token)).isPresent();return null;});
 }
 @Test void consumptionCannotBeRolledBackByAnOuterRequestOrReusedAfterProcessReplacement(){
  when(sessions.active(10,101,"admin")).thenReturn(true);var a=node(100);
  Users.as(actor(98305,"admin"),()->{
   var token=a.issue(10).ticket();new TransactionTemplate(transactions).execute(status->{assertThat(a.consume(token)).isPresent();status.setRollbackOnly();return null;});
   assertThat(node(100).consume(token)).isEmpty();return null;
  });
 }
 @Test void sharedCapacityPermitsReplacementAndBoundedExpiryReleasesSlots(){
  when(sessions.active(anyLong(),eq(101L),eq("admin"))).thenReturn(true);var a=node(2);var b=node(2);
  Users.as(actor(98306,"admin"),()->{
   var first=a.issue(11);b.issue(12);
   assertThatThrownBy(()->a.issue(13)).isInstanceOf(com.sma.admin.common.BizException.class);
   var replacement=b.issue(11);assertThat(a.consume(first.ticket())).isEmpty();assertThat(rows.selectCount(null)).isEqualTo(2);
   assertThat(a.consume(replacement.ticket())).isPresent();a.issue(13);expire();assertThat(b.issue(14)).isNotNull();assertThat(rows.selectCount(null)).isEqualTo(1);return null;
  });
 }
 @Test void ticketTransactionsNeverHoldConnectionsAcrossTheAuthAuthorityAndItsFailureDoesNotRestoreConsumption(){
  var service=node(100);
  when(sessions.active(15,101,"admin")).thenAnswer(call->{
   assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return true;
  });
  Users.as(actor(98307,"admin"),()->{
   var ticket=service.issue(15);assertThat(service.consume(ticket.ticket())).isPresent();
   var uncertain=service.issue(15);when(sessions.active(15,101,"admin")).thenThrow(new IllegalStateException("Authority unavailable"));
   assertThatThrownBy(()->service.consume(uncertain.ticket())).isInstanceOf(IllegalStateException.class);
   doReturn(true).when(sessions).active(15,101,"admin");assertThat(service.consume(uncertain.ticket())).isEmpty();return null;
  });
 }
 private void expire(){for(var row:rows.selectList(null)){row.setExpiresAt(Instant.EPOCH);rows.updateById(row);}}
}
