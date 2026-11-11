package com.sma.admin.common.internal.cache;

import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import jakarta.annotation.PreDestroy;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import com.sma.admin.common.cache.CacheTaskRejected;
import com.sma.admin.common.internal.service.CacheNodeLeaseStore;
import com.sma.admin.common.security.*;

@Component
public class CacheNodeRuntime {
    private static final Logger log=LoggerFactory.getLogger(CacheNodeRuntime.class);
    private final CacheNodeIdentity identity;
    private final CacheNodeLeaseStore leases;
    private final CacheConfirmationProcessor processor;
    private final TenantWorkQuery tenants;
    private final ExecutorService heartbeat=worker("sma-cache-heartbeat-");
    private final ExecutorService confirmations=worker("sma-cache-confirmation-");
    private final AtomicBoolean heartbeating=new AtomicBoolean(),confirming=new AtomicBoolean();
    private final AtomicReference<CacheNodeLeaseStore.Lease> owned=new AtomicReference<>();
    private volatile String state="STARTING";
    private volatile Instant lastHeartbeat;
    private volatile long lastHeartbeatStart;
    private volatile boolean closed;
    private long afterTenant;
    public CacheNodeRuntime(CacheNodeIdentity identity,CacheNodeLeaseStore leases,CacheConfirmationProcessor processor,TenantWorkQuery tenants){
        this.identity=identity;this.leases=leases;this.processor=processor;this.tenants=tenants;
        if(!identity.properties().enabled())state="DISABLED";
    }
    public record Status(String state,Instant lastHeartbeatAt) {}
    public Status status(){return new Status(state.equals("OWNED")&&System.nanoTime()-lastHeartbeatStart>TimeUnit.SECONDS.toNanos(30)?"STALE":state,lastHeartbeat);}
    @Scheduled(fixedDelay=5000)
    public void heartbeatTick(){
        requireHost();
        if(closed||!identity.properties().enabled()||!heartbeating.compareAndSet(false,true))return;
        submit(heartbeat,heartbeating,()->{
            long started=System.nanoTime();
            try{var lease=leases.heartbeat(identity);if(!closed){owned.set(lease);lastHeartbeat=lease.until().minusSeconds(30);lastHeartbeatStart=started;state="OWNED";}}
            catch(RuntimeException failure){if(closed)return;owned.set(null);String next=failure instanceof CacheTaskRejected?"CONFLICT":"UNAVAILABLE";
                if(!state.equals(next))log.warn("Cache node membership {}; confirmations suspended",next);state=next;}
        });
    }
    @Scheduled(fixedDelay=1000)
    public void confirmationTick(){
        requireHost();
        if(closed||!identity.properties().enabled()||owned.get()==null||!confirming.compareAndSet(false,true))return;
        submit(confirmations,confirming,()->{
            try{
                var lease=owned.get();if(lease==null)return;
                var page=tenants.page(afterTenant,16);if(page.isEmpty()){afterTenant=0;return;}
                long started=System.nanoTime();
                for(var tenant:page){
                    afterTenant=tenant.tenantId();
                    ActorContext.withinJob(tenant.tenantId(),"cache-node:"+identity.processId(),()->processor.processTenant(lease.epoch()));
                    if(closed||owned.get()!=lease||System.nanoTime()-started>TimeUnit.SECONDS.toNanos(2))break;
                }
            }catch(RuntimeException unavailable){log.warn("Cache node confirmations deferred; durable requests are retained");}
        });
    }
    private static ExecutorService worker(String name){return new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),
            Thread.ofPlatform().daemon().name(name,0).factory(),new ThreadPoolExecutor.AbortPolicy());}
    private void submit(ExecutorService worker,AtomicBoolean busy,Runnable action){
        try{worker.execute(()->{try{action.run();}finally{busy.set(false);}});}catch(RejectedExecutionException closed){busy.set(false);}
    }
    @PreDestroy public void close(){closed=true;owned.set(null);state="STOPPED";heartbeat.shutdownNow();confirmations.shutdownNow();}
    private void requireHost(){if(SecurityContextHolder.getContext().getAuthentication()!=null)throw new AccessDeniedException("Host scheduler required");}
}
