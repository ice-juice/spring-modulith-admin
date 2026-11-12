package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.admin.common.internal.cache.*;
import com.sma.admin.common.internal.service.*;
import com.sma.admin.common.internal.deployment.SharedStorageProbe.Offer;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.support.IntegrationTest;

@IntegrationTest
class SharedStorageStoreTests {
    @Autowired SharedStorageStore storage;
    @Autowired DeploymentEvidenceStore configuration;
    @Autowired CacheNodeLeaseStore nodes;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    private record Node(CacheNodeIdentity identity,CacheNodeLeaseStore.Lease lease,Offer offer){}
    private record Pair(Node a,Node b){}
    private Node node(String cluster,String id,long version){
        var identity=new CacheNodeIdentity(new CacheNodeProperties(cluster,id,List.of("a","b"),false,version));
        var lease=nodes.heartbeat(identity);var ticket=configuration.challenge(identity,lease);
        assertThat(configuration.report(identity,ticket,new DeploymentEvidenceRegistry.Snapshot("d".repeat(64),Set.of())).matches()).isTrue();
        var offer=new Offer(identity.manifestHash(),id,identity.processId(),lease.epoch(),Map.of("file.legacy",id.equals("a")?"a".repeat(64):"b".repeat(64)));
        return new Node(identity,lease,offer);
    }
    private Pair pair(){
        String cluster="storage-"+UUID.randomUUID();var a=node(cluster,"a",1);var b=node(cluster,"b",1);
        storage.register(a.identity,a.lease,a.offer);storage.register(b.identity,b.lease,b.offer);return new Pair(a,b);
    }
    private SharedStorageStore.View view(Node node){return storage.snapshot(node.identity,node.lease);}
    private SharedStorageStore.Report observe(Node node,boolean own,boolean anchor,List<SharedStorageStore.Read> reads){
        var view=view(node);return storage.observe(node.identity,node.lease,view.self().generation(),view.anchorDigest(),own,anchor,reads);
    }
    private void certify(Pair pair){
        var a=pair.a;var b=pair.b;
        assertThat(observe(a,true,true,List.of(view(b).self().reference())).certified()).isFalse();
        assertThat(observe(b,true,true,List.of(view(a).self().reference())).certified()).isTrue();
        assertThat(observe(a,true,true,List.of(view(b).self().reference())).certified()).isTrue();
    }

    @Test void singleDirectionCannotCertifyAndAnchorOfferCannotBeReplaced(){host(()->{
        String cluster="storage-"+UUID.randomUUID();var a=node(cluster,"a",1);var b=node(cluster,"b",1);
        var pending=storage.register(b.identity,b.lease,b.offer);assertThat(pending.anchor()).isNull();
        assertThat(observe(b,true,true,List.of()).certified()).isFalse();
        var first=storage.register(a.identity,a.lease,a.offer);assertThat(first.anchor()).isEqualTo(a.offer);
        assertThat(storage.register(a.identity,a.lease,a.offer).anchorDigest()).isEqualTo(first.anchorDigest());
        var replacement=new Offer(a.offer.manifestHash(),"a",a.offer.processId(),a.offer.epoch(),Map.of("file.legacy","c".repeat(64)));
        assertThatThrownBy(()->storage.register(a.identity,a.lease,replacement)).isInstanceOf(IllegalStateException.class);
        certify(new Pair(a,b));return null;
    });}

    @Test void isolatedAnchorAndWrongResourceSetNeverCertify(){host(()->{
        var pair=pair();var a=pair.a;var b=pair.b;
        assertThat(observe(a,true,true,List.of(view(b).self().reference())).certified()).isFalse();
        assertThat(observe(b,true,false,List.of(view(a).self().reference())).healthy()).isFalse();
        assertThat(observe(a,true,true,List.of(view(b).self().reference())).certified()).isFalse();
        var current=view(b);
        assertThat(storage.observe(b.identity,b.lease,current.self().generation(),"e".repeat(64),true,true,List.of(view(a).self().reference())).certified()).isFalse();
        var forged=new Offer(b.offer.manifestHash(),"b",b.offer.processId(),b.offer.epoch(),Map.of("file.other","b".repeat(64)));
        assertThatThrownBy(()->storage.register(b.identity,b.lease,forged)).isInstanceOf(IllegalStateException.class);
        return null;
    });}

