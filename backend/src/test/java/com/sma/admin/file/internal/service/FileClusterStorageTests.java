package com.sma.admin.file.internal.service;
import static org.assertj.core.api.Assertions.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.*;
import com.sma.admin.common.deployment.SharedStorageWitness;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;

class FileClusterStorageTests {
    @TempDir Path temp;
    private SharedStorageWitness.Scope scope(){return new SharedStorageWitness.Scope("test-deployment","node-a",UUID.randomUUID());}
    private FileClusterStorage adapter(Path root){return new FileClusterStorage(new FileProperties(root.toString(),1024),new StorageSettings(Map.of("default",root.toString())),new FileClusterProperties(Set.of("file.legacy","file.local.default"),null));}
    private Path probe(Path root)throws Exception{try(var paths=Files.walk(root)){return paths.filter(p->p.getFileName().toString().endsWith(".probe")).findFirst().orElseThrow();}}

    @Test void actualIndependentAdaptersVerifySharedBytesAndCannotRetirePeersOrOverwrite(){
        var scope=scope();byte[] challenge=new byte[32];new java.security.SecureRandom().nextBytes(challenge);
        try(var a=adapter(temp);var b=adapter(temp)){
            String hash=a.publish("file.legacy",scope,challenge);assertThat(hash).matches("[0-9a-f]{64}");assertThat(b.verify("file.legacy",scope,hash)).isTrue();
            assertThat(a.publish("file.legacy",scope,challenge)).isEqualTo(hash);b.retire("file.legacy",scope,hash);assertThat(a.verify("file.legacy",scope,hash)).isTrue();
            byte[] different=challenge.clone();different[0]^=1;
            assertThatThrownBy(()->a.publish("file.legacy",scope,different)).isInstanceOf(IllegalStateException.class);
            assertThat(b.verify("file.legacy",scope,hash)).isTrue();
            assertThat(b.verify("file.local.default",scope,hash)).isFalse();
            assertThat(b.verify("file.legacy",new SharedStorageWitness.Scope("other-deployment","node-a",scope.processId()),hash)).isFalse();
            assertThat(b.verify("file.legacy",new SharedStorageWitness.Scope(scope.group(),"node-b",scope.processId()),hash)).isFalse();
            assertThat(b.verify("file.legacy",scope(),hash)).isFalse();
            a.retire("file.legacy",scope,hash);assertThat(b.verify("file.legacy",scope,hash)).isFalse();
        }
    }
    @Test void separateOrMissingRootsDoNotPassAndMissingMountIsNeverCreated()throws Exception{
        Path first=Files.createDirectory(temp.resolve("first")),second=Files.createDirectory(temp.resolve("second")),missing=temp.resolve("unmounted");var scope=scope();
        try(var a=adapter(first);var b=adapter(second);var absent=adapter(missing)){
            String hash=a.publish("file.legacy",scope,new byte[32]);assertThat(b.verify("file.legacy",scope,hash)).isFalse();
            assertThatThrownBy(()->absent.publish("file.legacy",scope,new byte[32])).isInstanceOf(IllegalStateException.class);
            assertThat(Files.exists(missing)).isFalse();assertThat(a.resources().toString()).doesNotContain(first.toString());
        }
    }
    @Test void busyAndChangedWitnessesAreNotAcceptedOverwrittenOrDeleted()throws Exception{
        var scope=scope();try(var a=adapter(temp);var b=adapter(temp)){
            String hash=a.publish("file.legacy",scope,new byte[32]);Path file=probe(temp);
            try(var channel=FileChannel.open(file,StandardOpenOption.READ,StandardOpenOption.WRITE);var lock=channel.lock()){
                assertThat(b.verify("file.legacy",scope,hash)).isFalse();
                assertThatThrownBy(()->a.retire("file.legacy",scope,hash)).isInstanceOf(IllegalStateException.class);
            }
            Files.write(file,new byte[33],StandardOpenOption.TRUNCATE_EXISTING);
            assertThat(b.verify("file.legacy",scope,hash)).isFalse();assertThatThrownBy(()->a.publish("file.legacy",scope,new byte[32])).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(()->a.retire("file.legacy",scope,hash)).isInstanceOf(IllegalStateException.class);assertThat(Files.size(file)).isEqualTo(33);
        }
    }
    @Test void undeclaredUnknownHttpAndTransactionUseFailBeforeFilesystemEffects(){
        var scope=scope();var legacy=new FileProperties(temp.toString(),1024);var managed=new StorageSettings(Map.of("default",temp.toString()));
        try(var undeclared=new FileClusterStorage(legacy,managed,new FileClusterProperties(null,null));var declared=adapter(temp)){
            assertThat(undeclared.resources()).allMatch(resource->!resource.declaredShared());
            assertThatThrownBy(()->undeclared.publish("file.legacy",scope,new byte[32])).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->declared.publish("file.local.unknown",scope,new byte[32])).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->declared.publish("file.legacy",scope,new byte[31])).isInstanceOf(IllegalArgumentException.class);
            Users.as(Users.principal(1,1,DataAccess.unrestricted()),()->{assertThatThrownBy(declared::resources).isInstanceOf(AccessDeniedException.class);return null;});
            var previous=RequestContextHolder.getRequestAttributes();
            try{RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("GET","/actuator/health")));assertThatThrownBy(declared::resources).isInstanceOf(AccessDeniedException.class);}
            finally{RequestContextHolder.setRequestAttributes(previous);}
            boolean active=TransactionSynchronizationManager.isActualTransactionActive();
            try{TransactionSynchronizationManager.setActualTransactionActive(true);assertThatThrownBy(()->declared.publish("file.legacy",scope,new byte[32])).isInstanceOf(IllegalStateException.class);}
            finally{TransactionSynchronizationManager.setActualTransactionActive(active);}
            assertThat(Files.exists(temp.resolve(".sma-cluster"))).isFalse();
        }
        assertThatThrownBy(()->new SharedStorageWitness.Scope("../escape","node-a",UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new FileClusterStorage(legacy,managed,new FileClusterProperties(Set.of("file.local.unknown"),null))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void deadlineCancelsBeforeWorkAndSaturatedWorkerDoesNotSpawnLateWrites()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var done=new CountDownLatch(1);
        var worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new SynchronousQueue<>()){
            @Override protected void beforeExecute(Thread thread,Runnable task){entered.countDown();try{release.await();}catch(InterruptedException stopped){Thread.currentThread().interrupt();}}
            @Override protected void afterExecute(Runnable task,Throwable failure){done.countDown();}
        };
        try(var storage=new FileClusterStorage(new FileProperties(temp.toString(),1024),new StorageSettings(Map.of("default",temp.toString())),new FileClusterProperties(Set.of("file.legacy"),Duration.ofMillis(100)),worker)){
            try{
                assertThatThrownBy(()->storage.publish("file.legacy",scope(),new byte[32])).isInstanceOf(IllegalStateException.class).hasMessage("Shared storage witness unavailable");
                assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(()->storage.publish("file.legacy",scope(),new byte[32])).isInstanceOf(IllegalStateException.class);
            }finally{release.countDown();}
            assertThat(done.await(5,TimeUnit.SECONDS)).isTrue();assertThat(Files.exists(temp.resolve(".sma-cluster"))).isFalse();assertThat(worker.getLargestPoolSize()).isEqualTo(1);
        }
    }
    @Test void realDirectoryLinkCannotRedirectProbeIntoAnotherRoot()throws Exception{
        Path base=Files.createDirectory(temp.resolve("base")),outside=Files.createDirectory(temp.resolve("outside")),link=base.resolve(".sma-cluster");
        if(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")){
            var process=new ProcessBuilder("cmd.exe","/c","mklink","/J",link.toString(),outside.toString()).redirectErrorStream(true).start();
            assertThat(process.waitFor(5,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).isZero();
        }else Files.createSymbolicLink(link,outside);
        try(var storage=adapter(base)){
            assertThatThrownBy(()->storage.publish("file.legacy",scope(),new byte[32])).isInstanceOf(IllegalStateException.class);
            try(var contents=Files.list(outside)){assertThat(contents.count()).isZero();}
        }finally{Files.deleteIfExists(link);}
        assertThat(Files.isDirectory(outside)).isTrue();
    }
}
