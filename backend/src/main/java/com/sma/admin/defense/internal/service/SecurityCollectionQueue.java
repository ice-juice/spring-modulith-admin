package com.sma.admin.defense.internal.service;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import com.sma.admin.common.security.*;

/** No caller-side I/O or waiting on a saturated lock. No source/tenant cardinality map. */
final class SecurityCollectionQueue {
    private final ReentrantLock lock=new ReentrantLock();
    private final ArrayDeque<SecuritySignal> high=new ArrayDeque<>(),low=new ArrayDeque<>();
    private final int maximum,lowMaximum,rate;private final LongSupplier clock;
    private long window;private int seen;private volatile int size;private boolean closed;
    final LongAdder queued=new LongAdder(),droppedCapacity=new LongAdder(),droppedRate=new LongAdder(),droppedContention=new LongAdder(),droppedCapture=new LongAdder(),droppedShutdown=new LongAdder();
    SecurityCollectionQueue(DefenseCollectionProperties settings,LongSupplier clock){
        maximum=settings.memoryRecords();lowMaximum=maximum-maximum/4;rate=settings.perSecond();this.clock=clock;window=clock.getAsLong();
    }
    SecuritySignals.Admission offer(SecuritySignal.Fact fact,SecuritySignalCapture capture){
        if(!lock.tryLock()){droppedContention.increment();return SecuritySignals.Admission.REJECTED;}
        try{
            if(closed){droppedShutdown.increment();return SecuritySignals.Admission.REJECTED;}
            boolean priority=fact!=null&&fact.severity()!=SecuritySignal.Severity.LOW;
            long now=clock.getAsLong();if(now-window>=1_000_000_000L){window=now;seen=0;}
            // Low severity cannot consume the last quarter of the per-second budget either.
            int allowed=priority?rate:Math.max(1,rate-rate/4);
            if(seen>=allowed){droppedRate.increment();return SecuritySignals.Admission.REJECTED;}
            seen++;
            if(size>=maximum||!priority&&size>=lowMaximum){droppedCapacity.increment();return SecuritySignals.Admission.REJECTED;}
            SecuritySignal signal;
            try{signal=capture.capture(fact);if(signal==null||signal.fact()!=fact)throw new IllegalArgumentException();}
            catch(RuntimeException failure){droppedCapture.increment();return SecuritySignals.Admission.REJECTED;}
            (priority?high:low).addLast(signal);size++;queued.increment();return SecuritySignals.Admission.QUEUED_VOLATILE;
        }finally{lock.unlock();}
    }
    SecuritySignal peek(){lock.lock();try{return high.isEmpty()?low.peekFirst():high.peekFirst();}finally{lock.unlock();}}
    void persisted(SecuritySignal signal){lock.lock();try{if(high.remove(signal)||low.remove(signal))size--;else throw new IllegalStateException("Missing pending signal");}finally{lock.unlock();}}
    int size(){return size;}
    void close(){lock.lock();try{if(closed)return;closed=true;droppedShutdown.add(size);high.clear();low.clear();size=0;}finally{lock.unlock();}}
}
