package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import com.sma.admin.common.BizException;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.persistence.*;
import com.sma.admin.common.internal.service.CacheTaskStore;
import com.sma.admin.common.request.*;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;

@IntegrationTest
@TestPropertySource(properties={"sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","sma.cache.tasks.max-records-per-tenant=4"})
class CacheTaskSubmissionTests {
    private static final String REGION="config.dictionary-options";
    @Autowired CacheTasks tasks;
    @Autowired CacheTaskMapper rows;
    @Autowired CacheTaskItemMapper items;
    @Autowired CacheGovernance controls;
    @Autowired CacheTaskStore store;
    @Autowired CacheTaskPlanner planner;
    @Autowired RequestExecution requests;
    @Autowired ManagedCache cache;

    @Test void acceptedPreviewIsDurableAndReplaysAfterExpiryOrControlChangesWithoutNewItems(){
        Users.as(manager(97101),()->{
            cache.get(REGION,"retained",String.class,()->"old");
            var preview=preview("same task");assertThat(preview.state()).isEqualTo(CacheTasks.State.PREVIEW);assertThat(preview.items()).isEmpty();
            var submitted=tasks.submit(preview.id(),"task-key-original-0001");
            assertThat(submitted.state()).isEqualTo(CacheTasks.State.QUEUED);assertThat(submitted.items()).hasSize(1);
            assertThat(submitted.items().getFirst().state()).isEqualTo("PENDING");
            expire(preview.id());controls.change(REGION,new CacheGovernance.Command(CacheGovernance.Action.RESET_STATS,0,null,"different control"));
            var replay=tasks.submit(preview.id(),"task-key-original-0001");assertThat(replay.id()).isEqualTo(preview.id());assertThat(replay.items()).hasSize(1);
            assertThat(requests.receiptByKey("task-key-original-0001").state()).isEqualTo(RequestReceipt.State.SUCCEEDED);
            assertThat(cache.get(REGION,"retained",String.class,()->"wrong")).isEqualTo("old");
            assertThat(tasks.recent(10)).hasSize(1);return null;
        });
    }
    @Test void changedPreviewCannotExecuteAndFailedRequestIsNeverAutomaticallyRetried(){
        Users.as(manager(97102),()->{
            var preview=preview("stale");controls.change(REGION,new CacheGovernance.Command(CacheGovernance.Action.PAUSE,0,null,"pause before submit"));
            assertThatThrownBy(()->tasks.submit(preview.id(),"task-key-stale-0002")).isInstanceOfSatisfying(CacheTaskRejected.class,e->assertThat(e.reason()).isEqualTo(CacheTaskRejected.Reason.CHANGED));
            assertThat(tasks.get(preview.id()).items()).isEmpty();assertThat(tasks.get(preview.id()).state()).isEqualTo(CacheTasks.State.PREVIEW);
            assertThatThrownBy(()->tasks.submit(preview.id(),"task-key-stale-0002")).isInstanceOfSatisfying(RequestStateException.class,e->assertThat(e.receipt().state()).isEqualTo(RequestReceipt.State.FAILED_FINAL));
            return null;
        });
    }
    @Test void tenantActorAndPortalOwnershipProtectEveryOperationAndReplayRechecksPermissions(){
        var actor=manager(97103);String id=Users.as(actor,()->preview("owned").id());
        for(var other:List.of(manager(97104),actor(97103,102,"admin",permissions()),actor(97103,101,"other",permissions())))Users.as(other,()->{
            assertThat(tasks.recent(10)).isEmpty();
            assertThatThrownBy(()->tasks.get(id)).isInstanceOf(BizException.class);
            assertThatThrownBy(()->tasks.submit(id,"task-key-cross-0003")).isInstanceOf(BizException.class);
            assertThatThrownBy(()->tasks.cancel(id)).isInstanceOf(BizException.class);return null;
        });
        Users.as(actor,()->{tasks.submit(id,"task-key-owned-0003");return null;});
        Users.as(actor(97103,101,"admin",Set.of("monitor:cache:view")),()->{
            assertThat(tasks.get(id).id()).isEqualTo(id);
            assertThatThrownBy(()->tasks.submit(id,"task-key-owned-0003")).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->tasks.cancel(id)).isInstanceOf(AccessDeniedException.class);return null;
        });
        Users.as(SmaPrincipal.forApplication(97103,"admin","machine"),()->{
            assertThatThrownBy(()->tasks.get(id)).isInstanceOf(AccessDeniedException.class);return null;
        });
    }
    @Test void expiredAndEmptyPreviewsDoNotBecomeTasksAndCapacityOnlyRetiresExpiredPreviews(){
        Users.as(manager(97105),()->{
            var empty=tasks.preview(CacheTasks.Action.CLEAR,new CacheTasks.Selection(Set.of("missing.region"),null,null,null),"unknown");
            assertThat(empty.skipped()).containsExactly(new CacheTasks.Skipped("missing.region",CacheTasks.SkipReason.UNKNOWN_REGION));
            assertThatThrownBy(()->tasks.submit(empty.id(),"task-key-empty-0005")).isInstanceOfSatisfying(CacheTaskRejected.class,e->assertThat(e.reason()).isEqualTo(CacheTaskRejected.Reason.EMPTY));
            var expired=preview("expired");expire(expired.id());
            assertThatThrownBy(()->tasks.submit(expired.id(),"task-key-expired-0005")).isInstanceOfSatisfying(CacheTaskRejected.class,e->assertThat(e.reason()).isEqualTo(CacheTaskRejected.Reason.EXPIRED));
            preview("retire expired");assertThatThrownBy(()->tasks.get(expired.id())).isInstanceOf(BizException.class);
            var accepted=preview("preserve accepted");tasks.submit(accepted.id(),"task-key-accepted-0005");expire(accepted.id());
            preview("fourth retained");
            assertThatThrownBy(()->preview("capacity")).isInstanceOfSatisfying(CacheTaskRejected.class,e->assertThat(e.reason()).isEqualTo(CacheTaskRejected.Reason.LIMIT));
            assertThat(tasks.get(accepted.id()).state()).isEqualTo(CacheTasks.State.QUEUED);return null;
        });
    }
    @Test void requestKeyCannotBeReusedForDifferentPreviewAndOnePreviewCannotBeSubmittedUnderTwoKeys(){
        Users.as(manager(97106),()->{
            var first=preview("first");var second=preview("second");tasks.submit(first.id(),"task-key-conflict-0006");
            assertThatThrownBy(()->tasks.submit(second.id(),"task-key-conflict-0006")).isInstanceOf(RequestRejected.class);
            assertThatThrownBy(()->tasks.submit(first.id(),"task-key-other-0006")).isInstanceOf(CacheTaskRejected.class);
            assertThat(tasks.get(first.id()).items()).hasSize(1);assertThat(tasks.get(second.id()).items()).isEmpty();return null;
        });
    }
    @Test void cancellationIsIdempotentAndDoesNotClearDataOrEraseReceipt(){
        Users.as(manager(97107),()->{
            cache.get(REGION,"retain",String.class,()->"value");var preview=preview("cancel");tasks.submit(preview.id(),"task-key-cancel-0007");
            assertThat(tasks.cancel(preview.id()).state()).isEqualTo(CacheTasks.State.CANCELLED);
            assertThat(tasks.cancel(preview.id()).items()).allSatisfy(item->assertThat(item.state()).isEqualTo("CANCELLED"));
            assertThat(tasks.submit(preview.id(),"task-key-cancel-0007").state()).isEqualTo(CacheTasks.State.CANCELLED);
            assertThat(requests.receiptByKey("task-key-cancel-0007").state()).isEqualTo(RequestReceipt.State.SUCCEEDED);
            assertThat(cache.get(REGION,"retain",String.class,()->"wrong")).isEqualTo("value");return null;
        });
    }
    @Test void enqueueAndItemCreationRollBackWithTheRequestTransaction(){
        Users.as(manager(97108),()->{
            var preview=preview("rollback");
            var command=new RequestCommand("cache.submit","cache.task",preview.id(),"task-key-rollback-0008",new byte[]{1},"monitor:cache:clear");
            assertThatThrownBy(()->requests.executeLocal(command,new RequestExecution.Work<Void>(){
                @Override public void authorize(){planner.authorize(preview.action(),preview.targets());}
                @Override public Void execute(){store.enqueue(preview.id(),1,()->planner.validateFrozen(preview.targets()));throw new IllegalStateException("rollback after enqueue");}
            })).isInstanceOf(IllegalStateException.class);
            assertThat(tasks.get(preview.id()).state()).isEqualTo(CacheTasks.State.PREVIEW);assertThat(tasks.get(preview.id()).items()).isEmpty();
            assertThat(requests.receiptByKey(command.key()).state()).isEqualTo(RequestReceipt.State.FAILED_FINAL);return null;
        });
    }
    @Test void simultaneousSubmissionsNeverDuplicateItems() throws Exception {
        var actor=manager(97109);String id=Users.as(actor,()->preview("concurrent").id());var start=new CountDownLatch(1);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var results=new ArrayList<Future<?>>();
            for(int i=0;i<2;i++)results.add(pool.submit(()->{start.await();return Users.as(actor,()->{
                try{return tasks.submit(id,"task-key-concurrent-0009");}
                catch(RequestStateException pending){assertThat(pending.receipt().state()).isIn(RequestReceipt.State.RUNNING,RequestReceipt.State.UNKNOWN);return null;}
                catch(RequestRejected contention){assertThat(contention.reason()).isEqualTo(RequestRejected.Reason.UNAVAILABLE);return null;}
            });}));start.countDown();for(var result:results)result.get(30,TimeUnit.SECONDS);
        }
        Users.as(actor,()->{assertThat(tasks.submit(id,"task-key-concurrent-0009").items()).hasSize(1);return null;});
    }
    private CacheTasks.View preview(String reason){return tasks.preview(CacheTasks.Action.CLEAR,new CacheTasks.Selection(Set.of(REGION),null,null,null),reason);}
    private void expire(String id){var row=rows.selectOne(new LambdaQueryWrapper<CacheTaskEntity>().eq(CacheTaskEntity::getPublicId,id));row.setExpiresAt(Instant.EPOCH);rows.updateById(row);}
    private Set<String> permissions(){return Set.of("monitor:cache:view","monitor:cache:clear","monitor:cache:pause","monitor:cache:reset-stats");}
    private SmaPrincipal manager(long tenant){return actor(tenant,101,"admin",permissions());}
    private SmaPrincipal actor(long tenant,long id,String portal,Set<String> permissions){return new SmaPrincipal(id,"manager",tenant,portal,null,false,DataAccess.none(),permissions,"UTC");}
}
