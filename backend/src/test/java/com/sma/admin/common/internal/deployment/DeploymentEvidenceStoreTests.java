package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.admin.common.deployment.DeploymentEvidence;
import com.sma.admin.common.internal.cache.*;
import com.sma.admin.common.internal.service.CacheNodeLeaseStore;
import com.sma.admin.common.internal.service.DeploymentEvidenceStore;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.support.IntegrationTest;

@IntegrationTest
class DeploymentEvidenceStoreTests {
    @Autowired DeploymentEvidenceStore store;
    @Autowired CacheNodeLeaseStore membership;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    private static DeploymentEvidenceRegistry.Snapshot good(String value){return new DeploymentEvidenceRegistry.Snapshot(value.repeat(64),Set.of());}
    private CacheNodeIdentity node(String cluster,String name,long version){return new CacheNodeIdentity(new CacheNodeProperties(cluster,name,List.of("a","b"),false,version));}
    private String cluster(){return "evidence-"+UUID.randomUUID();}

    @Test void designatedBootstrapThenMatchingPeerAndWrongPeerCannotRewriteBaseline(){host(()->{
        var cluster=cluster();var a=node(cluster,"a",1);var b=node(cluster,"b",1);
        var la=membership.heartbeat(a);var lb=membership.heartbeat(b);
        var tb=store.challenge(b,lb);var ta=store.challenge(a,la);
        assertThat(ta.challenge()).isEqualTo(tb.challenge());
        assertThat(store.report(b,tb,good("b")).state()).isEqualTo(DeploymentEvidenceStore.State.WAITING_BASELINE);
        assertThat(store.report(a,ta,good("a")).matches()).isTrue();
        assertThat(store.report(b,tb,good("b")).state()).isEqualTo(DeploymentEvidenceStore.State.MISMATCH);
        assertThat(store.inspect(a,la).matches()).isTrue();
        assertThat(store.report(a,ta,good("b")).state()).isEqualTo(DeploymentEvidenceStore.State.MISMATCH);
        assertThat(store.report(a,ta,good("a")).matches()).isTrue();
        assertThat(store.report(b,tb,good("a")).matches()).isTrue();
        var unavailable=new DeploymentEvidenceRegistry.Snapshot(null,Set.of("auth.security-keys"));
        assertThat(store.report(b,tb,unavailable).state()).isEqualTo(DeploymentEvidenceStore.State.UNAVAILABLE);
        assertThat(store.inspect(a,la).matches()).isTrue();
        assertThat(jdbc.queryForObject("SELECT evidence_digest FROM common_cache_node_group WHERE cluster_hash=?",String.class,a.clusterHash())).isEqualTo("a".repeat(64));
        return null;
    });}

