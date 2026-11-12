package com.sma.admin.common.internal.deployment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import com.sma.admin.common.internal.cache.*;
import com.sma.admin.common.internal.service.*;
import com.sma.admin.file.internal.service.*;
import com.sma.admin.support.IntegrationTest;

@IntegrationTest
class SharedStorageRuntimeIntegrationTests {
    @TempDir Path temp;
    @Autowired SharedStorageStore storage;
    @Autowired DeploymentEvidenceStore evidence;
    @Autowired CacheNodeLeaseStore nodes;
    @Autowired JdbcTemplate jdbc;
    private record Node(CacheNodeIdentity identity,CacheNodeLeaseStore.Lease lease,FileClusterStorage adapter,SharedStorageRuntime runtime) implements AutoCloseable{
        @Override public void close(){runtime.close();adapter.close();}
    }
    private Node node(String cluster,String id,Path path){
        var identity=new CacheNodeIdentity(new CacheNodeProperties(cluster,id,List.of("a","b"),true));
        var lease=host(()->{
            var registered=nodes.heartbeat(identity);var ticket=evidence.challenge(identity,registered);
            assertThat(evidence.report(identity,ticket,new DeploymentEvidenceRegistry.Snapshot("a".repeat(64),Set.of())).matches()).isTrue();return registered;
        });
        var membership=mock(CacheNodeRuntime.class);when(membership.currentLease()).thenReturn(lease);
        var config=mock(DeploymentEvidenceRuntime.class);when(config.status()).thenReturn(new DeploymentEvidenceRuntime.Status("MATCH"));
        var adapter=new FileClusterStorage(new FileProperties(path.toString(),1024),new StorageSettings(Map.of("default",path.toString())),new FileClusterProperties(Set.of("file.legacy","file.local.default"),null));
        return new Node(identity,lease,adapter,new SharedStorageRuntime(identity,membership,config,storage,new SharedStorageRegistry(List.of(adapter))));
    }
    private void tick(Node node){host(()->{node.runtime.tick();return null;});}
    private void qualify(Node a,Node b){await().atMost(Duration.ofSeconds(8)).pollInterval(Duration.ofMillis(100)).until(()->{
        tick(a);tick(b);return a.runtime.status().ready()&&b.runtime.status().ready();
    });}
    @Test void realFileIoAndMysqlMutualProofRevokesLostSelfButKeepsHealthyPeer()throws Exception{
        var shared=Files.createDirectory(temp.resolve("shared"));var cluster="storage-io-"+UUID.randomUUID();
        try(var a=node(cluster,"a",shared);var b=node(cluster,"b",shared)){
            qualify(a,b);
            var before=host(()->storage.snapshot(b.identity,b.lease)).self();assertThat(before.certified()).isTrue();
            Path file;try(var files=Files.walk(shared)){file=files.filter(path->path.getFileName().toString().equals(b.identity.processId()+".probe")).findFirst().orElseThrow();}
            Files.delete(file);
            await().atMost(Duration.ofSeconds(5)).until(()->{tick(b);return b.runtime.status().state().equals("UNAVAILABLE");});
            await().atMost(Duration.ofSeconds(5)).until(()->{tick(a);return a.runtime.status().ready();});
            assertThat(file).doesNotExist();
            var after=host(()->storage.snapshot(b.identity,b.lease)).self();assertThat(after.certified()).isFalse();assertThat(after.generation()).isGreaterThan(before.generation());
        }
    }
    @Test void independentDirectoriesCannotObtainMutualQualification()throws Exception{
        var first=Files.createDirectory(temp.resolve("first"));var second=Files.createDirectory(temp.resolve("second"));var cluster="storage-split-"+UUID.randomUUID();
        try(var a=node(cluster,"a",first);var b=node(cluster,"b",second)){
            await().atMost(Duration.ofSeconds(6)).until(()->{tick(a);tick(b);
                return a.runtime.status().state().equals("WAITING_PEER")&&b.runtime.status().state().equals("UNAVAILABLE");});
            assertThat(a.runtime.status().ready()).isFalse();assertThat(b.runtime.status().ready()).isFalse();
            assertThat(host(()->storage.snapshot(a.identity,a.lease)).self().certified()).isFalse();
        }
    }
    private static <T>T host(Supplier<T> work){
        try(var executor=Executors.newSingleThreadExecutor(Thread.ofPlatform().inheritInheritableThreadLocals(false).factory())){
            try{return executor.submit(work::get).get(10,TimeUnit.SECONDS);}
            catch(ExecutionException failure){if(failure.getCause() instanceof RuntimeException runtime)throw runtime;throw new AssertionError(failure.getCause());}
            catch(Exception failure){throw new AssertionError(failure);}
        }
    }
}