    @Test void localFailureRevokesOnlySelfAndRecoveryNeedsNewReverseAcknowledgement(){host(()->{
        var pair=pair();certify(pair);var a=pair.a;var b=pair.b;
        var before=view(b).self();var failed=observe(b,false,true,List.of());
        assertThat(failed.certified()).isFalse();assertThat(failed.generation()).isEqualTo(before.generation()+1);
        assertThat(observe(a,true,true,List.of()).certified()).isTrue();
        assertThatThrownBy(()->storage.observe(b.identity,b.lease,before.generation(),view(b).anchorDigest(),true,true,List.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(observe(b,true,true,List.of(view(a).self().reference())).certified()).isFalse();
        assertThat(observe(a,true,true,List.of(view(b).self().reference())).certified()).isTrue();
        assertThat(observe(b,true,true,List.of(view(a).self().reference())).certified()).isTrue();return null;
    });}

    @Test void reportGapInvalidatesOldReverseReadAndPeerOfflineDoesNotRevokeContinuousSelfChecks(){host(()->{
        var pair=pair();certify(pair);var a=pair.a;var b=pair.b;
        jdbc.update("UPDATE common_cache_node_lease SET storage_report_json=JSON_SET(storage_report_json,'$.until','2000-01-01T00:00:00Z') WHERE cluster_hash=? AND node_id='b'",b.identity.clusterHash());
        var expired=view(b).self();
        var next=observe(b,true,true,List.of(view(a).self().reference()));
        assertThat(next.generation()).isEqualTo(expired.generation()+1);assertThat(next.certified()).isFalse();
        observe(a,true,true,List.of(next.reference()));assertThat(observe(b,true,true,List.of(view(a).self().reference())).certified()).isTrue();
        expire(a);assertThat(view(b).peers()).isEmpty();assertThat(observe(b,true,true,List.of()).certified()).isTrue();return null;
    });}

    @Test void replacementProcessCannotInheritOldCertificateOrRewriteAnchor(){host(()->{
        var pair=pair();certify(pair);var old=pair.a;var b=pair.b;var anchor=view(b).anchor();
        expire(old);var next=node(old.identity.properties().cluster(),"a",1);
        assertThat(view(next).self()).isNull();var registered=storage.register(next.identity,next.lease,next.offer);
        assertThat(registered.anchor()).isEqualTo(anchor);assertThat(registered.self().certified()).isFalse();
        assertThat(observe(next,true,true,List.of(view(b).self().reference())).certified()).isFalse();
        assertThatThrownBy(()->view(old)).isInstanceOf(IllegalStateException.class);
        assertThat(observe(b,true,true,List.of(view(next).self().reference())).certified()).isTrue();
        assertThat(observe(next,true,true,List.of(view(b).self().reference())).certified()).isTrue();return null;
    });}

    @Test void configurationMismatchAndTenantOrTransactionCallersCannotUseWitnessState(){
        var pair=host(this::pair);var a=pair.a;var b=pair.b;
        assertThatThrownBy(()->view(a)).isInstanceOf(AccessDeniedException.class);
        host(()->{
            assertThatThrownBy(()->ActorContext.withinJob(1,"probe",()->view(a))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->view(a))).isInstanceOf(IllegalStateException.class);
            var ticket=configuration.challenge(b.identity,b.lease);
            configuration.report(b.identity,ticket,new DeploymentEvidenceRegistry.Snapshot("f".repeat(64),Set.of()));
            assertThatThrownBy(()->view(b)).isInstanceOf(IllegalStateException.class);assertThat(view(a).peers()).isEmpty();return null;
        });
    }
    @Test void newManifestDropsOldAnchorAndLateObservationCannotRestoreFailedGeneration(){host(()->{
        var pair=pair();certify(pair);var a=pair.a;var b=pair.b;
        var old=view(a);observe(a,false,false,List.of());
        assertThatThrownBy(()->storage.observe(a.identity,a.lease,old.self().generation(),old.anchorDigest(),true,true,List.of(view(b).self().reference())))
                .isInstanceOf(IllegalStateException.class);
        expire(a);expire(b);var next=node(a.identity.properties().cluster(),"a",2);
        assertThat(view(next).anchor()).isNull();var initial=storage.register(next.identity,next.lease,next.offer);
        assertThat(initial.anchor()).isEqualTo(next.offer);assertThat(initial.anchorDigest()).isNotEqualTo(old.anchorDigest());
        assertThat(observe(next,true,true,List.of()).certified()).isFalse();
        assertThatThrownBy(()->view(a)).isInstanceOf(IllegalStateException.class);return null;
    });}
    private void expire(Node node){jdbc.update("UPDATE common_cache_node_lease SET lease_until=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE cluster_hash=? AND node_id=?",node.identity.clusterHash(),node.identity.properties().id());}
    private static <T>T host(Supplier<T> work){
        try(var executor=Executors.newSingleThreadExecutor(Thread.ofPlatform().inheritInheritableThreadLocals(false).factory())){
            try{return executor.submit(work::get).get(25,TimeUnit.SECONDS);}
            catch(ExecutionException failure){if(failure.getCause() instanceof RuntimeException runtime)throw runtime;throw new AssertionError(failure.getCause());}
            catch(Exception failure){throw new AssertionError(failure);}
        }
    }
}
