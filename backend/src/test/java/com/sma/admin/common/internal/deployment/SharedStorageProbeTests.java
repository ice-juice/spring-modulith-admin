package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.*;
import com.sma.admin.common.deployment.SharedStorageWitness;
import com.sma.admin.common.internal.cache.*;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.file.internal.service.*;

class SharedStorageProbeTests {
    @TempDir Path root;
    private CacheNodeIdentity node(String id){return new CacheNodeIdentity(new CacheNodeProperties("storage-probe",id,List.of("a","b"),true));}
    private FileClusterStorage adapter(Path directory){return new FileClusterStorage(new FileProperties(directory.toString(),1024),
            new StorageSettings(Map.of("default",directory.toString())),new FileClusterProperties(Set.of("file.legacy","file.local.default"),null));}
    private SharedStorageProbe probe(CacheNodeIdentity node,long epoch,FileClusterStorage adapter){return new SharedStorageProbe(node,epoch,new SharedStorageRegistry(List.of(adapter)));}

    @Test void twoRealAdaptersExchangeBothResourcesAndBindManifestProcessAndEpoch()throws Exception{
        try(var a=adapter(root);var b=adapter(root)){
            var identity=node("a");var first=probe(identity,1,a);var second=probe(node("b"),2,b);
            var left=first.publish();var right=second.publish();
            assertThat(left.digests()).hasSize(2);assertThat(new HashSet<>(left.digests().values())).hasSize(2);
            assertThat(first.verifyOwn()).isTrue();assertThat(second.verifyOwn()).isTrue();
            assertThat(first.verifyPeer(right)).isTrue();assertThat(second.verifyPeer(left)).isTrue();
            assertThat(first.publish()).isEqualTo(left); // Re-observation, not another write.
            assertThat(first.verifyPeer(new SharedStorageProbe.Offer(right.manifestHash(),right.nodeId(),right.processId(),3,right.digests()))).isFalse();
            assertThat(first.verifyPeer(new SharedStorageProbe.Offer(right.manifestHash(),right.nodeId(),UUID.randomUUID().toString(),right.epoch(),right.digests()))).isFalse();
            assertThatThrownBy(()->first.verifyPeer(left)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->first.verifyPeer(new SharedStorageProbe.Offer("f".repeat(64),"b",right.processId(),2,right.digests()))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->first.verifyPeer(new SharedStorageProbe.Offer(right.manifestHash(),"unknown",right.processId(),2,right.digests()))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->first.verifyPeer(new SharedStorageProbe.Offer(right.manifestHash(),"b",right.processId(),2,Map.of("file.legacy",right.digests().get("file.legacy"))))).isInstanceOf(IllegalArgumentException.class);
            assertThat(left.toString()).doesNotContain(root.toString(),left.processId(),left.digests().get("file.legacy"));
            assertThatThrownBy(()->left.digests().clear()).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test void differentMissingAndLostMountsNeverBecomeSharedOrRecreated()throws Exception{
        var isolated=Files.createDirectory(root.resolve("isolated"));var shared=Files.createDirectory(root.resolve("shared"));var absent=root.resolve("absent");
        try(var a=adapter(shared);var b=adapter(isolated);var missing=adapter(absent)){
            var first=probe(node("a"),1,a);var second=probe(node("b"),1,b);
            var left=first.publish();var right=second.publish();assertThat(first.verifyPeer(right)).isFalse();assertThat(second.verifyPeer(left)).isFalse();
            assertThatThrownBy(()->probe(node("a"),1,missing).publish()).isInstanceOf(IllegalStateException.class);
            assertThat(absent).doesNotExist();
            Path own;
            try(var files=Files.walk(shared)){own=files.filter(file->file.getFileName().toString().equals(left.processId()+".probe")).findFirst().orElseThrow();}
            Files.delete(own);assertThat(first.verifyOwn()).isFalse();
            assertThatThrownBy(first::publish).isInstanceOf(IllegalStateException.class);assertThat(own).doesNotExist();
            var replacement=probe(node("a"),2,a);assertThat(replacement.publish().processId()).isNotEqualTo(left.processId());
        }
    }

    @Test void unknownPublicationCanOnlyBeResolvedByVerifyNeverAnotherWrite()throws Exception{
        var adapter=mock(FileClusterStorage.class);when(adapter.resources()).thenReturn(List.of(new SharedStorageWitness.Resource("file.legacy",true)));
        var disk=new HashMap<String,String>();
        doAnswer(call->{var digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(call.getArgument(2,byte[].class)));
            disk.put("digest",digest);throw new IllegalStateException("private-path-and-value");}).when(adapter).publish(anyString(),any(),any());
        when(adapter.verify(anyString(),any(),anyString())).thenAnswer(call->Objects.equals(disk.get("digest"),call.getArgument(2)));
        var probe=probe(node("a"),1,adapter);
        assertThatThrownBy(probe::publish).hasMessage("Shared storage witness unavailable").hasNoCause();
        assertThat(probe.publish().digests()).hasSize(1);verify(adapter,times(1)).publish(anyString(),any(),any());
        disk.clear();assertThat(probe.verifyOwn()).isFalse();assertThatThrownBy(probe::publish).isInstanceOf(IllegalStateException.class);
        verify(adapter,times(1)).publish(anyString(),any(),any());
    }

    @Test void registrationIsLazyStrictlyOwnedBoundedAndFrozen(){
        var adapter=mock(FileClusterStorage.class);var registry=new SharedStorageRegistry(List.of(adapter));verifyNoInteractions(adapter);
        when(adapter.resources()).thenReturn(List.of(new SharedStorageWitness.Resource("file.legacy",false)));
        assertThatThrownBy(registry::resources).isInstanceOf(IllegalStateException.class);
        when(adapter.resources()).thenReturn(List.of(new SharedStorageWitness.Resource("auth.fake",true)));
        assertThatThrownBy(registry::resources).isInstanceOf(IllegalStateException.class);
        when(adapter.resources()).thenReturn(List.of(new SharedStorageWitness.Resource("file.legacy",true)));
        assertThat(registry.resources()).containsOnlyKeys("file.legacy");
        when(adapter.resources()).thenReturn(List.of(new SharedStorageWitness.Resource("file.legacy",true),new SharedStorageWitness.Resource("file.new",true)));
        assertThatThrownBy(registry::resources).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new SharedStorageRegistry(List.of(adapter,adapter)).resources()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new SharedStorageRegistry(List.of()).resources()).isInstanceOf(IllegalStateException.class);
        var many=new ArrayList<SharedStorageWitness.Resource>();for(int i=0;i<33;i++)many.add(new SharedStorageWitness.Resource("file.r"+i,true));
        when(adapter.resources()).thenReturn(many);assertThatThrownBy(()->new SharedStorageRegistry(List.of(adapter)).resources()).isInstanceOf(IllegalStateException.class);
    }

