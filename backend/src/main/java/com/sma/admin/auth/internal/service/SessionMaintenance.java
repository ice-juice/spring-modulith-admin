package com.sma.admin.auth.internal.service;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.properties.*;
import org.slf4j.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.auth.SessionEndedEvent;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.common.security.*;

@Service @EnableConfigurationProperties(SessionMaintenance.Settings.class)
public class SessionMaintenance implements AutoCloseable {
 @ConfigurationProperties("sma.auth.maintenance")
 public record Settings(Boolean enabled,Integer tenantPageSize,Long pollMillis){
  public Settings{enabled=enabled==null?true:enabled;tenantPageSize=tenantPageSize==null?8:tenantPageSize;pollMillis=pollMillis==null?10000L:pollMillis;
   if(tenantPageSize<1||tenantPageSize>50||pollMillis<1000||pollMillis>300000)throw new IllegalArgumentException("Invalid auth maintenance bounds");}
 }
 private static final Logger log=LoggerFactory.getLogger(SessionMaintenance.class);
 private final SessionMapper sessions;private final ApplicationEventPublisher events;private final TransactionTemplate transactions;
 private final SecurityMaintenance security;private final TenantWorkQuery tenants;private final Settings settings;
 private final ExecutorService worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),Thread.ofPlatform().daemon().name("sma-auth-maintenance-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
 private final AtomicBoolean running=new AtomicBoolean();private volatile boolean closed;private long cursor;
 public SessionMaintenance(SessionMapper sessions,ApplicationEventPublisher events,PlatformTransactionManager manager,SecurityMaintenance security,TenantWorkQuery tenants,Settings settings){
  this.sessions=sessions;this.events=events;this.transactions=new TransactionTemplate(manager);this.transactions.setTimeout(5);
  this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  this.security=security;this.tenants=tenants;this.settings=settings;
 }
 /** Compatibility hook for existing internal issuers; discovery no longer depends on traffic. */
 public void seen(){}
 @Scheduled(fixedDelayString="${sma.auth.maintenance.poll-millis:10000}") public void clean(){
  host();if(closed||!settings.enabled()||!running.compareAndSet(false,true))return;
  try{worker.execute(()->{try{pollOnce();}finally{running.set(false);}});}catch(RejectedExecutionException stopped){running.set(false);}
 }
 synchronized void pollOnce(){
  host();if(closed||!settings.enabled())return;
  try{
   var page=tenants.page(cursor,settings.tenantPageSize());if(page.isEmpty()){cursor=0;return;}
   long began=System.nanoTime();
   for(var entry:page){
    long tenant=entry.tenantId();cursor=tenant;
    try{cleanTenant(tenant);}catch(RuntimeException failure){log.warn("Could not expire sessions for tenant {}",tenant);}
    try{ActorContext.withinJob(tenant,"auth-maintenance:rotation",security::rotateEncryption);}
    catch(RuntimeException failure){log.warn("Auth key rotation failed for tenant {}; retain old keys and inspect credentials",tenant);}
    if(closed||System.nanoTime()-began>TimeUnit.SECONDS.toNanos(2))return;
   }
   if(page.size()<settings.tenantPageSize())cursor=0;
  }catch(RuntimeException unavailable){log.warn("Auth maintenance tenant discovery unavailable");}
 }
 public int cleanTenant(long tenant){return ActorContext.withinJob(tenant,"auth-maintenance:expiry",()->transactions.execute(status->{
  security.expire();
  var expired=sessions.selectList(new LambdaQueryWrapper<SessionEntity>().le(SessionEntity::getExpiresAt,sessions.now()).orderByAsc(SessionEntity::getId).last("LIMIT 200 FOR UPDATE SKIP LOCKED"));
  for(var session:expired){sessions.deleteById(session.getId());events.publishEvent(new SessionEndedEvent(tenant,session.getUserId(),session.getId(),"EXPIRED"));}return expired.size();
 }));}
 private static void host(){if(SecurityContextHolder.getContext().getAuthentication()!=null)throw new AccessDeniedException("Host auth maintenance required");}
 @Override @PreDestroy public void close(){closed=true;worker.shutdownNow();}
}
