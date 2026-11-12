package com.sma.admin.defense.internal.service;

import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import com.sma.admin.common.deployment.*;
import com.sma.admin.common.security.TenantWorkQuery;
import io.micrometer.core.instrument.*;

@Component @EnableConfigurationProperties(DefenseMaintenanceProperties.class)
public class SecurityRetentionService {
    private final DefenseMaintenanceProperties settings;private final DeploymentWork admission;private final SecurityRetentionCycle worker;
    private final ExecutorService executor;private final AtomicBoolean running=new AtomicBoolean();
    private volatile boolean closed;private volatile long lastCycle;
    @org.springframework.beans.factory.annotation.Autowired
    public SecurityRetentionService(DefenseMaintenanceProperties settings,DeploymentWork admission,TenantWorkQuery directory,SecurityEventStore store,MeterRegistry meters){
        this(settings,admission,new SecurityRetentionCycle(settings,admission,directory,store::prunePlatform,store::pruneTenant),
                new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),
                        Thread.ofPlatform().daemon().inheritInheritableThreadLocals(false).name("sma-security-retention-",0).factory(),new ThreadPoolExecutor.AbortPolicy()),meters);
    }
    SecurityRetentionService(DefenseMaintenanceProperties settings,DeploymentWork admission,SecurityRetentionCycle worker,ExecutorService executor,MeterRegistry meters){
        this.settings=settings;this.admission=admission;this.worker=worker;this.executor=executor;
        Gauge.builder("sma.defense.maintenance.enabled",settings,value->value.enabled()?1:0).register(meters);
        Gauge.builder("sma.defense.maintenance.running",running,value->value.get()?1:0).register(meters);
        Gauge.builder("sma.defense.maintenance.last-cycle",this,value->value.lastCycle).register(meters);
        counter(meters,"cycle",worker.cycles);counter(meters,"platform-deleted",worker.platformDeleted);counter(meters,"tenant-deleted",worker.tenantDeleted);
        counter(meters,"failure",worker.failures);counter(meters,"denied",worker.denied);
    }
    private void counter(MeterRegistry meters,String result,java.util.concurrent.atomic.LongAdder value){
        FunctionCounter.builder("sma.defense.maintenance",value,java.util.concurrent.atomic.LongAdder::doubleValue).tag("result",result).register(meters);
    }
    @Scheduled(fixedDelayString="${sma.defense.maintenance.interval:PT30S}",initialDelayString="${sma.defense.maintenance.interval:PT30S}")
    public void tick(){
        DeploymentEvidence.requireHost();if(closed||!settings.enabled())return;
        try{if(!admission.snapshot().accepting()){worker.denied.increment();return;}}
        catch(RuntimeException unavailable){worker.failures.increment();return;}
        if(!running.compareAndSet(false,true))return;
        try{executor.execute(()->{
            try{if(!closed)worker.cycle();}
            catch(RuntimeException failed){worker.failures.increment();}
            finally{lastCycle=Instant.now().getEpochSecond();running.set(false);}
        });}catch(RejectedExecutionException stopped){running.set(false);}
    }
    @PreDestroy public void close(){
        closed=true;worker.stop();executor.shutdown();
        // Never cancel a live transaction or release its permit merely because this observation period ended.
        try{executor.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
    }
}
