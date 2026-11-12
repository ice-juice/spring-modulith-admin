package com.sma.admin.file.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sma.admin.common.BizException;
import com.sma.admin.common.deployment.DeploymentActivity;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.file.FileErrorCode;

class LocalTransferDeploymentTests {
    @TempDir Path root;
    private LocalTransferStorage.ObjectRef object(){return new LocalTransferStorage.ObjectRef(
            new LocalStorageLocation.Location(1,2,"default",root.toString(),""),UUID.randomUUID().toString(),"part-00001-"+UUID.randomUUID()+".blob");}
    private Path path(LocalTransferStorage.ObjectRef object){return root.resolve("tenants/1/profiles/2/tasks").resolve(object.uploadId()).resolve(object.name());}
    private <T>T job(java.util.function.Supplier<T> action){return ActorContext.withinJob(1,"file-test",action);}

    @Test void timedOutCallerAndRepeatedCloseDoNotHideLiveFileIoOrReleaseItsLock()throws Exception{
        var object=object();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var contexts=new AtomicReference<Throwable>();
        var closes=new AtomicInteger();var inherited=new InheritableThreadLocal<String>();inherited.set("caller");
        InputStream blocked=new InputStream(){
            public int read(){throw new AssertionError("Use bulk read");}
            public int read(byte[] bytes,int offset,int length){
                try{assertThat(inherited.get()).isNull();assertThatThrownBy(ActorContext::require).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);}
                catch(Throwable failure){contexts.set(failure);}
                entered.countDown();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
                while(release.getCount()!=0&&System.nanoTime()<end)try{release.await(10,TimeUnit.MILLISECONDS);}catch(InterruptedException ignored){}
                if(release.getCount()!=0)throw new AssertionError("Fixture not released");return -1;
            }
            public void close(){closes.incrementAndGet();} // deliberately does not unblock the I/O
        };
        try(var storage=new LocalTransferStorage(new LocalTransferProperties(1,Duration.ofSeconds(1),0L));var caller=Executors.newSingleThreadExecutor()){
            var result=caller.submit(()->job(()->storage.write(object,blocked,1,"a".repeat(64))));
            try{
                assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();assertThatThrownBy(()->result.get(3,TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class).cause().isInstanceOfSatisfying(BizException.class,error->assertThat(error.getErrorCode()).isEqualTo(FileErrorCode.STORAGE));
                assertThat(closes.get()).isGreaterThanOrEqualTo(1);assertThat(storage.snapshot()).isEqualTo(new DeploymentActivity.Snapshot(0,1));
                assertThat(Files.exists(path(object))).isTrue();
                try(var channel=FileChannel.open(path(object),StandardOpenOption.READ,StandardOpenOption.WRITE)){
                    assertThatThrownBy(channel::tryLock).isInstanceOf(OverlappingFileLockException.class);
                }
                storage.close();storage.close();assertThat(storage.snapshot().running()).isOne();
                assertThatThrownBy(()->job(()->storage.inspect(object,1,"a".repeat(64)))).isInstanceOf(LocalTransferStorage.NotStarted.class);
            }finally{release.countDown();}
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3)).until(()->storage.snapshot().quiescent());
            assertThat(contexts.get()).isNull();assertThat(Files.exists(path(object))).isTrue();
            try(var channel=FileChannel.open(path(object),StandardOpenOption.READ,StandardOpenOption.WRITE);var lock=channel.tryLock()){assertThat(lock).isNotNull();}
        }finally{release.countDown();inherited.remove();}
    }

    @Test void rejectingBeforeStartClosesUnusedInputWithoutCreatingEvidence(){
        var storage=new LocalTransferStorage(new LocalTransferProperties(1,Duration.ofSeconds(1),0L));storage.close();
        var closed=new AtomicBoolean();var ref=object();
        InputStream input=new ByteArrayInputStream(new byte[]{1}){@Override public void close(){closed.set(true);}};
        assertThatThrownBy(()->job(()->storage.write(ref,input,1,"a".repeat(64)))).isInstanceOf(LocalTransferStorage.NotStarted.class);
        assertThat(closed).isTrue();assertThat(storage.snapshot().quiescent()).isTrue();assertThat(Files.exists(path(ref))).isFalse();
    }
}
