package com.sma.admin.auth.internal.service;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.auth.SessionEndedEvent;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.common.security.*;

@Service
public class SessionMaintenance {
 private static final Logger log=LoggerFactory.getLogger(SessionMaintenance.class);
 private final Set<Long> tenants=ConcurrentHashMap.newKeySet();private final SessionMapper sessions;private final ApplicationEventPublisher events;private final TransactionTemplate transactions;
 public SessionMaintenance(SessionMapper sessions,ApplicationEventPublisher events,PlatformTransactionManager manager){this.sessions=sessions;this.events=events;this.transactions=new TransactionTemplate(manager);tenants.add(1L);}
 public void seen(){tenants.add(CurrentUser.tenantId());}
 @Scheduled(fixedDelay=60000) public void clean(){for(long tenant:tenants)try{cleanTenant(tenant);}catch(org.springframework.dao.DataAccessException failure){log.debug("Could not expire sessions for tenant {}",tenant);}}
 public int cleanTenant(long tenant){return TenantScope.within(tenant,()->transactions.execute(status->{
  var expired=sessions.selectList(new LambdaQueryWrapper<SessionEntity>().le(SessionEntity::getExpiresAt,Instant.now()).orderByAsc(SessionEntity::getId).last("LIMIT 200 FOR UPDATE"));
  for(var session:expired){sessions.deleteById(session.getId());events.publishEvent(new SessionEndedEvent(tenant,session.getUserId(),session.getId(),"EXPIRED"));}return expired.size();
 }));}
}