    @Test void cumulativeDeadlineRefusesLateResultsAndDoesNotWriteRemainingResources(){
        var clock=new AtomicLong();var adapter=mock(FileClusterStorage.class);
        when(adapter.resources()).thenReturn(List.of(new SharedStorageWitness.Resource("file.legacy",true),new SharedStorageWitness.Resource("file.second",true)));
        when(adapter.publish(anyString(),any(),any())).thenAnswer(call->{clock.set(Duration.ofSeconds(6).toNanos());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(call.getArgument(2,byte[].class)));});
        var probe=new SharedStorageProbe(node("a"),1,new SharedStorageRegistry(List.of(adapter)),clock::get,Duration.ofSeconds(5));
        assertThatThrownBy(probe::publish).isInstanceOf(IllegalStateException.class);verify(adapter,times(1)).publish(anyString(),any(),any());
        verify(adapter,never()).publish(eq("file.second"),any(),any());assertThat(probe.verifyOwn()).isFalse();
    }

    @Test void httpJobAndOuterTransactionCannotTouchStorage(){
        var adapter=mock(FileClusterStorage.class);var probe=probe(node("a"),1,adapter);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        try{assertThatThrownBy(probe::publish).isInstanceOf(AccessDeniedException.class);}finally{RequestContextHolder.resetRequestAttributes();}
        assertThatThrownBy(()->ActorContext.withinJob(1,"probe",probe::publish)).isInstanceOf(AccessDeniedException.class);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try{assertThatThrownBy(probe::publish).isInstanceOf(IllegalStateException.class);}finally{TransactionSynchronizationManager.clear();}
        verifyNoInteractions(adapter);
    }
    @Test void malformedOffersAreRejectedWithoutEchoingInput(){
        for(String value:List.of("private-path-value","11111111-1111-1111-1111-11111111111Z")){
            assertThatThrownBy(()->new SharedStorageProbe.Offer("a".repeat(64),"a",value,1,Map.of("file.legacy","b".repeat(64))))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid shared storage witness binding");
        }
        assertThatThrownBy(()->new SharedStorageProbe.Offer("a".repeat(64),"a",UUID.randomUUID().toString(),0,Map.of("file.legacy","b".repeat(64))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
