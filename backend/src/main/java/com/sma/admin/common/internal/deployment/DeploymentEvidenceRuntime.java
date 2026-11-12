package com.sma.admin.common.internal.deployment;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.LongSupplier;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import com.sma.admin.common.deployment.DeploymentEvidence;
import com.sma.admin.common.internal.cache.CacheNodeIdentity;
import com.sma.admin.common.internal.cache.CacheNodeRuntime;
import com.sma.admin.common.internal.service.DeploymentEvidenceStore;

/** One non-queuing worker. Slow evidence cannot block membership renewals or retain local qualification. */
public final class DeploymentEvidenceRuntime {
    public record Status(String state) { public boolean matches(){return state.equals("MATCH");} }
    private record Observation(String state,long epoch,long startedAt) { }
    private final CacheNodeIdentity identity;
    private final CacheNodeRuntime membership;
    private final DeploymentEvidenceStore store;
    private final DeploymentEvidenceRegistry registry;
    private final LongSupplier clock;
    private final ExecutorService worker;
    private final AtomicBoolean busy=new AtomicBoolean();
    private final AtomicReference<Observation> observation=new AtomicReference<>(new Observation("STARTING",0,0));
    private volatile boolean closed;
    public DeploymentEvidenceRuntime(CacheNodeIdentity identity,CacheNodeRuntime membership,DeploymentEvidenceStore store,DeploymentEvidenceRegistry registry){
        this(identity,membership,store,registry,System::nanoTime);
    }
    DeploymentEvidenceRuntime(CacheNodeIdentity identity,CacheNodeRuntime membership,DeploymentEvidenceStore store,DeploymentEvidenceRegistry registry,LongSupplier clock){
        this.identity=identity;this.membership=membership;this.store=store;this.registry=registry;this.clock=clock;
        worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new SynchronousQueue<>(),
                Thread.ofPlatform().daemon().inheritInheritableThreadLocals(false).name("sma-deployment-evidence-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
    }
    public Status status(){
        if(closed)return new Status("STOPPED");
        var current=observation.get();var lease=membership.currentLease();
        if(lease==null||!lease.processId().equals(identity.processId())||lease.epoch()!=current.epoch())return new Status("WAITING_LEASE");
        if(current.state().equals("MATCH")&&(clock.getAsLong()-current.startedAt()<0||clock.getAsLong()-current.startedAt()>=TimeUnit.SECONDS.toNanos(10)))
            return new Status("STALE");
        return new Status(current.state());
    }
    @Scheduled(fixedDelay=1000)
    public void tick(){
        DeploymentEvidence.requireHost();
        if(closed||!busy.compareAndSet(false,true))return;
        try{worker.execute(()->{try{probe();}finally{busy.set(false);}});}
        catch(RejectedExecutionException stopped){busy.set(false);}
    }
    private void probe(){
        var lease=membership.currentLease();long started=clock.getAsLong();
        if(lease==null){publish(new Observation("WAITING_LEASE",0,started));return;}
        try{
            var ticket=store.challenge(identity,lease);
            var evidence=registry.collect(ticket.challenge()); // Outside all database transactions, on our one worker.
            if(closed||clock.getAsLong()-started>=TimeUnit.SECONDS.toNanos(5)){publish(new Observation("STALE",lease.epoch(),started));return;}
            var verdict=store.report(identity,ticket,evidence);
            publish(new Observation(verdict.state().name(),lease.epoch(),started));
        }catch(RuntimeException failure){publish(new Observation("UNAVAILABLE",lease.epoch(),started));}
    }
    private void publish(Observation value){if(!closed)observation.set(value);}
    @PreDestroy public void close(){closed=true;observation.set(new Observation("STOPPED",0,0));worker.shutdownNow();}
}
