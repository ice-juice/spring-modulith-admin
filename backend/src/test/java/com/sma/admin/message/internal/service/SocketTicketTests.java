package com.sma.admin.message.internal.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import com.sma.admin.auth.SessionQuery;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;

class SocketTicketTests {
 @Test void ticketsAreSingleUseExpireAndRemainBoundToActiveSession(){
  SessionQuery sessions=mock(SessionQuery.class);Clock clock=mock(Clock.class);Instant now=Instant.parse("2026-11-10T08:00:00Z");when(clock.instant()).thenReturn(now);
  when(sessions.active(7,101,"admin")).thenReturn(true);
  var service=new SocketTicketService(sessions,new SocketProperties(null,null,100),clock);
  Users.as(Users.principal(101,251,DataAccess.own()),()->{
   var ticket=service.issue(7);assertThat(ticket.ticket()).hasSize(43);assertThat(service.consume(ticket.ticket())).get().extracting(SocketTicketService.Grant::tenantId).isEqualTo(251L);
   assertThat(service.consume(ticket.ticket())).isEmpty();assertThat(service.consume("invalid")).isEmpty();
   var old=service.issue(7);var replacement=service.issue(7);assertThat(service.consume(old.ticket())).isEmpty();
   when(sessions.active(7,101,"admin")).thenReturn(false);assertThat(service.consume(replacement.ticket())).isEmpty();
   when(sessions.active(7,101,"admin")).thenReturn(true);var expired=service.issue(7);when(clock.instant()).thenReturn(now.plusSeconds(31));assertThat(service.consume(expired.ticket())).isEmpty();return null;
  });
 }
}
