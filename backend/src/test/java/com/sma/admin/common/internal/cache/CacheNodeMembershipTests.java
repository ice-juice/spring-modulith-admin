package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import com.sma.admin.common.BizException;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.persistence.*;
import com.sma.admin.common.internal.service.CacheNodeLeaseStore;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
@TestPropertySource(properties={"sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "sma.cache.nodes.cluster=p08-node-membership-tests","sma.cache.nodes.id=node-a","sma.cache.nodes.expected=node-a,node-b"})
class CacheNodeMembershipTests {
    @Autowired CacheNodeLeaseStore nodes;
    @Autowired CacheNodeLeaseMapper leases;
    @Autowired CacheNodeGroupMapper groups;
    @Autowired CacheNodeIdentity identity;
    @Autowired CacheTaskMapper taskRows;
    @Autowired CacheTasks tasks;
    @Autowired MockMvcTester mvc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    private static final String REGION="config.dictionary-options";

    @Test void twoNodesRegisterAndConflictingLiveIncarnationCannotReplaceEither(){
        String cluster="node-test-"+UUID.randomUUID();var a=node(cluster,"a",List.of("a","b"));var b=node(cluster,"b",List.of("a","b"));
        var aLease=host(()->nodes.heartbeat(a));host(()->nodes.heartbeat(b));
        assertThat(host(()->nodes.heartbeat(a)).epoch()).isEqualTo(aLease.epoch());
        assertThatThrownBy(()->host(()->nodes.heartbeat(node(cluster,"a",List.of("a","b"))))).isInstanceOf(CacheTaskRejected.class);
        var snapshot=nodes.snapshot(a);assertThat(snapshot.members()).extracting(CacheNodeScope.Member::stateAtCapture).containsExactly("ONLINE","ONLINE");
        assertThat(snapshot.members().getFirst().processId()).isEqualTo(a.processId());
    }
    @Test void expiredNodeCanBeTakenOverButTheOldProcessCannotRenewTheNewEpoch(){
        String cluster="node-test-"+UUID.randomUUID();var old=node(cluster,"a",List.of("a"));var next=node(cluster,"a",List.of("a"));
        var before=host(()->nodes.heartbeat(old));expire(old,"a");assertThat(nodes.snapshot(old).members().getFirst().stateAtCapture()).isEqualTo("OFFLINE");
        var after=host(()->nodes.heartbeat(next));assertThat(after.epoch()).isEqualTo(before.epoch()+1);
        assertThatThrownBy(()->host(()->nodes.heartbeat(old))).isInstanceOf(CacheTaskRejected.class);
        assertThat(nodes.snapshot(next).members().getFirst().processId()).isEqualTo(next.processId());
    }
    @Test void differentManifestsCannotCoexistWhileAnyNodeLeaseIsLive(){
        String cluster="node-test-"+UUID.randomUUID();var old=node(cluster,"a",List.of("a"));
        var changed=new CacheNodeIdentity(new CacheNodeProperties(cluster,"a",List.of("a","b"),false,2L));
        host(()->nodes.heartbeat(old));assertThatThrownBy(()->host(()->nodes.heartbeat(changed))).isInstanceOf(CacheTaskRejected.class);
        assertThat(nodes.snapshot(changed).members()).allSatisfy(member->assertThat(member.stateAtCapture()).isEqualTo("CONFIGURATION_MISMATCH"));
        expire(old,"a");host(()->nodes.heartbeat(changed));
        assertThat(nodes.snapshot(changed).members()).extracting(CacheNodeScope.Member::stateAtCapture).containsExactly("ONLINE","UNKNOWN");
        expire(changed,"a");assertThatThrownBy(()->host(()->nodes.heartbeat(old))).isInstanceOf(CacheTaskRejected.class);
        assertThatThrownBy(()->host(()->nodes.heartbeat(node(cluster,"a",List.of("a","b"))))).isInstanceOf(CacheTaskRejected.class);
    }
    @Test void concurrentIncarnationsHaveExactlyOneLeaseWinner() throws Exception {
        String cluster="node-test-"+UUID.randomUUID();var a=node(cluster,"a",List.of("a"));var b=node(cluster,"a",List.of("a"));var start=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var futures=new ArrayList<Future<Boolean>>();
            for(var candidate:List.of(a,b))futures.add(pool.submit(()->{start.await();try{host(()->nodes.heartbeat(candidate));return true;}catch(CacheTaskRejected conflict){return false;}}));
            start.countDown();int successes=0;for(var future:futures)if(future.get(15,TimeUnit.SECONDS))successes++;assertThat(successes).isEqualTo(1);
        }
    }
    @Test void tenantSubmissionDoesNotBlockAnExistingProcessesHeartbeat() throws Exception {
        var node=node("node-test-"+UUID.randomUUID(),"a",List.of("a"));var first=host(()->nodes.heartbeat(node));var snapshot=nodes.snapshot(node);
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var submitted=pool.submit(()->Users.as(manager(97405),()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
                nodes.validateFrozen(node,snapshot);held.countDown();
                try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("test release timeout");}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
                return true;
            })));
            try{
                assertThat(held.await(3,TimeUnit.SECONDS)).isTrue();
                var renewal=pool.submit(()->host(()->nodes.heartbeat(node)));
                assertThat(renewal.get(3,TimeUnit.SECONDS).epoch()).isEqualTo(first.epoch());
            }finally{release.countDown();}
            assertThat(submitted.get(5,TimeUnit.SECONDS)).isTrue();
        }
    }
    @Test void humanAndTenantJobCannotMutateGlobalMembershipAndReadOnlyPreviewDoesNotCreateIt(){
        var local=node("node-test-"+UUID.randomUUID(),"a",List.of("a"));
        Users.as(manager(97401),()->{
            assertThatThrownBy(()->nodes.heartbeat(local)).isInstanceOf(AccessDeniedException.class);
            assertThat(nodes.snapshot(local).members().getFirst().stateAtCapture()).isEqualTo("UNKNOWN");return null;
        });
        ActorContext.withinJob(97401,"cache-dispatch",()->{assertThatThrownBy(()->nodes.heartbeat(local)).isInstanceOf(AccessDeniedException.class);return null;});
        assertThat(groups.selectCount(new LambdaQueryWrapper<CacheNodeGroupEntity>().eq(CacheNodeGroupEntity::getClusterHash,local.clusterHash()))).isZero();
    }
    @Test void taskScopeFreezesIncarnationsAndRejectsFirstSubmissionAfterReplacement(){
        expire(identity,"node-a");expire(identity,"node-b");host(()->nodes.heartbeat(identity));
        var b=node(identity.properties().cluster(),"node-b",identity.properties().expected());host(()->nodes.heartbeat(b));
        var actor=manager(97402);String id=Users.as(actor,()->preview().id());
        Users.as(actor,()->{
            var scope=tasks.nodeScope(id);assertThat(scope.members()).hasSize(2);
            assertThat(scope.members().get(1).processId()).isEqualTo(b.processId());
            assertThat(mvc.get().uri("/api/monitor/caches/tasks/"+id+"/nodes").with(Users.request(actor))).hasStatusOk().bodyJson()
                    .hasPathSatisfying("$.data.members[0].nodeId",v->v.assertThat().isEqualTo("node-a"))
                    .doesNotHavePath("$.data.cluster").doesNotHavePath("$.data.host");return null;
        });
        expire(b,"node-b");var replacement=node(b.properties().cluster(),"node-b",b.properties().expected());host(()->nodes.heartbeat(replacement));
        Users.as(actor,()->{
            assertThatThrownBy(()->tasks.submit(id,"node-scope-changed-0002")).isInstanceOf(CacheTaskRejected.class);
            assertThat(tasks.get(id).items()).isEmpty();assertThat(tasks.nodeScope(id).members().get(1).processId()).isEqualTo(b.processId());
            var refreshed=preview();assertThat(tasks.submit(refreshed.id(),"node-scope-refreshed-02").state()).isEqualTo(CacheTasks.State.QUEUED);return null;
        });
        Users.as(manager(97403),()->{assertThatThrownBy(()->tasks.nodeScope(id)).isInstanceOf(BizException.class);return null;});
    }
    @Test void offlineNodesStayInFrozenScopeAndLegacyTasksHaveUnknownCoverage(){
        expire(identity,"node-a");expire(identity,"node-b");host(()->nodes.heartbeat(identity));
        var b=node(identity.properties().cluster(),"node-b",identity.properties().expected());host(()->nodes.heartbeat(b));expire(b,"node-b");
        Users.as(manager(97404),()->{
            var preview=preview();assertThat(tasks.nodeScope(preview.id()).members().get(1).stateAtCapture()).isEqualTo("OFFLINE");
            tasks.submit(preview.id(),"node-scope-offline-0004");
            taskRows.update(null,new LambdaUpdateWrapper<CacheTaskEntity>().eq(CacheTaskEntity::getPublicId,preview.id()).set(CacheTaskEntity::getNodeScopeJson,null));
            assertThat(tasks.nodeScope(preview.id())).isNull();assertThat(tasks.get(preview.id()).state()).isEqualTo(CacheTasks.State.QUEUED);
            var oldPreview=preview();taskRows.update(null,new LambdaUpdateWrapper<CacheTaskEntity>().eq(CacheTaskEntity::getPublicId,oldPreview.id()).set(CacheTaskEntity::getNodeScopeJson,null));
            assertThatThrownBy(()->tasks.submit(oldPreview.id(),"node-scope-legacy-0004")).isInstanceOf(CacheTaskRejected.class);return null;
        });
    }
    private CacheTasks.View preview(){return tasks.preview(CacheTasks.Action.CLEAR,new CacheTasks.Selection(Set.of(REGION),null,null,null),"node scope test");}
    private CacheNodeIdentity node(String cluster,String id,List<String> expected){return new CacheNodeIdentity(new CacheNodeProperties(cluster,id,expected,false));}
    private void expire(CacheNodeIdentity node,String alias){host(()->{var row=leases.selectOne(new LambdaQueryWrapper<CacheNodeLeaseEntity>()
            .eq(CacheNodeLeaseEntity::getClusterHash,node.clusterHash()).eq(CacheNodeLeaseEntity::getNodeId,alias));
        if(row!=null){row.setLeaseUntil(Instant.EPOCH);leases.updateById(row);}return null;});}
    private SmaPrincipal manager(long tenant){return new SmaPrincipal(101,"manager",tenant,"admin",null,false,DataAccess.none(),Set.of("monitor:cache:view","monitor:cache:clear"),"UTC");}
    private <T> T host(Supplier<T> work){var previous=SecurityContextHolder.getContext();SecurityContextHolder.clearContext();try{return work.get();}finally{SecurityContextHolder.setContext(previous);}}
}
