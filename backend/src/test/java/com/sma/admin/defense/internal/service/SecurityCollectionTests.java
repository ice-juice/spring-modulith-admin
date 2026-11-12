package com.sma.admin.defense.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sma.admin.common.security.*;
import com.sma.admin.common.deployment.*;
import tools.jackson.databind.json.JsonMapper;

class SecurityCollectionTests {
    @TempDir Path temp;
    private final JsonMapper json=JsonMapper.builder().build();
    private DefenseCollectionProperties settings(int batch)throws Exception{
        Path key=temp.resolve("key");if(!Files.exists(key))Files.write(key,new byte[32]);
        return new DefenseCollectionProperties(true,temp.resolve("spool").toString(),key.toString(),4,8,16384L,100,batch,Duration.ofMillis(250),"test.node-a");
    }
    private SecuritySignal signal(SecuritySignal.Fact fact,long tenant){return new SecuritySignal(UUID.randomUUID(),UUID.randomUUID(),Instant.now(),fact,
            new SecuritySignal.Attribution(SecuritySignal.Scope.TENANT,SecuritySignal.Identity.ADMIN_USER,tenant,"77","admin",null,null,null),null);}
    static class Admission implements DeploymentWork {
        volatile boolean accepting=true;final AtomicInteger active=new AtomicInteger();
        @Override public Optional<Permit> tryStart(){if(!accepting)return Optional.empty();active.incrementAndGet();AtomicBoolean done=new AtomicBoolean();return Optional.of(()->{if(done.compareAndSet(false,true))active.decrementAndGet();});}
        @Override public Snapshot snapshot(){return new Snapshot(accepting,!accepting,active.get());}
    }
    @Test void memoryCapacityReservesPriorityWithoutAnyIoAndRateWindowIsBounded()throws Exception{
        var settings=settings(4);var now=new AtomicLong();var queue=new SecurityCollectionQueue(settings,now::get);
        var low=SecuritySignal.Fact.AUTHORIZATION_REJECTED;var high=SecuritySignal.Fact.SIGNATURE_REPLAY_REJECTED;
        for(int i=0;i<3;i++)assertThat(queue.offer(low,f->signal(f,1))).isEqualTo(SecuritySignals.Admission.QUEUED_VOLATILE);
        assertThat(queue.offer(low,f->signal(f,1))).isEqualTo(SecuritySignals.Admission.REJECTED);
        assertThat(queue.offer(high,f->signal(f,1))).isEqualTo(SecuritySignals.Admission.QUEUED_VOLATILE);
        assertThat(queue.peek().fact()).isEqualTo(high);assertThat(queue.size()).isEqualTo(4);assertThat(temp.resolve("spool")).doesNotExist();
        queue.close();assertThat(queue.droppedShutdown.sum()).isEqualTo(4);assertThat(queue.offer(high,f->signal(f,1))).isEqualTo(SecuritySignals.Admission.REJECTED);
        var rateSettings=new DefenseCollectionProperties(false,null,null,8,8,16384L,4,4,Duration.ofMillis(250),null);
        var limited=new SecurityCollectionQueue(rateSettings,now::get);
        for(int i=0;i<3;i++)assertThat(limited.offer(low,f->signal(f,1))).isEqualTo(SecuritySignals.Admission.QUEUED_VOLATILE);
        assertThat(limited.offer(low,f->signal(f,1))).isEqualTo(SecuritySignals.Admission.REJECTED);
        assertThat(limited.offer(high,f->signal(f,1))).isEqualTo(SecuritySignals.Admission.QUEUED_VOLATILE);
        assertThat(limited.offer(high,f->signal(f,1))).isEqualTo(SecuritySignals.Admission.REJECTED);
        while(limited.peek()!=null)limited.persisted(limited.peek());now.set(1_000_000_000L);
        assertThat(limited.offer(low,f->signal(f,1))).isEqualTo(SecuritySignals.Admission.QUEUED_VOLATILE);
    }
    @Test void contentionAndCaptureFailureNeverBlockOrLeakExceptionsToCaller()throws Exception{
        var queue=new SecurityCollectionQueue(settings(2),System::nanoTime);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newSingleThreadExecutor()){
            var pending=pool.submit(()->queue.offer(SecuritySignal.Fact.AUTHORIZATION_REJECTED,f->{entered.countDown();try{release.await();}catch(InterruptedException e){throw new IllegalStateException(e);}return signal(f,1);}));
            try{assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();assertThat(queue.offer(SecuritySignal.Fact.AUTHORIZATION_REJECTED,f->signal(f,1))).isEqualTo(SecuritySignals.Admission.REJECTED);}
            finally{release.countDown();}assertThat(pending.get(5,TimeUnit.SECONDS)).isEqualTo(SecuritySignals.Admission.QUEUED_VOLATILE);
        }
        assertThat(queue.droppedContention.sum()).isEqualTo(1);
        assertThat(queue.offer(SecuritySignal.Fact.AUTHORIZATION_REJECTED,f->{throw new IllegalStateException("private detail");})).isEqualTo(SecuritySignals.Admission.REJECTED);
        assertThat(queue.droppedCapture.sum()).isEqualTo(1);
    }
    @Test void commitAcknowledgementLossRetainsOriginalDiskEnvelopeAndReopenDeduplicates()throws Exception{
        var settings=settings(4);var time=new AtomicLong();var queue=new SecurityCollectionQueue(settings,time::get);var admission=new Admission();
        var signal=signal(SecuritySignal.Fact.AUTHORIZATION_REJECTED,1);queue.offer(signal.fact(),f->signal);var committed=new HashSet<UUID>();
        try(var worker=new SecurityCollectionWorker(settings,queue,admission,s->{committed.add(s.eventId());throw new IllegalStateException("lost commit response");},json,time::get)){
            worker.cycle();assertThat(queue.size()).isZero();assertThat(worker.diskRecords()).isEqualTo(1);assertThat(committed).containsExactly(signal.eventId());
            assertThat(admission.active).hasValue(0);assertThat(worker.failures.sum()).isEqualTo(1);
        }
        var nextQueue=new SecurityCollectionQueue(settings,time::get);var calls=new AtomicInteger();
        try(var worker=new SecurityCollectionWorker(settings,nextQueue,admission,s->{calls.incrementAndGet();assertThat(s).isEqualTo(signal);return committed.contains(s.eventId())?SecurityEventStore.Result.DUPLICATE:SecurityEventStore.Result.STORED;},json,time::get)){
            worker.cycle();assertThat(worker.diskRecords()).isZero();assertThat(calls).hasValue(1);assertThat(worker.duplicates.sum()).isEqualTo(1);
        }
    }
    @Test void missingKeyDoesNotCreateOneAndRetryBackoffPreservesVolatileQueue()throws Exception{
        var settings=settings(4);Files.delete(Path.of(settings.keyFile()));var time=new AtomicLong();var queue=new SecurityCollectionQueue(settings,time::get);
        queue.offer(SecuritySignal.Fact.AUTHORIZATION_REJECTED,f->signal(f,1));var calls=new AtomicInteger();
        try(var worker=new SecurityCollectionWorker(settings,queue,new Admission(),s->{calls.incrementAndGet();return SecurityEventStore.Result.STORED;},json,time::get)){
            worker.cycle();assertThat(queue.size()).isEqualTo(1);assertThat(calls).hasValue(0);assertThat(worker.available()).isFalse();assertThat(Path.of(settings.keyFile())).doesNotExist();
            Files.write(Path.of(settings.keyFile()),new byte[32]);worker.cycle();assertThat(calls).hasValue(0);
            time.set(Duration.ofMillis(251).toNanos());worker.cycle();assertThat(calls).hasValue(1);assertThat(queue.size()).isZero();assertThat(worker.available()).isTrue();
        }
    }
    @Test void capacityOrConflictDoesNotDeleteAndRotationLetsOtherTenantsProgress()throws Exception{
        var settings=settings(4);var time=new AtomicLong();var queue=new SecurityCollectionQueue(settings,time::get);var calls=new ConcurrentHashMap<Long,AtomicInteger>();
        queue.offer(SecuritySignal.Fact.AUTHORIZATION_REJECTED,f->signal(f,1));queue.offer(SecuritySignal.Fact.AUTHORIZATION_REJECTED,f->signal(f,2));
        try(var worker=new SecurityCollectionWorker(settings,queue,new Admission(),s->{long t=s.attribution().tenantId();calls.computeIfAbsent(t,k->new AtomicInteger()).incrementAndGet();return t==1?SecurityEventStore.Result.CAPACITY:SecurityEventStore.Result.STORED;},json,time::get)){
            worker.cycle();assertThat(calls.get(2L)).hasValue(1);assertThat(worker.diskRecords()).isEqualTo(1);assertThat(worker.capacity.sum()).isGreaterThan(0);
        }
        try(var worker=new SecurityCollectionWorker(settings,new SecurityCollectionQueue(settings,time::get),new Admission(),s->SecurityEventStore.Result.CONFLICT,json,time::get)){
            worker.cycle();assertThat(worker.diskRecords()).isEqualTo(1);assertThat(worker.conflicts.sum()).isEqualTo(1);
        }
        try(var worker=new SecurityCollectionWorker(settings,new SecurityCollectionQueue(settings,time::get),new Admission(),s->SecurityEventStore.Result.EXPIRED,json,time::get)){
            worker.cycle();assertThat(worker.diskRecords()).isZero();assertThat(worker.expired.sum()).isEqualTo(1);
        }
    }
    @Test void admissionDenialAndStopNeverReleaseAnActuallyRunningUnitEarly()throws Exception{
        var settings=settings(4);var queue=new SecurityCollectionQueue(settings,System::nanoTime);var admission=new Admission();admission.accepting=false;
        queue.offer(SecuritySignal.Fact.AUTHORIZATION_REJECTED,f->signal(f,1));queue.offer(SecuritySignal.Fact.AUTHORIZATION_REJECTED,f->signal(f,2));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new AtomicInteger();
        try(var worker=new SecurityCollectionWorker(settings,queue,admission,s->{calls.incrementAndGet();entered.countDown();try{release.await();}catch(InterruptedException e){throw new IllegalStateException(e);}return SecurityEventStore.Result.STORED;},json,System::nanoTime);
                var pool=Executors.newSingleThreadExecutor(Thread.ofPlatform().inheritInheritableThreadLocals(false).factory())){
            worker.cycle();assertThat(temp.resolve("spool")).doesNotExist();assertThat(queue.size()).isEqualTo(2);
            admission.accepting=true;var running=pool.submit(worker::cycle);
            try{assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();worker.stop();assertThat(admission.active).hasValue(1);assertThat(running.isDone()).isFalse();}
            finally{release.countDown();}running.get(5,TimeUnit.SECONDS);assertThat(admission.active).hasValue(0);assertThat(calls).hasValue(1);assertThat(queue.size()).isEqualTo(1);
        }
    }
    @Test void publicServiceOnlyAcceptsFactsCapturesCallerAndWorksOnCleanThread()throws Exception{
        var settings=settings(4);var admission=new Admission();var store=mock(SecurityEventStore.class);var signal=signal(SecuritySignal.Fact.AUTHORIZATION_REJECTED,77);
        var done=new CountDownLatch(1);when(store.append(any())).thenAnswer(call->{
            assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).isNull();
            assertThat((SecuritySignal)call.getArgument(0)).isEqualTo(signal);done.countDown();return SecurityEventStore.Result.STORED;
        });
        var capture=mock(SecuritySignalCapture.class);when(capture.capture(signal.fact())).thenReturn(signal);
        var meters=new io.micrometer.core.instrument.simple.SimpleMeterRegistry();var service=new SecurityCollectionService(settings,capture,admission,store,json,meters);
        try{
            assertThat(service.observe(signal.fact())).isEqualTo(SecuritySignals.Admission.QUEUED_VOLATILE);verifyNoInteractions(store);
            service.tick();assertThat(done.await(5,TimeUnit.SECONDS)).isTrue();
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(new org.springframework.mock.web.MockHttpServletRequest()));
            try{assertThatThrownBy(service::tick).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);}
            finally{org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
        }finally{service.close();}
        assertThat(admission.active).hasValue(0);assertThat(service.observe(signal.fact())).isEqualTo(SecuritySignals.Admission.REJECTED);
        assertThat(meters.get("sma.defense.collection").tag("result","stored").functionCounter().count()).isEqualTo(1);
    }
    @Test void diskScopeBindingRejectsReplayInAnotherNodeOrEnvironmentAndKeepsFiles()throws Exception{
        var p=settings(1);var clock=new AtomicLong(-1000000000L);var queue=new SecurityCollectionQueue(p,clock::get);
        var signal=signal(SecuritySignal.Fact.SIGNATURE_REPLAY_REJECTED,1);queue.offer(signal.fact(),f->signal);
        try(var worker=new SecurityCollectionWorker(p,queue,new Admission(),s->SecurityEventStore.Result.CAPACITY,json,clock::get)){
            worker.cycle();assertThat(worker.diskRecords()).isEqualTo(1); // Negative nanoTime is valid.
        }
        var wrong=new DefenseCollectionProperties(true,p.directory(),p.keyFile(),p.memoryRecords(),p.diskRecords(),p.diskBytes(),p.perSecond(),p.batch(),p.retryDelay(),"other.node-a");
        var calls=new AtomicInteger();
        try(var worker=new SecurityCollectionWorker(wrong,new SecurityCollectionQueue(wrong,clock::get),new Admission(),s->{calls.incrementAndGet();return SecurityEventStore.Result.STORED;},json,clock::get)){
            worker.cycle();assertThat(worker.available()).isFalse();assertThat(calls).hasValue(0);
        }
        assertThat(Path.of(p.directory()).resolve(signal.eventId()+".ready")).exists();
        try(var worker=new SecurityCollectionWorker(p,new SecurityCollectionQueue(p,clock::get),new Admission(),s->{assertThat(s).isEqualTo(signal);return SecurityEventStore.Result.STORED;},json,clock::get)){
            worker.cycle();assertThat(worker.diskRecords()).isZero();
        }
    }
    @Test void disabledOrUnavailableAdmissionCannotCauseCallerSideCaptureOrDiskIo()throws Exception{
        var capture=mock(SecuritySignalCapture.class);var store=mock(SecurityEventStore.class);var admission=mock(DeploymentWork.class);
        var disabled=new DefenseCollectionProperties(false,null,null,null,null,null,null,null,null,null);
        var service=new SecurityCollectionService(disabled,capture,admission,store,json,new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        try{assertThat(service.observe(SecuritySignal.Fact.AUTHORIZATION_REJECTED)).isEqualTo(SecuritySignals.Admission.DISABLED);service.tick();verifyNoInteractions(capture,store,admission);}
        finally{service.close();}
        when(admission.snapshot()).thenThrow(new IllegalStateException("unavailable"));
        var active=new SecurityCollectionService(settings(1),capture,admission,store,json,new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        try{assertThat(active.observe(SecuritySignal.Fact.AUTHORIZATION_REJECTED)).isEqualTo(SecuritySignals.Admission.REJECTED);verifyNoInteractions(capture,store);}
        finally{active.close();}
    }
    @Test void serviceShutdownTimeoutKeepsPermitAndDirectoryOwnedUntilActualCompletion()throws Exception{
        var p=settings(4);var admission=new Admission();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var store=mock(SecurityEventStore.class);
        when(store.append(any())).thenAnswer(call->{entered.countDown();release.await();return SecurityEventStore.Result.STORED;});
        var service=new SecurityCollectionService(p,f->signal(f,1),admission,store,json,new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        try{
            service.observe(SecuritySignal.Fact.AUTHORIZATION_REJECTED);service.tick();assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            service.close();assertThat(admission.active).hasValue(1);
            // The timeout is not permission to release a directory still being used by actual I/O.
            try(var peer=new SecurityCollectionWorker(p,new SecurityCollectionQueue(p,System::nanoTime),new Admission(),s->SecurityEventStore.Result.STORED,json,System::nanoTime)){
                peer.cycle();assertThat(peer.available()).isFalse();
            }
        }finally{release.countDown();}
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(admission.active.get()!=0&&System.nanoTime()<deadline)Thread.sleep(10);
        assertThat(admission.active).hasValue(0);service.close();
        try(var peer=new SecurityCollectionWorker(p,new SecurityCollectionQueue(p,System::nanoTime),new Admission(),s->SecurityEventStore.Result.STORED,json,System::nanoTime)){
            peer.cycle();assertThat(peer.available()).isTrue();
        }
    }
}
