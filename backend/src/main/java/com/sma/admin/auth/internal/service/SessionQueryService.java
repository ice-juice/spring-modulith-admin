package com.sma.admin.auth.internal.service;
import java.time.Instant;
import org.springframework.stereotype.Service;
import com.sma.admin.auth.SessionQuery;
import com.sma.admin.auth.internal.persistence.SessionMapper;
import com.sma.admin.iam.AccountQuery;
@Service
public class SessionQueryService implements SessionQuery {
 private final SessionMapper sessions;private final AccountQuery accounts;
 public SessionQueryService(SessionMapper sessions,AccountQuery accounts){this.sessions=sessions;this.accounts=accounts;}
 public boolean active(long sessionId,long userId,String portal){
  var session=sessions.selectById(sessionId);
  if(session==null||session.getUserId()!=userId||!session.getPortal().equals(portal)||session.getLocked()||!session.getExpiresAt().isAfter(Instant.now()))return false;
  return accounts.findById(userId).filter(user->user.enabled()&&user.portal().equals(portal)&&user.credentialVersion()==session.getCredentialVersion()).isPresent();
 }
}
