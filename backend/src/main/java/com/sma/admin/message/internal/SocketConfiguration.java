package com.sma.admin.message.internal;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.*;
import com.sma.admin.message.internal.service.*;

@Configuration(proxyBeanMethods=false) @EnableWebSocket @EnableScheduling
class SocketConfiguration implements WebSocketConfigurer {
 private final SocketHub hub;private final SocketTicketService tickets;private final SocketProperties properties;
 SocketConfiguration(SocketHub hub,SocketTicketService tickets,SocketProperties properties){this.hub=hub;this.tickets=tickets;this.properties=properties;}
 public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){
  registry.addHandler(hub,"/api/message/ws").setAllowedOrigins(properties.allowedOrigins().toArray(String[]::new)).addInterceptors(new SocketHandshakeInterceptor(hub,tickets));
 }
}
