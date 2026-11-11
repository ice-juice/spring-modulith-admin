package com.sma.admin.message.internal.service;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PreDestroy;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final ExecutorService worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),Thread.ofPlatform().daemon().name("sma-socket-signals-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
    private final AtomicBoolean running=new AtomicBoolean();private volatile boolean closed;private boolean failed;
    private int offset;private long maintenanceCursor,nextMaintenance;
    public SocketSignalProcessor(SocketHub hub,SocketSignalStore signals,SocketSignalProperties settings,TenantWorkQuery tenants){this.hub=hub;this.signals=signals;this.settings=settings;this.tenants=tenants;}
    @Scheduled(fixedDelayString="${sma.message.signals.poll-millis:1000}")
    public void tick(){
        host();if(closed||!settings.enabled()||!running.compareAndSet(false,true))return;
        try{worker.execute(()->{try{pollOnce();}finally{running.set(false);}});}catch(RejectedExecutionException stopped){running.set(false);}
    }
    synchronized void pollOnce(){
        host();if(closed||!settings.enabled())return;
        var live=hub.subscriptions();observed.keySet().removeIf(key->!live.contains(key.audience()));
        var ordered=live.stream().sorted(ORDER).toList();
        if(!ordered.isEmpty()){
            offset%=ordered.size();var first=ordered.get(offset);int end=offset;
            while(end<ordered.size()&&end-offset<settings.usersPerPoll()){
                var next=ordered.get(end);if(next.tenantId()!=first.tenantId()||!next.portal().equals(first.portal()))break;end++;
            }
            var batch=List.copyOf(ordered.subList(offset,end));offset=end%ordered.size();
            try{
                ActorContext.withinJob(first.tenantId(),workId,()->{
                    var users=batch.stream().map(SocketHub.Audience::userId).collect(java.util.stream.Collectors.toSet());
                    var rows=signals.read(first.portal(),users);
                    // read() has returned and committed before authority checks or socket I/O.
                    for(var row:rows){
                        var audience=new SocketHub.Audience(first.tenantId(),first.portal(),row.userId());
                        if(!users.contains(row.userId())||!(row.portal().equals(first.portal())||row.portal().equals("*")))throw new IllegalStateException("Signal subscription mismatch");
                        var key=new Key(audience,row.type());var stamp=new Stamp(row.id(),row.revision());
                        if(stamp.equals(observed.get(key)))continue;
                        if(row.type().equals("session-ended"))hub.revalidate(audience,row.objectId(),row.reason());
                        else hub.push(first.tenantId(),first.portal(),Set.of(row.userId()),row.type(),row.objectId());
                        observed.put(key,stamp);
                    }
                    return null;
                });
                failed=false;
            }catch(RuntimeException unavailable){if(!failed)log.warn("Socket signal delivery deferred; REST refresh remains authoritative");failed=true;}
        }else offset=0;
        if(System.nanoTime()>=nextMaintenance){
            nextMaintenance=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            try{
                var page=tenants.page(maintenanceCursor,1);
                if(page.isEmpty())maintenanceCursor=0;
                else{var tenant=page.getFirst();maintenanceCursor=tenant.tenantId();ActorContext.withinJob(tenant.tenantId(),workId,signals::purge);}
            }catch(RuntimeException unavailable){log.debug("Expired socket signal cleanup deferred");}
        }
    }
    int observedCount(){return observed.size();}
    private static void host(){if(SecurityContextHolder.getContext().getAuthentication()!=null)throw new AccessDeniedException("Host socket dispatcher required");}
    @Override @PreDestroy public void close(){closed=true;worker.shutdownNow();}
}
