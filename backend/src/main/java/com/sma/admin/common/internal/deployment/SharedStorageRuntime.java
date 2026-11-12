package com.sma.admin.common.internal.deployment;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.LongSupplier;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import com.sma.admin.common.deployment.DeploymentEvidence;
import com.sma.admin.common.internal.cache.CacheNodeIdentity;
import com.sma.admin.common.internal.cache.CacheNodeRuntime;
import com.sma.admin.common.internal.service.SharedStorageStore;
import com.sma.admin.common.internal.service.CacheNodeLeaseStore;

/** Filesystem I/O is independent of both node heartbeat and configuration evidence. No work queue. */
public final class SharedStorageRuntime {
    public record Status(String state){public boolean ready(){return state.equals("READY");}}
    private record Observation(String state,long epoch,long startedAt){}
    @FunctionalInterface interface Probes{SharedStorageProbe create(long epoch);}
    private final CacheNodeIdentity identity;private final CacheNodeRuntime membership;private final DeploymentEvidenceRuntime configuration;
    private final SharedStorageStore store;private final Probes probes;private final LongSupplier clock;
    private final ExecutorService worker;private final AtomicBoolean busy=new AtomicBoolean();
    private final AtomicReference<Observation> observed=new AtomicReference<>(new Observation("STARTING",0,0));
    private volatile boolean closed;private SharedStorageProbe probe;private SharedStorageProbe.Offer offer;private long epoch;private int nextPeer;
    public SharedStorageRuntime(CacheNodeIdentity identity,CacheNodeRuntime membership,DeploymentEvidenceRuntime configuration,
            SharedStorageStore store,SharedStorageRegistry registry){this(identity,membership,configuration,store,e->new SharedStorageProbe(identity,e,registry),System::nanoTime);}
    SharedStorageRuntime(CacheNodeIdentity identity,CacheNodeRuntime membership,DeploymentEvidenceRuntime configuration,SharedStorageStore store,Probes probes,LongSupplier clock){
        this.identity=identity;this.membership=membership;this.configuration=configuration;this.store=store;this.probes=probes;this.clock=clock;
        worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new SynchronousQueue<>(),Thread.ofPlatform().daemon()
                .inheritInheritableThreadLocals(false).name("sma-shared-storage-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
    }
    public Status status(){
        if(closed)return new Status("STOPPED");if(!configuration.status().matches())return new Status("WAITING_CONFIGURATION");
        var lease=membership.currentLease();var observation=observed.get();
        if(lease==null||!lease.processId().equals(identity.processId())||lease.epoch()!=observation.epoch())return new Status("WAITING_LEASE");
        long elapsed=clock.getAsLong()-observation.startedAt();
        if(observation.state().equals("READY")&&(elapsed<0||elapsed>=TimeUnit.SECONDS.toNanos(10)))return new Status("STALE");
        return new Status(observation.state());
    }
    @Scheduled(fixedDelay=1000)
    public void tick(){
        DeploymentEvidence.requireHost();if(closed||!busy.compareAndSet(false,true))return;
        try{worker.execute(()->{try{cycle();}finally{busy.set(false);}});}catch(RejectedExecutionException stopped){busy.set(false);}
    }
    private void cycle(){
        long started=clock.getAsLong();var lease=membership.currentLease();
        if(lease==null||!configuration.status().matches()){publish("WAITING_CONFIGURATION",lease==null?0:lease.epoch(),started);return;}
        SharedStorageStore.View view=null;
        try{
            if(probe==null||epoch!=lease.epoch()){probe=probes.create(lease.epoch());epoch=lease.epoch();offer=null;nextPeer=0;}
            if(offer==null)offer=probe.publish(); // Probe itself never republishes an uncertain resource.
            deadline(started);view=store.register(identity,lease,offer); // Re-read current generations after any uncertain commit.
            boolean own=probe.verifyOwn();deadline(started);
            boolean anchor=own&&view.anchor()!=null&&probe.verifyAnchor(view.anchor());deadline(started);
            var reads=new ArrayList<SharedStorageStore.Read>();
            if(anchor&&!view.peers().isEmpty()){
                // The first live member services every other member; followers prefer that member.
                // The hub rotates so a slow/bad peer cannot starve all other incoming incarnations.
                boolean hub=identity.properties().id().compareTo(view.peers().getFirst().offer().nodeId())<0;
                var peer=view.peers().get(hub?Math.floorMod(nextPeer++,view.peers().size()):0);
                if(probe.verifyPeer(peer.offer()))reads.add(peer.reference());
            }
            deadline(started);
            var report=store.observe(identity,lease,view.self().generation(),view.anchorDigest(),own,anchor,reads);
            publish(report.certified()?"READY":own&&anchor?"WAITING_PEER":"UNAVAILABLE",lease.epoch(),started);
        }catch(RuntimeException failure){
            publish("UNAVAILABLE",lease.epoch(),started);
            if(!closed&&view!=null)try{store.observe(identity,lease,view.self().generation(),view.anchorDigest(),false,false,List.of());}
            catch(RuntimeException stale){/* No forced overwrite; durable reports expire and later snapshots resolve state. */}
        }
    }
    private void deadline(long started){long elapsed=clock.getAsLong()-started;if(closed||elapsed<0||elapsed>=TimeUnit.SECONDS.toNanos(5))throw new IllegalStateException("Storage observation expired");}
    private void publish(String state,long epoch,long started){if(!closed)observed.set(new Observation(state,epoch,started));}
    @PreDestroy public void close(){closed=true;worker.shutdownNow();}
}
