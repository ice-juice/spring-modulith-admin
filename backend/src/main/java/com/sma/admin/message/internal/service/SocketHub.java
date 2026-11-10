package com.sma.admin.message.internal.service;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SocketHub extends TextWebSocketHandler {
 private static final Logger log=LoggerFactory.getLogger(SocketHub.class);
 private final SocketTicketService tickets;private final SocketProperties properties;private final JsonMapper json;
 private final Map<String,Connection> connections=new ConcurrentHashMap<>();
 private record Connection(WebSocketSession session,SocketTicketService.Grant grant){}
 public SocketHub(SocketTicketService tickets,SocketProperties properties,JsonMapper json){this.tickets=tickets;this.properties=properties;this.json=json;}
 @Override public synchronized void afterConnectionEstablished(WebSocketSession session)throws Exception{
  var grant=(SocketTicketService.Grant)session.getAttributes().get("sma.socket-grant");
  if(grant==null||!tickets.active(grant)||connections.size()>=properties.maxConnections()){session.close(CloseStatus.POLICY_VIOLATION);return;}
  long sessionCount=connections.values().stream().filter(c->c.grant().tenantId()==grant.tenantId()&&c.grant().sessionId()==grant.sessionId()).count();
  if(sessionCount>=8){session.close(CloseStatus.POLICY_VIOLATION);return;}
  var decorated=new ConcurrentWebSocketSessionDecorator(session,5000,64*1024);
  var connection=new Connection(decorated,grant);connections.put(session.getId(),connection);send(connection,Map.of("type","connected"));
 }
 @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status){connections.remove(session.getId());}
 @Override public void handleTransportError(WebSocketSession session,Throwable error){close(session.getId(),"TRANSPORT_ERROR");}
 @Override protected void handleTextMessage(WebSocketSession session,TextMessage message)throws Exception{session.close(CloseStatus.NOT_ACCEPTABLE);}
 public void push(long tenantId,String portal,Set<Long> userIds,String type,long id){
  for(var entry:connections.entrySet()){
   var connection=entry.getValue();var grant=connection.grant();
   if(grant.tenantId()!=tenantId||!grant.portal().equals(portal)||!userIds.contains(grant.userId()))continue;
   if(!tickets.active(grant)){close(entry.getKey(),"SESSION_INVALID");continue;}
   send(connection,Map.of("type",type,"id",Long.toString(id)));
  }
 }
 public void endSession(long tenantId,long sessionId,String reason){for(var entry:connections.entrySet())if(entry.getValue().grant().tenantId()==tenantId&&entry.getValue().grant().sessionId()==sessionId)close(entry.getKey(),reason);}
 @Scheduled(fixedDelay=30000) public void validateConnections(){
  for(var entry:connections.entrySet())try{if(!tickets.active(entry.getValue().grant()))close(entry.getKey(),"SESSION_INVALID");else entry.getValue().session().sendMessage(new PingMessage());}
  catch(Exception failure){close(entry.getKey(),"TRANSPORT_ERROR");}
 }
 private void send(Connection connection,Map<String,String> body){
  try{connection.session().sendMessage(new TextMessage(json.writeValueAsString(body)));}
  catch(Exception failure){log.debug("Socket delivery failed for connection {}",connection.session().getId());close(connection.session().getId(),"TRANSPORT_ERROR");}
 }
 private void close(String id,String reason){
  var connection=connections.remove(id);if(connection==null)return;
  try{if(connection.session().isOpen()){connection.session().sendMessage(new TextMessage(json.writeValueAsString(Map.of("type","session-ended","reason",reason))));connection.session().close(CloseStatus.POLICY_VIOLATION);}}
  catch(Exception ignored){try{connection.session().close();}catch(IOException closeFailure){log.debug("Could not close socket {}",id);}}
 }
}
