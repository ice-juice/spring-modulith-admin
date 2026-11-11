package com.sma.admin.message.internal.service;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.auth.SessionQuery;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.message.internal.persistence.*;

@Service @EnableConfigurationProperties(SocketProperties.class)
public class SocketTicketService {
 private final SessionQuery sessions;private final SocketProperties properties;
 private final SocketTicketMapper tickets;private final SocketTicketGuardMapper guards;private final TransactionTemplate writes;
 private final SecureRandom random=new SecureRandom();
 public SocketTicketService(SessionQuery sessions,SocketProperties properties,SocketTicketMapper tickets,SocketTicketGuardMapper guards,PlatformTransactionManager transactions){
  this.sessions=sessions;this.properties=properties;this.tickets=tickets;this.guards=guards;
  writes=new TransactionTemplate(transactions);writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);writes.setTimeout(5);
 }
 public record Grant(long tenantId,String portal,long userId,long sessionId,Instant expiresAt){}
 public record Ticket(String ticket,Instant expiresAt){}
 public Ticket issue(long sessionId){
  var user=CurrentUser.require();
  // Auth may own a separate authority transaction; do not hold ticket locks/connections across it.
  if(!sessions.active(sessionId,user.userId(),user.portal()))throw new BizException(CommonErrorCode.UNAUTHORIZED);
  var issued=writes.execute(status->{
   guards.ensure(IdWorker.getId(),user.tenantId());
   Objects.requireNonNull(guards.selectOne(new LambdaQueryWrapper<SocketTicketGuardEntity>().eq(SocketTicketGuardEntity::getTenantId,user.tenantId()).last("FOR UPDATE")),"Ticket capacity guard unavailable");
   tickets.removeExpired();
   var previous=tickets.selectOne(new LambdaQueryWrapper<SocketTicketEntity>().eq(SocketTicketEntity::getPortal,user.portal()).eq(SocketTicketEntity::getSessionId,sessionId));
   // Shared tenant capacity across portals/nodes; replacement cannot increase the number of grants.
   if(previous==null&&tickets.selectCount(new LambdaQueryWrapper<>())>=properties.maxConnections())return null;
   byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
   var expires=tickets.now().plus(properties.ticketTtl());tickets.removeSession(user.portal(),sessionId);
   var row=new SocketTicketEntity();row.setPortal(user.portal());row.setUserId(user.userId());row.setSessionId(sessionId);row.setTokenHash(hash(token));row.setExpiresAt(expires);
   tickets.insert(row);return new Ticket(token,expires);
  });
  // Commit bounded expiry cleanup even when a lowered capacity still rejects a new grant.
  if(issued==null)throw new BizException(com.sma.admin.message.MessageErrorCode.SOCKET_BUSY);
  return issued;
 }
 public Optional<Grant> consume(String token){
  if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))return Optional.empty();
  // Anonymous handshakes require a verified entry; internal calls require an authenticated owner.
  if(TenantEntryScope.optional().isEmpty()&&CurrentUser.optional().isEmpty())return Optional.empty();
  Grant grant=writes.execute(status->{
   String portal=CurrentUser.portal(),digest=hash(token);var row=tickets.usable(portal,digest);
   if(row==null||tickets.consume(row.getId(),portal,digest)!=1)return null;
   return new Grant(row.getTenantId(),row.getPortal(),row.getUserId(),row.getSessionId(),row.getExpiresAt());
  });
  // Commit consumption before consulting Auth. Authority failure never returns a grant or restores the ticket.
  return grant!=null&&active(grant)?Optional.of(grant):Optional.empty();
 }
 private boolean matchesEntry(Grant grant){return TenantEntryScope.optional().map(entry->entry.tenantId()==grant.tenantId()&&entry.portal().equals(grant.portal())).orElse(true);}
 public boolean active(Grant grant){return matchesEntry(grant)&&TenantScope.within(grant.tenantId(),()->sessions.active(grant.sessionId(),grant.userId(),grant.portal()));}
 private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
