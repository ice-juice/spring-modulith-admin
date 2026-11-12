package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.deployment.*;

class DeploymentTaskTrackerTests {
    @Test void stopAcceptingPreservesAcceptedQueueAndRunningResult()throws Exception{
        try(var tracker=new DeploymentTaskTracker(2)){
            var queue=new ArrayDeque<Runnable>();var calls=new AtomicInteger();
            var first=tracker.submit(queue::add,()->{assertThat(tracker.snapshot()).isEqualTo(new DeploymentActivity.Snapshot(1,1));return calls.incrementAndGet();});
            var second=tracker.submit(queue::add,calls::incrementAndGet);
            tracker.stopAccepting();tracker.stopAccepting();assertThat(tracker.snapshot()).isEqualTo(new DeploymentActivity.Snapshot(2,0));
            assertThatThrownBy(()->tracker.submit(queue::add,calls::incrementAndGet)).isInstanceOf(RejectedExecutionException.class);
            assertThat(first.isCancelled()).isFalse();queue.remove().run();queue.remove().run();
            assertThat(first.get()).isEqualTo(1);assertThat(second.get()).isEqualTo(2);assertThat(tracker.snapshot().quiescent()).isTrue();
        }
    }
    @Test void cancellingFutureDoesNotEraseAThreadThatIgnoresInterrupts()throws Exception{
        try(var tracker=new DeploymentTaskTracker(1);var pool=Executors.newSingleThreadExecutor()){
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var exited=new CountDownLatch(1);
            var future=tracker.submit(pool,()->{entered.countDown();try{awaitIgnoringInterrupts(release);return "late";}finally{exited.countDown();}});
            try{
                assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();assertThat(future.cancel(true)).isTrue();assertThat(future.isDone()).isTrue();
                assertThat(tracker.snapshot()).isEqualTo(new DeploymentActivity.Snapshot(0,1));
                assertThatThrownBy(()->tracker.submit(pool,()->"another")).isInstanceOf(RejectedExecutionException.class);
                tracker.close();tracker.close();assertThat(tracker.snapshot().running()).isOne();
            }finally{release.countDown();}
            assertThat(exited.await(3,TimeUnit.SECONDS)).isTrue();pool.shutdown();assertThat(pool.awaitTermination(3,TimeUnit.SECONDS)).isTrue();
            assertThat(tracker.snapshot().quiescent()).isTrue();
        }
    }
    @Test void queuedCancellationAndExecutorRejectionNeverInvokeOrLeak()throws Exception{
        try(var tracker=new DeploymentTaskTracker(2)){
            var queued=new ArrayDeque<Runnable>();var calls=new AtomicInteger();
            var future=tracker.submit(queued::add,()->calls.incrementAndGet());assertThat(tracker.snapshot().queued()).isOne();
            future.cancel(false);queued.remove().run();assertThat(calls).hasValue(0);assertThat(tracker.snapshot().quiescent()).isTrue();
            assertThatThrownBy(()->tracker.submit(task->{throw new RejectedExecutionException();},()->calls.incrementAndGet())).isInstanceOf(RejectedExecutionException.class);
            assertThat(tracker.snapshot().quiescent()).isTrue();
            var next=tracker.submit(queued::add,()->calls.incrementAndGet());tracker.close();assertThat(next.isCancelled()).isTrue();queued.remove().run();
            assertThat(calls).hasValue(0);assertThat(tracker.snapshot().quiescent()).isTrue();
            assertThatThrownBy(()->tracker.submit(Runnable::run,()->calls.incrementAndGet())).isInstanceOf(RejectedExecutionException.class);
        }
    }
    @Test void successFailureAndDuplicateRunReleaseExactlyOnce()throws Exception{
        try(var tracker=new DeploymentTaskTracker(2)){
            var queue=new ArrayDeque<Runnable>();var calls=new AtomicInteger();
            var success=tracker.submit(queue::add,()->{assertThat(tracker.snapshot().running()).isOne();return calls.incrementAndGet();});
            var task=queue.remove();task.run();task.run();assertThat(success.get()).isEqualTo(1);assertThat(calls).hasValue(1);
            var failure=tracker.submit(Runnable::run,()->{throw new IllegalArgumentException("fixture");});
            assertThatThrownBy(failure::get).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalArgumentException.class);
            assertThat(tracker.snapshot().quiescent()).isTrue();
        }
    }
    @Test void concurrentStartCancellationAndCloseNeverUnderflowOrRunAfterQueuedCancellation()throws Exception{
        for(int round=0;round<40;round++){
            try(var tracker=new DeploymentTaskTracker(1);var pool=Executors.newFixedThreadPool(2)){
                var queue=new ArrayDeque<Runnable>();var calls=new AtomicInteger();
                var future=tracker.submit(queue::add,()->{calls.incrementAndGet();return null;});var task=queue.remove();
                var start=new CountDownLatch(1);
                var executing=pool.submit(()->{awaitIgnoringInterrupts(start);task.run();});
                var cancelling=pool.submit(()->{awaitIgnoringInterrupts(start);future.cancel(true);tracker.close();});
                start.countDown();executing.get(3,TimeUnit.SECONDS);cancelling.get(3,TimeUnit.SECONDS);
                assertThat(calls.get()).isBetween(0,1);assertThat(tracker.snapshot()).isEqualTo(new DeploymentActivity.Snapshot(0,0));
            }
        }
    }
    @Test void shutdownNowRemovedQueueIsReleasedButRunningTaskRemainsCounted()throws Exception{
        var tracker=new DeploymentTaskTracker(3);var pool=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var later=new AtomicInteger();
        try{
            tracker.submit(pool,()->{entered.countDown();awaitIgnoringInterrupts(release);return null;});assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
            tracker.submit(pool,later::incrementAndGet);tracker.submit(pool,later::incrementAndGet);
            assertThat(tracker.snapshot()).isEqualTo(new DeploymentActivity.Snapshot(2,1));
            assertThat(pool.shutdownNow()).hasSize(2);tracker.close();assertThat(tracker.snapshot()).isEqualTo(new DeploymentActivity.Snapshot(0,1));
        }finally{release.countDown();tracker.close();pool.shutdownNow();}
        assertThat(pool.awaitTermination(3,TimeUnit.SECONDS)).isTrue();assertThat(later).hasValue(0);assertThat(tracker.snapshot().quiescent()).isTrue();
    }
    private static void awaitIgnoringInterrupts(CountDownLatch latch){
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(latch.getCount()!=0&&System.nanoTime()<until)try{latch.await(10,TimeUnit.MILLISECONDS);}catch(InterruptedException ignored){}
        if(latch.getCount()!=0)throw new AssertionError("Fixture was not released");
    }
}
