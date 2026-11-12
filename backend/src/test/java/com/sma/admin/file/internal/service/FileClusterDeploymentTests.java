package com.sma.admin.file.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sma.admin.common.deployment.*;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.support.MaintenanceCallerAssertions;

class FileClusterDeploymentTests {
    @TempDir Path root;
    private FileClusterStorage storage(){return storage(Duration.ofSeconds(2));}
    private FileClusterStorage storage(Duration timeout){return new FileClusterStorage(new FileProperties(root.toString(),1024),
            new StorageSettings(Map.of("default",root.toString())),new FileClusterProperties(Set.of("file.legacy"),timeout));}
    @Test void timeoutAndCloseRetainActualFileIoAndLockUntilFinallyExits()throws Exception{
        Path file=Files.write(root.resolve("held-probe"),new byte[32]);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var context=new AtomicReference<Throwable>();var inherited=new InheritableThreadLocal<String>();inherited.set("host-caller");
        try(var storage=storage(Duration.ofMillis(250));var caller=Executors.newSingleThreadExecutor()){
            var result=caller.submit(()->storage.execute(()->{
                try{assertThat(inherited.get()).isNull();assertThatThrownBy(ActorContext::require).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
                    assertThat(org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).isNull();}
                catch(Throwable failure){context.set(failure);}
                try(var channel=FileChannel.open(file,StandardOpenOption.READ,StandardOpenOption.WRITE);var lock=channel.lock()){
                    entered.countDown();awaitIgnoringInterrupts(release);return true;
                }
            }));
            try{
                assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();assertThatThrownBy(()->result.get(3,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                        .cause().isInstanceOf(IllegalStateException.class).hasMessage("Shared storage witness unavailable");
                assertThat(storage.snapshot()).isEqualTo(new DeploymentActivity.Snapshot(0,1));
                storage.close();storage.close();assertThat(storage.snapshot().running()).isOne();
                try(var channel=FileChannel.open(file,StandardOpenOption.READ,StandardOpenOption.WRITE)){
                    assertThatThrownBy(channel::tryLock).isInstanceOf(OverlappingFileLockException.class);
                }
                assertThatThrownBy(()->storage.execute(()->true)).isInstanceOf(IllegalStateException.class).hasMessage("Shared storage witness unavailable");
            }finally{release.countDown();}
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3)).until(()->storage.snapshot().quiescent());
            assertThat(context.get()).isNull();assertThat(Files.readAllBytes(file)).hasSize(32);
            try(var channel=FileChannel.open(file,StandardOpenOption.READ,StandardOpenOption.WRITE);var lock=channel.tryLock()){assertThat(lock).isNotNull();}
        }finally{release.countDown();inherited.remove();}
    }
    @Test void queuedCancellationAndRejectedExecutorDoNotCreateProbeOrLeakCounts()throws Exception{
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);var completed=new CountDownLatch(1);
        var worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new SynchronousQueue<>()){
            @Override protected void beforeExecute(Thread thread,Runnable task){started.countDown();awaitIgnoringInterrupts(release);}
            @Override protected void afterExecute(Runnable task,Throwable error){completed.countDown();}
        };
        var scope=new SharedStorageWitness.Scope("deployment","node-a",UUID.randomUUID());
        try(var storage=new FileClusterStorage(new FileProperties(root.toString(),1024),new StorageSettings(Map.of("default",root.toString())),
                new FileClusterProperties(Set.of("file.legacy"),Duration.ofMillis(100)),worker)){
            try{
                assertThatThrownBy(()->storage.publish("file.legacy",scope,new byte[32])).isInstanceOf(IllegalStateException.class);
                assertThat(started.await(2,TimeUnit.SECONDS)).isTrue();assertThat(storage.snapshot().quiescent()).isTrue();
                assertThatThrownBy(()->storage.publish("file.legacy",scope,new byte[32])).isInstanceOf(IllegalStateException.class);
                assertThat(storage.snapshot().quiescent()).isTrue();
            }finally{release.countDown();}
            assertThat(completed.await(3,TimeUnit.SECONDS)).isTrue();assertThat(Files.exists(root.resolve(".sma-cluster"))).isFalse();
        }finally{release.countDown();worker.shutdownNow();}
    }
    @Test void actualWitnessSuccessAndFailureReleaseAndPackageSeamKeepsHostBoundary()throws Exception{
        try(var storage=storage()){
            var scope=new SharedStorageWitness.Scope("deployment","node-a",UUID.randomUUID());
            var hash=storage.publish("file.legacy",scope,new byte[32]);assertThat(storage.verify("file.legacy",scope,hash)).isTrue();
            assertThatThrownBy(()->storage.execute(()->{throw new java.io.IOException();})).isInstanceOf(IllegalStateException.class);
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2)).until(()->storage.snapshot().quiescent());
            MaintenanceCallerAssertions.hostOnly(()->storage.execute(()->true));
            storage.close();assertThatThrownBy(()->storage.publish("file.legacy",scope,new byte[32])).isInstanceOf(IllegalStateException.class);
            try(var files=Files.walk(root)){assertThat(files.filter(path->path.toString().endsWith(".probe")).count()).isEqualTo(1);}
        }
    }
    private static void awaitIgnoringInterrupts(CountDownLatch latch){
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(latch.getCount()!=0&&System.nanoTime()<until)try{latch.await(10,TimeUnit.MILLISECONDS);}catch(InterruptedException ignored){}
        if(latch.getCount()!=0)throw new AssertionError("Fixture not released");
    }
}
