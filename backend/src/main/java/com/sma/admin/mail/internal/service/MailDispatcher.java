package com.sma.admin.mail.internal.service;

import java.util.Set;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import com.sma.admin.common.security.TenantWorkQuery;
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
    public MailDispatcher(TenantWorkQuery tenants,MailDispatchCycle cycle,MailDispatchProperties settings,
                          @Value("${sma.mail.dispatch.poll-millis:5000}")long pollMillis){
        this.tenants=tenants;this.cycle=cycle;this.settings=settings;
        if(pollMillis<250 || pollMillis>300000)throw new IllegalArgumentException("Invalid mail polling interval");
        workers=new ThreadPoolExecutor(settings.concurrency(),settings.concurrency(),0,TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(settings.tenantPageSize()),Thread.ofPlatform().daemon().name("sma-mail-dispatch-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
    }
    @Scheduled(fixedDelayString="${sma.mail.dispatch.poll-millis:5000}")
    public synchronized void poll(){
        if(!settings.enabled())return;
        try{
            var page=tenants.page(cursor,settings.tenantPageSize());
            if(page.isEmpty()){cursor=0;return;}
            for(var tenant:page){
                if(!inFlight.add(tenant.tenantId())){cursor=tenant.tenantId();continue;}
                try{workers.execute(()->{
                    try{cycle.runTenant(tenant.tenantId());}
                    catch(RuntimeException failure){log.warn("Mail dispatch failed for tenant {}; persistent tasks will be recovered",tenant.tenantId());}
                    finally{inFlight.remove(tenant.tenantId());}
                });cursor=tenant.tenantId();}
                catch(RejectedExecutionException saturated){inFlight.remove(tenant.tenantId());return;}
            }
            if(page.size()<settings.tenantPageSize())cursor=0;
        }catch(RuntimeException unavailable){log.warn("Mail tenant discovery unavailable; no dispatch performed");}
    }
    @PreDestroy public void close(){workers.shutdownNow();}
}
