package com.sma.admin.common.internal.cache;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sma.admin.common.internal.service.CacheTaskExecutor;
import com.sma.admin.common.security.*;

@Component
@EnableConfigurationProperties(CacheTaskDispatchProperties.class)
public class CacheTaskDispatcher {
    private static final Logger log=LoggerFactory.getLogger(CacheTaskDispatcher.class);
    private final CacheTaskExecutor executor;
    private final TenantWorkQuery tenants;
    private final CacheTaskDispatchProperties properties;
    private final String workerId;
    private final AtomicBoolean running=new AtomicBoolean();
    private final ExecutorService workers=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),
            Thread.ofPlatform().daemon().name("sma-cache-tasks-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
    private long afterTenant;
    public CacheTaskDispatcher(CacheTaskExecutor executor,TenantWorkQuery tenants,CacheTaskDispatchProperties properties){
        this(executor,tenants,properties,new CacheNodeIdentity(new CacheNodeProperties(null,null,null,null)));
    }
    @org.springframework.beans.factory.annotation.Autowired
    public CacheTaskDispatcher(CacheTaskExecutor executor,TenantWorkQuery tenants,CacheTaskDispatchProperties properties,CacheNodeIdentity identity){
        this.executor=executor;this.tenants=tenants;this.properties=properties;
        this.workerId=identity.processId();
    }
    @Scheduled(fixedDelay=1000)
    public void tick(){
        if(!properties.enabled()||!running.compareAndSet(false,true))return;
        try{workers.execute(()->{try{cycle();}finally{running.set(false);}});}
        catch(RejectedExecutionException stopped){running.set(false);}
    }
    private void cycle(){
        try{
            var page=tenants.page(afterTenant,properties.tenantsPerTick());
            if(page.isEmpty()){afterTenant=0;return;}
            for(var tenant:page){
                afterTenant=tenant.tenantId();
                // Inactive tenants are visited too so accepted work is explicitly denied, not silently stranded.
                for(int i=0;i<properties.itemsPerTenant();i++){
                    try{
                        var lease=ActorContext.withinJob(tenant.tenantId(),"cache-dispatch",()->executor.claim(workerId));
                        if(lease.isEmpty())break;
                        ActorContext.withinJob(tenant.tenantId(),"cache-task:"+lease.get().taskId(),()->executor.executeOne(lease.get()));
                    }catch(RuntimeException unavailable){log.warn("Cache task dispatch deferred; persisted leases and results will be rechecked");break;}
                }
            }
        }catch(RuntimeException unavailable){log.warn("Cache tenant work directory unavailable; dispatch deferred");}
    }
    @PreDestroy public void close(){workers.shutdownNow();}
}
