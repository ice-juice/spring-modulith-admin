package com.sma.admin.mail.internal.service;

import java.util.Set;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import com.sma.admin.common.security.TenantWorkQuery;
import com.sma.admin.common.deployment.DeploymentEvidence;
import com.sma.admin.common.deployment.DeploymentWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded executor, persistent discovery, no per-tenant permanent threads or in-memory-only registration. */
@Component @EnableConfigurationProperties(MailDispatchProperties.class)
public class MailDispatcher {
    private static final Logger log=LoggerFactory.getLogger(MailDispatcher.class);
    private final TenantWorkQuery tenants;private final MailDispatchCycle cycle;private final MailDispatchProperties settings;
    private final ExecutorService workers;private final Set<Long> inFlight=ConcurrentHashMap.newKeySet();private long cursor;
    private final DeploymentWork admission;private volatile boolean closed;
    @org.springframework.beans.factory.annotation.Autowired
    public MailDispatcher(TenantWorkQuery tenants,MailDispatchCycle cycle,MailDispatchProperties settings,
                          @Value("${sma.mail.dispatch.poll-millis:5000}")long pollMillis,DeploymentWork admission){
        this(tenants,cycle,settings,pollMillis,admission,new ThreadPoolExecutor(settings.concurrency(),settings.concurrency(),0,TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(settings.tenantPageSize()),Thread.ofPlatform().daemon().inheritInheritableThreadLocals(false)
                .name("sma-mail-dispatch-",0).factory(),new ThreadPoolExecutor.AbortPolicy()));
    }
    MailDispatcher(TenantWorkQuery tenants,MailDispatchCycle cycle,MailDispatchProperties settings,long pollMillis,DeploymentWork admission,ExecutorService workers){
        this.tenants=tenants;this.cycle=cycle;this.settings=settings;this.admission=admission;this.workers=workers;
        if(pollMillis<250 || pollMillis>300000)throw new IllegalArgumentException("Invalid mail polling interval");
    }
    @Scheduled(fixedDelayString="${sma.mail.dispatch.poll-millis:5000}")
    public synchronized void poll(){
        DeploymentEvidence.requireHost();
        if(closed||!settings.enabled()||!admission.snapshot().accepting())return;
        try{
            var discovery=admission.tryStart();if(discovery.isEmpty())return;
            java.util.List<TenantWorkQuery.Entry> page;
            try(var permit=discovery.orElseThrow()){page=tenants.page(cursor,settings.tenantPageSize());}
            if(page.isEmpty()){cursor=0;return;}
            for(var tenant:page){
                if(!inFlight.add(tenant.tenantId())){cursor=tenant.tenantId();continue;}
                try{workers.execute(()->{
                    try{if(!closed&&admission.snapshot().accepting())cycle.runTenant(tenant.tenantId());}
                    catch(RuntimeException failure){log.warn("Mail dispatch failed for tenant {}; persistent tasks will be recovered",tenant.tenantId());}
                    finally{inFlight.remove(tenant.tenantId());}
                });cursor=tenant.tenantId();}
                catch(RejectedExecutionException saturated){inFlight.remove(tenant.tenantId());return;}
            }
            if(page.size()<settings.tenantPageSize())cursor=0;
        }catch(RuntimeException unavailable){log.warn("Mail tenant discovery unavailable; no dispatch performed");}
    }
    @PreDestroy public void close(){closed=true;workers.shutdownNow();}
}
