package com.sma.admin.message.internal;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.*;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import com.sma.admin.message.internal.service.*;

final class SocketHandshakeInterceptor implements HandshakeInterceptor {
    private final SocketHub hub;
    private final SocketTicketService tickets;
    SocketHandshakeInterceptor(SocketHub hub,SocketTicketService tickets){this.hub=hub;this.tickets=tickets;}
    @Override public boolean beforeHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Map<String,Object> attributes){
        if(!(request instanceof ServletServerHttpRequest servlet)){response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);return false;}
        var reservation=hub.beginHandshake();
        if(reservation.isEmpty()){response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);return false;}
        var pending=reservation.orElseThrow();boolean accepted=false;
        try{
            var token=UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("ticket");
            var grant=tickets.consume(token);
            if(grant.isEmpty()){response.setStatusCode(HttpStatus.UNAUTHORIZED);return false;}
            attributes.put("sma.socket-grant",grant.orElseThrow());attributes.put(SocketHub.HANDSHAKE_ATTRIBUTE,pending);
            servlet.getServletRequest().setAttribute(SocketHub.HANDSHAKE_ATTRIBUTE,pending);
            accepted=true;return true;
        }finally{if(!accepted)pending.close();}
    }
    @Override public void afterHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Exception failure){
        if(!(request instanceof ServletServerHttpRequest servlet))return;
        var value=servlet.getServletRequest().getAttribute(SocketHub.HANDSHAKE_ATTRIBUTE);
        servlet.getServletRequest().removeAttribute(SocketHub.HANDSHAKE_ATTRIBUTE);
        if(value instanceof SocketHub.Handshake pending&&(failure!=null||!(response instanceof ServletServerHttpResponse result)||result.getServletResponse().getStatus()!=101))pending.close();
        // Successful upgrade retains the reservation until the connection callback takes ownership.
    }
}
