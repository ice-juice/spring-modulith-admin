package com.sma.admin.extension.internal.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.sma.extension.runtime.InvocationObservation;

/** Bounded host-generated counters and per-call metadata audit. No plugin payloads are logged. */
@Component
public class ExtensionTelemetry {
    private static final long[] BOUNDS_MS={5,10,25,50,100,250,500,1000,2500,5000,10000,30000,Long.MAX_VALUE};
    private static final int LIMIT=2000;
    private final Map<Key,Counts> counters=new LinkedHashMap<>();
    private final Map<String,Long> custom=new LinkedHashMap<>();
    private long dropped;
    private final long startedMinute=Instant.now().getEpochSecond()/60;
    private record Window(String deployment,long revision,String bundle,long minute) {}
    private final Map<Window,long[]> windows=new LinkedHashMap<>();
    private final Map<Window,com.sma.extension.protocol.CanaryObservation> reported=new LinkedHashMap<>();
    private record Key(String deployment,long revision,String bundle,String plugin,String version,String entry) {}
    private static final class Counts {long calls,failures,nanos,max;long[] buckets=new long[BOUNDS_MS.length];Instant last;}
    public record View(String deployment,long revision,String bundle,String plugin,String version,String entry,long calls,long failures,
            double averageMs,long p95UpperBoundMs,double maximumMs,Instant lastCall) {}
    public record Snapshot(List<View> entries,Map<String,Long> customMetrics,long droppedKeys) {}
    public synchronized void observe(InvocationObservation event) {
        long minute=Instant.now().getEpochSecond()/60;
        windows.keySet().removeIf(k -> k.minute()<minute-3);
        var window=new Window(event.deploymentId(),event.releaseRevision(),event.bundleId(),minute);
        if(windows.containsKey(window) || windows.size()<LIMIT){var counts=windows.computeIfAbsent(window,k -> new long[3]);counts[0]=saturate(counts[0],1);if(!event.success())counts[1]=saturate(counts[1],1);if(event.elapsedNanos()>=1_000_000_000L)counts[2]=saturate(counts[2],1);}else dropped=saturate(dropped,1);
        // This logger is a business audit stream, separate from the authoritative release-security audit chain.
        LoggerFactory.getLogger("sma.extension.invocations").info("{}",new String(com.sma.extension.protocol.ProtocolJson.write(event),java.nio.charset.StandardCharsets.UTF_8));
        var key=new Key(event.deploymentId(),event.releaseRevision(),event.bundleId(),event.pluginId(),event.version(),event.entryId());
        if(!counters.containsKey(key) && counters.size()>=LIMIT){dropped=saturate(dropped,1);return;}
        var count=counters.computeIfAbsent(key,ignored -> new Counts());count.calls=saturate(count.calls,1);if(!event.success())count.failures=saturate(count.failures,1);
        count.nanos=saturate(count.nanos,event.elapsedNanos());count.max=Math.max(count.max,event.elapsedNanos());count.last=Instant.now();
        long elapsedMs=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(event.elapsedNanos());
        for(int i=0;i<BOUNDS_MS.length;i++)if(elapsedMs<=BOUNDS_MS[i]){count.buckets[i]=saturate(count.buckets[i],1);break;}
    }
    public synchronized void metric(String plugin,String version,String metric,long amount) {
        String key=plugin+":"+version+":"+metric;if(!custom.containsKey(key) && custom.size()>=LIMIT){dropped=saturate(dropped,1);return;}
        custom.merge(key,amount,ExtensionTelemetry::saturate);
    }
    public synchronized Snapshot snapshot() {
        return new Snapshot(counters.entrySet().stream().map(item -> {
            var k=item.getKey();var c=item.getValue();long target=(long)Math.ceil(c.calls*0.95),total=0,p95=Long.MAX_VALUE;
            for(int i=0;i<c.buckets.length;i++){total=saturate(total,c.buckets[i]);if(total>=target){p95=BOUNDS_MS[i];break;}}
            return new View(k.deployment(),k.revision(),k.bundle(),k.plugin(),k.version(),k.entry(),c.calls,c.failures,c.nanos/(double)c.calls/1_000_000,p95,c.max/1_000_000.0,c.last);
        }).toList(),Map.copyOf(custom),dropped);
    }
    public synchronized com.sma.extension.protocol.CanaryObservation observation(String deployment,long revision,String bundle,boolean noInFlight) {
        long minute=Instant.now().getEpochSecond()/60-1;var counts=windows.getOrDefault(new Window(deployment,revision,bundle,minute),new long[3]);
        reported.keySet().removeIf(k -> k.minute()<minute-2);var key=new Window(deployment,revision,bundle,minute);
        return reported.computeIfAbsent(key,k -> new com.sma.extension.protocol.CanaryObservation(deployment,revision,bundle,minute,counts[0],counts[1],counts[2],startedMinute<minute && dropped==0 && noInFlight));
    }
    private static long saturate(long left,long right){return right>Long.MAX_VALUE-left?Long.MAX_VALUE:left+right;}
}
