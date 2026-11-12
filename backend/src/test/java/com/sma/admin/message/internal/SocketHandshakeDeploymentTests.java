package com.sma.admin.message.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.http.server.*;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.message.internal.service.*;
import com.sma.admin.support.DeploymentWorkFixture;

class SocketHandshakeDeploymentTests {
    final SocketTicketService tickets=mock(SocketTicketService.class);
    final DeploymentWorkFixture work=new DeploymentWorkFixture();
    final SocketTicketService.Grant grant=new SocketTicketService.Grant(1,"admin",2,3,Instant.now().plusSeconds(30));
    @Test void refusalDoesNotConsumeAndFailedHandshakeReleasesReservation()throws Exception{
        try(var hub=new SocketHub(tickets,new SocketProperties(null,null,100),JsonMapper.builder().build(),work)){
            var interceptor=new SocketHandshakeInterceptor(hub,tickets);var raw=new MockHttpServletRequest("GET","/api/message/ws");raw.setQueryString("ticket=one");
            var request=new ServletServerHttpRequest(raw);var result=new MockHttpServletResponse();var response=new ServletServerHttpResponse(result);var attributes=new HashMap<String,Object>();
            work.accepting=false;assertThat(interceptor.beforeHandshake(request,response,hub,attributes)).isFalse();assertThat(result.getStatus()).isEqualTo(503);verifyNoInteractions(tickets);
            work.accepting=true;when(tickets.consume("one")).thenReturn(Optional.empty());assertThat(interceptor.beforeHandshake(request,response,hub,attributes)).isFalse();assertThat(hub.snapshot().drained()).isTrue();
            when(tickets.consume("one")).thenReturn(Optional.of(grant));assertThat(interceptor.beforeHandshake(request,response,hub,attributes)).isTrue();assertThat(hub.snapshot().handshakes()).isOne();
            interceptor.afterHandshake(request,response,hub,new IllegalStateException());assertThat(hub.snapshot().drained()).isTrue();
            assertThat(interceptor.beforeHandshake(request,response,hub,attributes)).isTrue();result.setStatus(400);interceptor.afterHandshake(request,response,hub,null);assertThat(hub.snapshot().drained()).isTrue();
        }
    }
    @Test void successfulUpgradeRetainsReservationUntilConnectionHandoffAndExceptionCannotLeak()throws Exception{
        try(var hub=new SocketHub(tickets,new SocketProperties(null,null,100),JsonMapper.builder().build(),work)){
            var interceptor=new SocketHandshakeInterceptor(hub,tickets);var raw=new MockHttpServletRequest("GET","/api/message/ws");raw.setQueryString("ticket=one");
            var request=new ServletServerHttpRequest(raw);var result=new MockHttpServletResponse();var response=new ServletServerHttpResponse(result);var attributes=new HashMap<String,Object>();
            when(tickets.consume("one")).thenThrow(new IllegalStateException());assertThatThrownBy(()->interceptor.beforeHandshake(request,response,hub,attributes)).isInstanceOf(IllegalStateException.class);assertThat(hub.snapshot().drained()).isTrue();
            doReturn(Optional.of(grant)).when(tickets).consume("one");when(tickets.active(grant)).thenReturn(true);
            assertThat(interceptor.beforeHandshake(request,response,hub,attributes)).isTrue();result.setStatus(101);interceptor.afterHandshake(request,response,hub,null);assertThat(hub.snapshot().handshakes()).isOne();
            var socket=mock(WebSocketSession.class);when(socket.getAttributes()).thenReturn(attributes);when(socket.getId()).thenReturn("one");
            hub.afterConnectionEstablished(socket);assertThat(hub.snapshot().handshakes()).isZero();assertThat(hub.snapshot().connections()).isOne();hub.afterConnectionClosed(socket,org.springframework.web.socket.CloseStatus.NORMAL);
        }
    }
}
