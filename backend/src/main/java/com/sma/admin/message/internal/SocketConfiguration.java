package com.sma.admin.message.internal;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.http.*;
import org.springframework.http.server.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import com.sma.admin.message.internal.service.*;

@Configuration(proxyBeanMethods=false) @EnableWebSocket @EnableScheduling
class SocketConfiguration implements WebSocketConfigurer {
 private final SocketHub hub;private final SocketTicketService tickets;private final SocketProperties properties;
 SocketConfiguration(SocketHub hub,SocketTicketService tickets,SocketProperties properties){this.hub=hub;this.tickets=tickets;this.properties=properties;}
 public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){
  registry.addHandler(hub,"/api/message/ws").setAllowedOrigins(properties.allowedOrigins().toArray(String[]::new)).addInterceptors(new HandshakeInterceptor(){
   public boolean beforeHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Map<String,Object> attributes){
    String token=UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("ticket");
    var grant=tickets.consume(token);if(grant.isEmpty()){response.setStatusCode(HttpStatus.UNAUTHORIZED);return false;}
    attributes.put("sma.socket-grant",grant.get());return true;
   }
   public void afterHandshake(ServerHttpRequest request,ServerHttpResponse response,WebSocketHandler handler,Exception failure){}
  });
 }
}
