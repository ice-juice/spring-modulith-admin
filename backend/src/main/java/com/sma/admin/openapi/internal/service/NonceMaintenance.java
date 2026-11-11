package com.sma.admin.openapi.internal.service;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.properties.*;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import com.sma.admin.common.security.*;
import com.sma.admin.openapi.internal.persistence.NonceMapper;
@Component @EnableConfigurationProperties(NonceMaintenance.Settings.class)
public class NonceMaintenance implements AutoCloseable {
 @ConfigurationProperties("sma.openapi.nonce-maintenance")
 public record Settings(Boolean enabled,Integer tenantPageSize,Long pollMillis){
  public Settings{enabled=enabled==null?true:enabled;tenantPageSize=tenantPageSize==null?8:tenantPageSize;pollMillis=pollMillis==null?10000L:pollMillis;
   if(tenantPageSize<1||tenantPageSize>50||pollMillis<1000||pollMillis>300000)throw new IllegalArgumentException("Invalid nonce maintenance bounds");}
 }
 private static final Logger log=LoggerFactory.getLogger(NonceMaintenance.class);
 private final NonceMapper nonces;private final TenantWorkQuery tenants;private final Settings settings;private final TransactionTemplate transactions;
 private final ExecutorService worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),Thread.ofPlatform().daemon().name("sma-nonce-maintenance-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
 private final AtomicBoolean running=new AtomicBoolean();private volatile boolean closed;private long cursor;
 public NonceMaintenance(NonceMapper nonces,TenantWorkQuery tenants,Settings settings,PlatformTransactionManager manager){
  this.nonces=nonces;this.tenants=tenants;this.settings=settings;this.transactions=new TransactionTemplate(manager);
  this.transactions.setTimeout(3);this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
 }
 /** Existing verifier calls are harmless; discovery never relies on a node having seen a tenant. */
 public void seen(){}
 @Scheduled(fixedDelayString="${sma.openapi.nonce-maintenance.poll-millis:10000}") public void purge(){
  host();if(closed||!settings.enabled()||!running.compareAndSet(false,true))return;
  try{worker.execute(()->{try{pollOnce();}finally{running.set(false);}});}catch(RejectedExecutionException stopped){running.set(false);}
 }
 synchronized void pollOnce(){
  host();if(closed||!settings.enabled())return;
  try{
   var page=tenants.page(cursor,settings.tenantPageSize());if(page.isEmpty()){cursor=0;return;}
   long began=System.nanoTime();
   for(var entry:page){cursor=entry.tenantId();
    try{cleanTenant(entry.tenantId());}catch(RuntimeException failure){log.debug("Could not clean expired application nonces for tenant {}",entry.tenantId());}
    if(closed||System.nanoTime()-began>TimeUnit.SECONDS.toNanos(2))return;
   }
   if(page.size()<settings.tenantPageSize())cursor=0;
  }catch(RuntimeException unavailable){log.warn("Nonce maintenance tenant discovery unavailable");}
 }
 public int cleanTenant(long tenant){return ActorContext.withinJob(tenant,"openapi-nonce-maintenance",()->transactions.execute(status->nonces.purge()));}
 private static void host(){if(SecurityContextHolder.getContext().getAuthentication()!=null)throw new AccessDeniedException("Host nonce maintenance required");}
 @Override @PreDestroy public void close(){closed=true;worker.shutdownNow();}
}