    @Test void expiredEvidenceIsNotExtendedByHeartbeatAndNewProcessMustProveAgain(){host(()->{
        var cluster=cluster();var a=node(cluster,"a",1);var lease=membership.heartbeat(a);var ticket=store.challenge(a,lease);
        var verdict=store.report(a,ticket,good("a"));assertThat(verdict.until()).isBeforeOrEqualTo(lease.until());
        jdbc.update("UPDATE common_cache_node_lease SET evidence_until=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 SECOND) WHERE cluster_hash=? AND node_id='a'",a.clusterHash());
        lease=membership.heartbeat(a);assertThat(store.inspect(a,lease).state()).isEqualTo(DeploymentEvidenceStore.State.STALE);
        assertThat(store.report(a,ticket,good("a")).matches()).isTrue();
        expire(a);var replacement=node(cluster,"a",1);var nextLease=membership.heartbeat(replacement);
        assertThat(nextLease.epoch()).isGreaterThan(lease.epoch());
        assertThat(store.inspect(replacement,nextLease).state()).isEqualTo(DeploymentEvidenceStore.State.STALE);
        assertThatThrownBy(()->store.report(a,ticket,good("a"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->store.report(replacement,ticket,good("a"))).isInstanceOf(IllegalStateException.class);
        var nextTicket=store.challenge(replacement,nextLease);assertThat(nextTicket.challenge()).isEqualTo(ticket.challenge());
        assertThat(store.report(replacement,nextTicket,good("b")).state()).isEqualTo(DeploymentEvidenceStore.State.MISMATCH);
        assertThat(store.report(replacement,nextTicket,good("a")).matches()).isTrue();return null;
    });}

    @Test void manifestAdvanceCreatesFreshChallengeOnlyAfterOldLeasesExit(){host(()->{
        var cluster=cluster();var a=node(cluster,"a",1);var b=node(cluster,"b",1);
        var la=membership.heartbeat(a);var lb=membership.heartbeat(b);var ta=store.challenge(a,la);var tb=store.challenge(b,lb);
        store.report(a,ta,good("a"));store.report(b,tb,good("a"));
        var next=node(cluster,"a",2);
        assertThatThrownBy(()->membership.heartbeat(next)).isInstanceOf(com.sma.admin.common.cache.CacheTaskRejected.class);
        expire(a);assertThat(store.inspect(b,lb).matches()).isTrue(); // Healthy peer is independent of the bootstrap node.
        expire(b);var nextLease=membership.heartbeat(next);var nextTicket=store.challenge(next,nextLease);
        assertThat(nextTicket.challenge().nonce()).isNotEqualTo(ta.challenge().nonce());
        assertThatThrownBy(()->store.report(a,ta,good("a"))).isInstanceOf(IllegalStateException.class);
        assertThat(store.report(next,nextTicket,good("b")).matches()).isTrue();
        assertThatThrownBy(()->membership.heartbeat(a)).isInstanceOf(com.sma.admin.common.cache.CacheTaskRejected.class);return null;
    });}

    @Test void staleChallengeForgedEpochAndExpiredLeaseCannotPublish(){host(()->{
        var a=node(cluster(),"a",1);var lease=membership.heartbeat(a);var ticket=store.challenge(a,lease);
        var forged=new DeploymentEvidenceStore.Ticket(new DeploymentEvidence.Challenge(a.clusterHash(),a.manifestHash(),"f".repeat(64)),a.processId(),lease.epoch());
        assertThatThrownBy(()->store.report(a,forged,good("a"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->store.report(a,new DeploymentEvidenceStore.Ticket(ticket.challenge(),a.processId(),lease.epoch()+1),good("a"))).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT evidence_digest FROM common_cache_node_group WHERE cluster_hash=?",String.class,a.clusterHash())).isNull();
        expire(a);assertThatThrownBy(()->store.report(a,ticket,good("a"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->store.challenge(a,lease)).isInstanceOf(IllegalStateException.class);return null;
    });}

    @Test void concurrentReportsKeepExactlyOneImmutableBootstrapValue()throws Exception{
        var a=node(cluster(),"a",1);var lease=host(()->membership.heartbeat(a));var ticket=host(()->store.challenge(a,lease));
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var results=List.of(pool.submit(()->{start.await();return store.report(a,ticket,good("a"));}),
                    pool.submit(()->{start.await();return store.report(a,ticket,good("b"));}));
            start.countDown();var states=new ArrayList<DeploymentEvidenceStore.State>();
            for(var future:results)states.add(future.get(10,TimeUnit.SECONDS).state());
            assertThat(states).containsExactlyInAnyOrder(DeploymentEvidenceStore.State.MATCH,DeploymentEvidenceStore.State.MISMATCH);
        }
        var digest=jdbc.queryForObject("SELECT evidence_digest FROM common_cache_node_group WHERE cluster_hash=?",String.class,a.clusterHash());
        assertThat(digest).isIn("a".repeat(64),"b".repeat(64));
        assertThat(host(()->store.report(a,ticket,new DeploymentEvidenceRegistry.Snapshot(digest,Set.of()))).matches()).isTrue();
    }

    @Test void rejectsHttpTenantJobsAndOuterTransactionsBeforeCreatingAnyEvidence(){
        var a=node(cluster(),"a",1);var lease=host(()->membership.heartbeat(a));
        assertThatThrownBy(()->store.challenge(a,lease)).isInstanceOf(AccessDeniedException.class); // Spring test request.
        host(()->{
            assertThatThrownBy(()->ActorContext.withinJob(1,"evidence",()->store.challenge(a,lease))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->store.challenge(a,lease))).isInstanceOf(IllegalStateException.class);
            return null;
        });
        assertThat(jdbc.queryForObject("SELECT evidence_nonce FROM common_cache_node_group WHERE cluster_hash=?",String.class,a.clusterHash())).isNull();
    }

    private void expire(CacheNodeIdentity identity){jdbc.update("UPDATE common_cache_node_lease SET lease_until=? WHERE cluster_hash=? AND node_id=?",Instant.EPOCH,identity.clusterHash(),identity.properties().id());}
    private static <T>T host(Supplier<T> work){
        try(var executor=Executors.newSingleThreadExecutor(Thread.ofPlatform().inheritInheritableThreadLocals(false).factory())){
            try{return executor.submit(work::get).get(20,TimeUnit.SECONDS);}
            catch(ExecutionException failure){if(failure.getCause() instanceof RuntimeException runtime)throw runtime;throw new AssertionError(failure.getCause());}
            catch(Exception failure){throw new AssertionError(failure);}
        }
    }
}
