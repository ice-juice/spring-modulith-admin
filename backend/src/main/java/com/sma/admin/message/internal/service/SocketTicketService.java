package com.sma.admin.message.internal.service;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import com.sma.admin.auth.SessionQuery;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;

@Service @EnableConfigurationProperties(SocketProperties.class)
public class SocketTicketService {
 private final SessionQuery sessions;private final SocketProperties properties;private final Clock clock;
 private final SecureRandom random=new SecureRandom();private final Map<String,Grant> grants=new ConcurrentHashMap<>();
 @Autowired public SocketTicketService(SessionQuery sessions,SocketProperties properties){this(sessions,properties,Clock.systemUTC());}
 SocketTicketService(SessionQuery sessions,SocketProperties properties,Clock clock){this.sessions=sessions;this.properties=properties;this.clock=clock;}
 public record Grant(long tenantId,String portal,long userId,long sessionId,Instant expiresAt){}
 public record Ticket(String ticket,Instant expiresAt){}
 public synchronized Ticket issue(long sessionId){
  var user=CurrentUser.require();if(!sessions.active(sessionId,user.userId(),user.portal()))throw new BizException(CommonErrorCode.UNAUTHORIZED);
  grants.values().removeIf(grant->!grant.expiresAt().isAfter(clock.instant())||(grant.tenantId()==user.tenantId()&&grant.sessionId()==sessionId));
  if(grants.size()>=properties.maxConnections())throw new BizException(com.sma.admin.message.MessageErrorCode.SOCKET_BUSY);
  byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  Instant expires=clock.instant().plus(properties.ticketTtl());grants.put(hash(token),new Grant(user.tenantId(),user.portal(),user.userId(),sessionId,expires));return new Ticket(token,expires);
 }
 public Optional<Grant> consume(String token){
  if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))return Optional.empty();
  var grant=grants.remove(hash(token));
  if(grant==null||!grant.expiresAt().isAfter(clock.instant())||!active(grant))return Optional.empty();return Optional.of(grant);
 }
 public boolean active(Grant grant){return TenantScope.within(grant.tenantId(),()->sessions.active(grant.sessionId(),grant.userId(),grant.portal()));}
 private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
