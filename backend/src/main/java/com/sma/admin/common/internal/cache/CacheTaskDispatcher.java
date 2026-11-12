package com.sma.admin.common.internal.cache;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sma.admin.common.internal.service.CacheTaskExecutor;
import com.sma.admin.common.deployment.DeploymentEvidence;
import com.sma.admin.common.deployment.DeploymentWork;
import com.sma.admin.common.security.*;

@Component
@EnableConfigurationProperties(CacheTaskDispatchProperties.class)
public class CacheTaskDispatcher {
    private static final Logger log=LoggerFactory.getLogger(CacheTaskDispatcher.class);
    private final CacheTaskExecutor executor;
    private final TenantWorkQuery tenants;
    private final CacheTaskDispatchProperties properties;
    private final String workerId;
    private final DeploymentWork admission;
    private final AtomicBoolean running=new AtomicBoolean();
    private final ExecutorService workers;
    private volatile boolean closed;
    private long afterTenant;
    @org.springframework.beans.factory.annotation.Autowired
    public CacheTaskDispatcher(CacheTaskExecutor executor,TenantWorkQuery tenants,CacheTaskDispatchProperties properties,CacheNodeIdentity identity,DeploymentWork admission){
        this(executor,tenants,properties,identity,admission,new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),
                Thread.ofPlatform().daemon().inheritInheritableThreadLocals(false).name("sma-cache-tasks-",0).factory(),new ThreadPoolExecutor.AbortPolicy()));
    }
    CacheTaskDispatcher(CacheTaskExecutor executor,TenantWorkQuery tenants,CacheTaskDispatchProperties properties,CacheNodeIdentity identity,DeploymentWork admission,ExecutorService workers){
        this.executor=executor;this.tenants=tenants;this.properties=properties;
        this.workerId=identity.processId();this.admission=admission;this.workers=workers;
    }
    @Scheduled(fixedDelay=1000)
    public void tick(){
        DeploymentEvidence.requireHost();
        if(closed||!properties.enabled()||!admission.snapshot().accepting()||!running.compareAndSet(false,true))return;
        try{workers.execute(()->{try{cycle();}finally{running.set(false);}});}
        catch(RejectedExecutionException stopped){running.set(false);}
    }
    private void cycle(){
        try{
            if(closed)return;
            var discovery=admission.tryStart();if(discovery.isEmpty())return;
            List<TenantWorkQuery.Entry> page;
            try(var permit=discovery.orElseThrow()){page=tenants.page(afterTenant,properties.tenantsPerTick());}
            if(page.isEmpty()){afterTenant=0;return;}
            for(var tenant:page){
                // Inactive tenants are visited too so accepted work is explicitly denied, not silently stranded.
                for(int i=0;i<properties.itemsPerTenant();i++){
                    if(closed)return;
                    var accepted=admission.tryStart();if(accepted.isEmpty())return;
                    try(var permit=accepted.orElseThrow()){
                        var lease=ActorContext.withinJob(tenant.tenantId(),"cache-dispatch",()->executor.claim(workerId));
                        if(lease.isEmpty())break;
                        ActorContext.withinJob(tenant.tenantId(),"cache-task:"+lease.get().taskId(),()->executor.executeOne(lease.get()));
                    }catch(RuntimeException unavailable){log.warn("Cache task dispatch deferred; persisted leases and results will be rechecked");break;}
                }
                // An admission refusal must not skip this tenant when qualification later recovers.
                afterTenant=tenant.tenantId();
            }
        }catch(RuntimeException unavailable){log.warn("Cache tenant work directory unavailable; dispatch deferred");}
    }
    @PreDestroy public void close(){closed=true;workers.shutdownNow();}
}
