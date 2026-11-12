package com.sma.admin.message.internal.service;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PreDestroy;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import com.sma.admin.common.deployment.*;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.*;

/** Every process reads its own recipients. Restart/reconnect also triggers the client's REST refresh. */
@Component
public class SocketSignalProcessor implements AutoCloseable {
    private static final Logger log=LoggerFactory.getLogger(SocketSignalProcessor.class);
    private static final Comparator<SocketHub.Audience> ORDER=Comparator.comparingLong(SocketHub.Audience::tenantId).thenComparing(SocketHub.Audience::portal).thenComparingLong(SocketHub.Audience::userId);
    private record Key(SocketHub.Audience audience,String type){}
    private record Stamp(long id,long revision){}
    private final SocketHub hub;private final SocketSignalStore signals;private final SocketSignalProperties settings;private final TenantWorkQuery tenants;
    private final Map<Key,Stamp> observed=new HashMap<>();
    private final String workId="message-socket:"+UUID.randomUUID();
    private final ExecutorService worker;private final DeploymentWork admission;
    private final AtomicBoolean running=new AtomicBoolean();private volatile boolean closed;private boolean failed;
    private int offset;private long maintenanceCursor,nextMaintenance;
    @org.springframework.beans.factory.annotation.Autowired
    public SocketSignalProcessor(SocketHub hub,SocketSignalStore signals,SocketSignalProperties settings,TenantWorkQuery tenants,DeploymentWork admission){
        this(hub,signals,settings,tenants,admission,new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),
                Thread.ofPlatform().daemon().inheritInheritableThreadLocals(false).name("sma-socket-signals-",0).factory(),new ThreadPoolExecutor.AbortPolicy()));
    }
    SocketSignalProcessor(SocketHub hub,SocketSignalStore signals,SocketSignalProperties settings,TenantWorkQuery tenants,DeploymentWork admission,ExecutorService worker){
        this.hub=hub;this.signals=signals;this.settings=settings;this.tenants=tenants;this.admission=admission;this.worker=worker;
    }
    @Scheduled(fixedDelayString="${sma.message.signals.poll-millis:1000}")
    public void tick(){
        DeploymentEvidence.requireHost();if(closed||!settings.enabled()||!admission.snapshot().accepting()||!running.compareAndSet(false,true))return;
        try{worker.execute(()->{try{pollOnce();}finally{running.set(false);}});}catch(RejectedExecutionException stopped){running.set(false);}
    }
    synchronized void pollOnce(){
        DeploymentEvidence.requireHost();if(closed||!settings.enabled())return;
        var discovery=admission.tryStart();if(discovery.isEmpty())return;
        Set<SocketHub.Audience> live;
        try(var permit=discovery.orElseThrow()){live=hub.subscriptions();observed.keySet().removeIf(key->!live.contains(key.audience()));}
        var ordered=live.stream().sorted(ORDER).toList();
        if(!ordered.isEmpty()){
            offset%=ordered.size();var first=ordered.get(offset);int end=offset;
            while(end<ordered.size()&&end-offset<settings.usersPerPoll()){
                var next=ordered.get(end);if(next.tenantId()!=first.tenantId()||!next.portal().equals(first.portal()))break;end++;
            }
            var batch=List.copyOf(ordered.subList(offset,end));
            try{
                var users=batch.stream().map(SocketHub.Audience::userId).collect(java.util.stream.Collectors.toSet());
                var read=admit();if(read.isEmpty())return;
                List<SocketSignalStore.Signal> rows;
                try(var permit=read.orElseThrow()){rows=ActorContext.withinJob(first.tenantId(),workId,()->signals.read(first.portal(),users));}
                // read() has returned and committed before authority checks or socket I/O.
                for(var row:rows){
                    var audience=new SocketHub.Audience(first.tenantId(),first.portal(),row.userId());
                    if(!users.contains(row.userId())||!(row.portal().equals(first.portal())||row.portal().equals("*")))throw new IllegalStateException("Signal subscription mismatch");
                    var key=new Key(audience,row.type());var stamp=new Stamp(row.id(),row.revision());
                    if(stamp.equals(observed.get(key)))continue;
                    var delivery=admit();if(delivery.isEmpty())return;
                    try(var permit=delivery.orElseThrow()){
                        ActorContext.withinJob(first.tenantId(),workId,()->{
                            if(row.type().equals("session-ended"))hub.revalidate(audience,row.objectId(),row.reason());
                            else hub.push(first.tenantId(),first.portal(),Set.of(row.userId()),row.type(),row.objectId());
                            return null;
                        });
                        observed.put(key,stamp);
                    }
                }
                failed=false;
            }catch(RuntimeException unavailable){if(!failed)log.warn("Socket signal delivery deferred; REST refresh remains authoritative");failed=true;}
            offset=end%ordered.size();
        }else offset=0;
        if(System.nanoTime()>=nextMaintenance){
            try{
                var directory=admit();if(directory.isEmpty())return;
                List<TenantWorkQuery.Entry> page;
                try(var permit=directory.orElseThrow()){page=tenants.page(maintenanceCursor,1);}
                if(page.isEmpty())maintenanceCursor=0;
                else{
                    var tenant=page.getFirst();var cleanup=admit();if(cleanup.isEmpty())return;
                    try(var permit=cleanup.orElseThrow()){ActorContext.withinJob(tenant.tenantId(),workId,signals::purge);}
                    maintenanceCursor=tenant.tenantId();
                }
            }catch(RuntimeException unavailable){log.debug("Expired socket signal cleanup deferred");}
            nextMaintenance=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        }
    }
    int observedCount(){return observed.size();}
    private Optional<DeploymentWork.Permit> admit(){return closed?Optional.empty():admission.tryStart();}
    @Override @PreDestroy public void close(){closed=true;worker.shutdownNow();}
}
