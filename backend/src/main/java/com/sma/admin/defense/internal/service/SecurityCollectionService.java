package com.sma.admin.defense.internal.service;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import com.sma.admin.common.deployment.*;
import com.sma.admin.common.security.*;
import io.micrometer.core.instrument.*;
import tools.jackson.databind.json.JsonMapper;

@Component @EnableConfigurationProperties(DefenseCollectionProperties.class)
public class SecurityCollectionService implements SecuritySignals {
    private final DefenseCollectionProperties settings;private final SecuritySignalCapture capture;private final DeploymentWork admission;
    private final SecurityCollectionQueue queue;private final SecurityCollectionWorker worker;private final ExecutorService executor;
    private final AtomicBoolean running=new AtomicBoolean();private volatile boolean closed;
    public SecurityCollectionService(DefenseCollectionProperties settings,SecuritySignalCapture capture,DeploymentWork admission,SecurityEventStore store,
            JsonMapper json,MeterRegistry meters){
        this.settings=settings;this.capture=capture;this.admission=admission;queue=new SecurityCollectionQueue(settings,System::nanoTime);
        worker=new SecurityCollectionWorker(settings,queue,admission,store::append,json,System::nanoTime);
        executor=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),
                Thread.ofPlatform().daemon().inheritInheritableThreadLocals(false).name("sma-security-events-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
        Gauge.builder("sma.defense.collection.enabled",settings,value->value.enabled()?1:0).register(meters);
        Gauge.builder("sma.defense.queue.records",queue,SecurityCollectionQueue::size).register(meters);
        Gauge.builder("sma.defense.spool.available",worker,value->value.available()?1:0).register(meters);
        Gauge.builder("sma.defense.spool.records",worker,SecurityCollectionWorker::diskRecords).register(meters);
        Gauge.builder("sma.defense.spool.bytes",worker,SecurityCollectionWorker::diskBytes).register(meters);
        counter(meters,"queued",queue.queued);counter(meters,"disk-persisted",worker.persisted);counter(meters,"stored",worker.stored);
        counter(meters,"duplicate",worker.duplicates);counter(meters,"expired",worker.expired);counter(meters,"conflict",worker.conflicts);
        counter(meters,"capacity",worker.capacity);counter(meters,"failure",worker.failures);
        counter(meters,"drop-capacity",queue.droppedCapacity);counter(meters,"drop-rate",queue.droppedRate);counter(meters,"drop-contention",queue.droppedContention);
        counter(meters,"drop-capture",queue.droppedCapture);counter(meters,"drop-shutdown",queue.droppedShutdown);
    }
    private void counter(MeterRegistry meters,String result,java.util.concurrent.atomic.LongAdder counter){
        FunctionCounter.builder("sma.defense.collection",counter,java.util.concurrent.atomic.LongAdder::doubleValue).tag("result",result).register(meters);
    }
    @Override public Admission observe(SecuritySignal.Fact fact){
        if(!settings.enabled())return Admission.DISABLED;
        try{if(closed||!admission.snapshot().accepting()){queue.droppedShutdown.increment();return Admission.REJECTED;}}
        catch(RuntimeException unavailable){queue.droppedCapture.increment();return Admission.REJECTED;}
        return queue.offer(fact,capture);
    }
    @Scheduled(fixedDelay=250) public void tick(){
        DeploymentEvidence.requireHost();
        if(closed||!settings.enabled()||!admission.snapshot().accepting()||!running.compareAndSet(false,true))return;
        try{executor.execute(()->{try{if(!closed)worker.cycle();}finally{if(closed){queue.close();worker.close();}running.set(false);}});}
        catch(RejectedExecutionException stopped){running.set(false);}
    }
    @PreDestroy public void close(){
        closed=true;worker.stop();executor.shutdown();
        // Close the disk owner on the execution thread after admitted I/O actually exits; never clear a live permit.
        try{executor.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
        if(executor.isTerminated()){queue.close();worker.close();}
        // Late completion closes in the task's finally block below; shutdown never reports volatile items durable.
    }
}
