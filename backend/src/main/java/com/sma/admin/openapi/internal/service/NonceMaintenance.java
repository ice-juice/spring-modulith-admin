package com.sma.admin.openapi.internal.service;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.OptionalInt;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.properties.*;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.admin.common.deployment.*;
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
 private final ExecutorService worker;private final DeploymentWork admission;
 private final AtomicBoolean running=new AtomicBoolean();private volatile boolean closed;private long cursor;
 @org.springframework.beans.factory.annotation.Autowired
 public NonceMaintenance(NonceMapper nonces,TenantWorkQuery tenants,Settings settings,PlatformTransactionManager manager,DeploymentWork admission){
  this(nonces,tenants,settings,manager,admission,new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),
   Thread.ofPlatform().daemon().inheritInheritableThreadLocals(false).name("sma-nonce-maintenance-",0).factory(),new ThreadPoolExecutor.AbortPolicy()));
 }
 NonceMaintenance(NonceMapper nonces,TenantWorkQuery tenants,Settings settings,PlatformTransactionManager manager,DeploymentWork admission,ExecutorService worker){
  this.nonces=nonces;this.tenants=tenants;this.settings=settings;this.transactions=new TransactionTemplate(manager);
  this.admission=admission;this.worker=worker;
  this.transactions.setTimeout(3);this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
 }
 /** Existing verifier calls are harmless; discovery never relies on a node having seen a tenant. */
 public void seen(){}
 @Scheduled(fixedDelayString="${sma.openapi.nonce-maintenance.poll-millis:10000}") public void purge(){
  DeploymentEvidence.requireHost();if(closed||!settings.enabled()||!admission.snapshot().accepting()||!running.compareAndSet(false,true))return;
  try{worker.execute(()->{try{pollOnce();}finally{running.set(false);}});}catch(RejectedExecutionException stopped){running.set(false);}
 }
 synchronized void pollOnce(){
  DeploymentEvidence.requireHost();if(closed||!settings.enabled())return;
  try{
   var discovery=admission.tryStart();if(discovery.isEmpty())return;
   java.util.List<TenantWorkQuery.Entry> page;
   try(var permit=discovery.orElseThrow()){page=tenants.page(cursor,settings.tenantPageSize());}
   if(page.isEmpty()){cursor=0;return;}
   long began=System.nanoTime();
   for(var entry:page){
    try{if(cleanAttempt(entry.tenantId()).isEmpty())return;}catch(RuntimeException failure){log.debug("Could not clean expired application nonces for tenant {}",entry.tenantId());}
    cursor=entry.tenantId();
    if(closed||System.nanoTime()-began>TimeUnit.SECONDS.toNanos(2))return;
   }
   if(page.size()<settings.tenantPageSize())cursor=0;
  }catch(RuntimeException unavailable){log.warn("Nonce maintenance tenant discovery unavailable");}
 }
 public int cleanTenant(long tenant){DeploymentEvidence.requireHost();return cleanAttempt(tenant).orElse(0);}
 private OptionalInt cleanAttempt(long tenant){
  if(closed)return OptionalInt.empty();var accepted=admission.tryStart();if(accepted.isEmpty())return OptionalInt.empty();
  try(var permit=accepted.orElseThrow()){return OptionalInt.of(ActorContext.withinJob(tenant,"openapi-nonce-maintenance",()->transactions.execute(status->nonces.purge())));}
 }
 @Override @PreDestroy public void close(){closed=true;worker.shutdownNow();}
}
