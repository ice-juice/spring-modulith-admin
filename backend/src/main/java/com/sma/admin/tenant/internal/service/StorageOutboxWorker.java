package com.sma.admin.tenant.internal.service;

import com.sma.admin.common.request.BusinessOutboxConsumer;
import com.sma.admin.common.deployment.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded polling over pools already opened by business work. */
@Component
public class StorageOutboxWorker implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(StorageOutboxWorker.class);
    private final StoragePoolRegistry pools;
    private final List<BusinessOutboxConsumer> consumers;
    private int cursor;
    private final DeploymentWork admission;
    private final ExecutorService worker;
    private final AtomicBoolean running=new AtomicBoolean();
    private volatile boolean closed;

    @org.springframework.beans.factory.annotation.Autowired
    public StorageOutboxWorker(StoragePoolRegistry pools, List<BusinessOutboxConsumer> consumers, DeploymentWork admission) {
        this(pools,consumers,admission,new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),
                Thread.ofPlatform().daemon().inheritInheritableThreadLocals(false).name("sma-storage-outbox-",0).factory(),new ThreadPoolExecutor.AbortPolicy()));
    }
    StorageOutboxWorker(StoragePoolRegistry pools,List<BusinessOutboxConsumer> consumers,DeploymentWork admission,ExecutorService worker){
        this.pools = pools; this.consumers = List.copyOf(consumers);this.admission=admission;this.worker=worker;
    }

    @Scheduled(fixedDelay = 1000L)
    public void tick() {
        DeploymentEvidence.requireHost();
        if(closed||consumers.isEmpty()||!admission.snapshot().accepting()||!running.compareAndSet(false,true))return;
        try{worker.execute(()->{try{pollOnce();}finally{running.set(false);}});}
        catch(RejectedExecutionException stopped){running.set(false);}
    }
    synchronized void pollOnce(){
        DeploymentEvidence.requireHost();if(closed||consumers.isEmpty())return;
        var discovery=admission.tryStart();if(discovery.isEmpty())return;
        List<String> keys;
        try(var permit=discovery.orElseThrow()){keys=pools.activeResourceKeys();}
        if (keys.isEmpty()) return;
        int count = Math.min(keys.size(), 8);
        cursor=Math.floorMod(cursor,keys.size());
        for (int i = 0; i < count; i++) {
            if(closed)return;
            var key = keys.get(cursor);
            var split = key.lastIndexOf(':');
            try { if(pools.deliverNext(key.substring(0, split), Long.parseLong(key.substring(split + 1)), consumers).isEmpty())return; }
            catch (RuntimeException unavailable) { log.warn("Business outbox delivery deferred for resource {}", key); }
            cursor=(cursor+1)%keys.size();
        }
    }
    @Override @PreDestroy public void close(){closed=true;worker.shutdownNow();}
}
