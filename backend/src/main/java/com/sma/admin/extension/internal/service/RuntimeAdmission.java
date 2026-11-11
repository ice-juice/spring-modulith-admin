package com.sma.admin.extension.internal.service;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayDeque;
import org.springframework.stereotype.Service;
import com.sma.extension.protocol.*;

/** Admission affects new loads only. Never restarts a host or interrupts already accepted business transactions. */
@Service
public class RuntimeAdmission {
    private final ArrayDeque<Sample> samples=new ArrayDeque<>();
    public record Sample(Instant at,double cpu,double heap,double metaspace,long availableHeap) {}
    public synchronized Sample sample() {
        var heap=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        double cpu=ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os?os.getCpuLoad():-1;
        var meta=ManagementFactory.getMemoryPoolMXBeans().stream().filter(p -> p.getName().contains("Metaspace")).findFirst().map(java.lang.management.MemoryPoolMXBean::getUsage).orElse(null);
        // With no configured metaspace max use a conservative 256 MiB admission budget, not a fabricated percentage.
        long metaBudget=meta==null?-1:meta.getMax()>0?meta.getMax():256*ResourcePolicy.MIB;
        var value=new Sample(Instant.now(),cpu,heap.getMax()>0?(double)heap.getUsed()/heap.getMax():-1,
                meta==null?-1:(double)meta.getUsed()/metaBudget,heap.getMax()>0?heap.getMax()-heap.getUsed():-1);
        samples.add(value);while(samples.size()>20 || !samples.isEmpty() && samples.peek().at().isBefore(value.at().minusSeconds(300)))samples.remove();
        return value;
    }
    public synchronized void requireCapacity(long artifactBytes,ResourcePolicy policy) {
        var current=sample();
        Checks.require(current.cpu()>=0 && current.heap()>=0 && current.metaspace()>=0,"runtime-metrics-unavailable");
        Checks.require(current.heap()<0.8 && current.metaspace()<0.8,"runtime-memory-pressure");
        Checks.require(samples.size()<2 || samples.stream().anyMatch(s -> s.cpu()>=0 && s.cpu()<0.8),"runtime-cpu-pressure");
        // Immutable inspection retains data while reading it. Reserve the configured worst-case expansion, not just ZIP size.
        long needed=Math.addExact(artifactBytes,Math.multiplyExact(policy.expandedBytes(),2));
        Checks.require(current.availableHeap()>needed,"runtime-insufficient-inspection-memory");
    }
}
