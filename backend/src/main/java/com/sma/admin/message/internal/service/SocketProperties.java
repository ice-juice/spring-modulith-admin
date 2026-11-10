package com.sma.admin.message.internal.service;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("sma.message.socket")
public record SocketProperties(Duration ticketTtl,List<String> allowedOrigins,int maxConnections){
 public SocketProperties{
  ticketTtl=ticketTtl==null?Duration.ofSeconds(30):ticketTtl;
  if(ticketTtl.isNegative()||ticketTtl.isZero()||ticketTtl.compareTo(Duration.ofMinutes(1))>0)throw new IllegalArgumentException("Ticket TTL must be within one minute");
  allowedOrigins=allowedOrigins==null?List.of("http://localhost:5173","http://127.0.0.1:5173"):List.copyOf(allowedOrigins);
  if(allowedOrigins.contains("*"))throw new IllegalArgumentException("Socket origins must be explicit");
  maxConnections=maxConnections<=0?10000:maxConnections;
 }
}
