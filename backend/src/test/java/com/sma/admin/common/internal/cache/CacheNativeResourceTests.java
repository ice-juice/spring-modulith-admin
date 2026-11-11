package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.persistence.*;
import com.sma.admin.common.internal.service.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.*;
import com.sma.admin.monitor.internal.service.MonitorPermissionCatalog;
import com.sma.admin.rule.internal.service.*;
import com.sma.admin.support.*;

@IntegrationTest
@TestPropertySource(properties={"sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","sma.cache.tasks.resources.enabled=true"})
class CacheNativeResourceTests {
    private static final String REGION="rule.runtime";
    @Autowired CacheTasks tasks;
    @Autowired CacheTaskExecutor executor;
    @Autowired CacheTaskMapper rows;
    @Autowired CacheTaskItemMapper items;
    @Autowired CacheRevisionStore revisions;
    @Autowired CacheTaskPlanner planner;
    @Autowired ManagedCache cache;
    @Autowired CacheGovernance controls;
    @Autowired ObjectProvider<CacheTaskActorResolver> actors;
    @Autowired TenantEntryQuery tenants;
    @Autowired CacheTaskDispatchProperties dispatch;
    @Autowired CacheConfirmationStore confirmations;
    @Autowired CacheConfirmations reports;
    @Autowired CacheWarmupRegistry warmups;
    @Autowired CacheResourceRegistry resources;
    @Autowired CacheNodeIdentity identity;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ExpressionEngine expressions;
    @MockitoSpyBean RuleCacheAdapter adapter;
    @Autowired UserService users;
    @Autowired RoleService roles;
    @Autowired RolePermissionService grants;
    @Autowired PermissionService permissions;
    @Autowired MonitorPermissionCatalog monitorCatalog;
    @Autowired RulePermissionCatalog ruleCatalog;
    private record Fixture(SmaPrincipal actor,long role) {}

    @Test void onlyTheFrozenProcessRunsTheAdapterOutsideTaskTransactionsAndRecordsNativeEvidence(){
        var f=fixture(98201);var id=submit(f,CacheTasks.Action.CLEAR,Set.of(REGION));
        assertThat(claim(f,UUID.randomUUID().toString())).isEmpty();
        doAnswer(call->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return call.callRealMethod();}).when(adapter).execute(any());
        Users.as(f.actor(),()->{expressions.validate("1 + 2");assertThat(tasks.get(id).targets().getFirst().resource().processId()).isEqualTo(identity.processId());return null;});
        run(f,claim(f,identity.processId()).orElseThrow());
        Users.as(f.actor(),()->{var view=tasks.get(id);assertThat(view.state()).isEqualTo(CacheTasks.State.SUCCEEDED);
            var result=view.items().getFirst();assertThat(result.resource().affectedEntries()).isEqualTo(1);assertThat(result.resource().remainingEntries()).isZero();
            assertThat(result.resource().startedAt()).isNotNull();assertThat(result.resource().completedAt()).isNotNull();assertThat(result.dataRevision()).isNull();
            assertThat(reports.task(id).coverage()).isEqualTo("NOT_APPLICABLE_RESOURCE");assertThat(resources.inspect(REGION).entries()).isZero();return null;});
        verify(adapter,times(1)).execute(CacheDescriptor.Operation.CLEAR);
    }
    @Test void earlierForeignTasksDoNotStarveLaterDataWorkAndRouteExpiryIsExplicit(){
        var f=fixture(98202);var ids=new ArrayList<String>();
        for(int i=0;i<17;i++)ids.add(submit(f,CacheTasks.Action.CLEAR,Set.of(REGION)));
        var data=submit(f,CacheTasks.Action.CLEAR,Set.of("config.parameters"));
        var foreign=UUID.randomUUID().toString();var lease=claim(f,foreign).orElseThrow();assertThat(lease.taskId()).isEqualTo(data);run(f,lease);
        Users.as(f.actor(),()->{var item=item(ids.getFirst());item.setNativeDeadline(Instant.EPOCH);items.updateById(item);return null;});
        assertThat(claim(f,foreign)).isEmpty();
        Users.as(f.actor(),()->{assertThat(tasks.get(ids.getFirst()).items().getFirst().failureCode()).isEqualTo("TARGET_NODE_UNAVAILABLE");
            assertThat(tasks.get(ids.get(1)).state()).isEqualTo(CacheTasks.State.QUEUED);return null;});
        verify(adapter,never()).execute(any());
    }
    @Test void uncertainAdapterFailureIsNeverAutomaticallyRetriedOrCancelledIntoAFalseNoEffect(){
        var f=fixture(98203);String id=submit(f,CacheTasks.Action.CLEAR,Set.of(REGION));
        doAnswer(call->{call.callRealMethod();throw new IllegalStateException("Injected lost adapter result");}).when(adapter).execute(any());
        run(f,claim(f,identity.processId()).orElseThrow());assertThat(claim(f,identity.processId())).isEmpty();
        Users.as(f.actor(),()->{assertThat(tasks.get(id).state()).isEqualTo(CacheTasks.State.UNKNOWN);assertThat(tasks.get(id).items().getFirst().failureCode()).isEqualTo("NATIVE_RESULT_UNKNOWN");
            assertThat(tasks.cancel(id).state()).isEqualTo(CacheTasks.State.UNKNOWN);return null;});
        verify(adapter,times(1)).execute(any());
    }
    @Test void lostArmCommitAcknowledgementNeverCallsTheAdapterAndRecoversAsUnknown(){
        var f=fixture(98204);String id=submit(f,CacheTasks.Action.CLEAR,Set.of(REGION));var lease=claim(f,identity.processId()).orElseThrow();
        var worker=worker(uncertainCommit(1));
        assertThatThrownBy(()->job(f,"cache-task:"+id,()->worker.executeOne(lease))).isInstanceOf(IllegalStateException.class);
        expire(f,id);assertThat(claim(f,UUID.randomUUID().toString())).isEmpty();
        Users.as(f.actor(),()->{assertThat(tasks.get(id).state()).isEqualTo(CacheTasks.State.UNKNOWN);return null;});
        verify(adapter,never()).execute(any());
    }
    @Test void lostResultCommitAcknowledgementReadsPersistedAppliedWithoutExecutingAgain(){
        var f=fixture(98205);String id=submit(f,CacheTasks.Action.CLEAR,Set.of(REGION));var lease=claim(f,identity.processId()).orElseThrow();
        var worker=worker(uncertainCommit(2));
        assertThatThrownBy(()->job(f,"cache-task:"+id,()->worker.executeOne(lease))).isInstanceOf(IllegalStateException.class);
        assertThat(claim(f,identity.processId())).isEmpty();
        Users.as(f.actor(),()->{assertThat(tasks.get(id).state()).isEqualTo(CacheTasks.State.SUCCEEDED);return null;});verify(adapter,times(1)).execute(any());
    }
    @Test void leaseLostDuringNativeEffectCannotBeOverwrittenOrRepeatedByAnotherWorker(){
        var f=fixture(98206);String id=submit(f,CacheTasks.Action.CLEAR,Set.of(REGION));
        doAnswer(call->{var result=call.callRealMethod();expire(f,id);assertThat(claim(f,UUID.randomUUID().toString())).isEmpty();return result;}).when(adapter).execute(any());
        assertThat(run(f,claim(f,identity.processId()).orElseThrow())).isFalse();
        Users.as(f.actor(),()->{assertThat(tasks.get(id).state()).isEqualTo(CacheTasks.State.UNKNOWN);assertThat(tasks.get(id).items().getFirst().resource().completedAt()).isNull();return null;});
        verify(adapter,times(1)).execute(any());
    }
    @Test void cancellationDuringEffectPreservesTheActualResultAndBeforeStartPreventsExecution(){
        var f=fixture(98207);String id=submit(f,CacheTasks.Action.CLEAR,Set.of(REGION));
        doAnswer(call->{tasks.cancel(id);return call.callRealMethod();}).when(adapter).execute(any());
        run(f,claim(f,identity.processId()).orElseThrow());
        Users.as(f.actor(),()->{assertThat(tasks.get(id).state()).isEqualTo(CacheTasks.State.SUCCEEDED);assertThat(tasks.get(id).cancelRequested()).isTrue();return null;});
        reset(adapter);var before=fixture(98208);String cancelled=submit(before,CacheTasks.Action.CLEAR,Set.of(REGION));var lease=claim(before,identity.processId()).orElseThrow();
        Users.as(before.actor(),()->{tasks.cancel(cancelled);return null;});run(before,lease);
        Users.as(before.actor(),()->{assertThat(tasks.get(cancelled).state()).isEqualTo(CacheTasks.State.CANCELLED);return null;});verify(adapter,never()).execute(any());
    }
    @Test void freshPermissionsAndScopeAreRequiredAndMixedConfirmationDoesNotClaimAllTargets(){
        var f=fixture(98209);String id=submit(f,CacheTasks.Action.CLEAR,Set.of(REGION));
        Users.as(admin(f.actor().tenantId()),()->{grants.replace(f.role(),Set.of());return null;});run(f,claim(f,identity.processId()).orElseThrow());
        Users.as(f.actor(),()->{assertThat(tasks.get(id).items().getFirst().state()).isEqualTo("DENIED");return null;});
        var mixed=fixture(98210);String both=submit(mixed,CacheTasks.Action.CLEAR,Set.of(REGION,"config.parameters"));
        run(mixed,claim(mixed,identity.processId()).orElseThrow());run(mixed,claim(mixed,identity.processId()).orElseThrow());
        Users.as(mixed.actor(),()->{assertThat(tasks.get(both).state()).isEqualTo(CacheTasks.State.SUCCEEDED);var report=reports.task(both);
            assertThat(report.coverage()).isEqualTo("DATA_TARGETS_ONLY");assertThat(report.allTargetsConfirmed()).isFalse();assertThat(report.nodes()).noneMatch(node->node.region().equals(REGION));return null;});
    }
    @Test void sqlStatisticsResetUsesItsOwnPermissionAndRecordsTheCurrentProcess(){
        var f=fixture(98211);var id=submit(f,CacheTasks.Action.RESET_STATS,Set.of("common.sql-statistics"));run(f,claim(f,identity.processId()).orElseThrow());
        Users.as(f.actor(),()->{var view=tasks.get(id);assertThat(view.state()).isEqualTo(CacheTasks.State.SUCCEEDED);assertThat(view.items().getFirst().resource().processId()).isEqualTo(identity.processId());return null;});
    }
    private CacheTaskExecutor worker(PlatformTransactionManager manager){return new CacheTaskExecutor(rows,items,revisions,planner,cache,controls,actors,tenants,dispatch,manager,confirmations,warmups,identity,resources);}
    private PlatformTransactionManager uncertainCommit(int number){var count=new AtomicInteger();return new PlatformTransactionManager(){
        public TransactionStatus getTransaction(TransactionDefinition definition){return transactions.getTransaction(definition);}
        public void commit(TransactionStatus status){transactions.commit(status);if(count.incrementAndGet()==number)throw new IllegalStateException("Injected lost commit acknowledgement");}
        public void rollback(TransactionStatus status){transactions.rollback(status);}
    };}
    private Fixture fixture(long tenant){return Users.as(admin(tenant),()->{
        permissions.sync(monitorCatalog.permissions());permissions.sync(ruleCatalog.permissions());
        var role=roles.save(null,new RoleService.Input(0L,"Native cache operator","native-cache-operator",0,true,DataScope.ALL,Set.of(),null));
        var keys=Set.of("monitor:cache:clear","monitor:cache:reset-stats","monitor:sql:view","monitor:sql:reset","rule:maintenance:view","rule:maintenance:clean");
        grants.replace(role.id(),permissions.list().stream().filter(p->keys.contains(p.permissionKey())).map(PermissionService.View::id).collect(java.util.stream.Collectors.toSet()));
        var input=new UserService.Input("worker","Native cache operator",null,null,null,"UNSPECIFIED",true,Set.of(role.id()),Set.of(),"zh-CN","UTC");
        var user=users.create(new UserService.Create(input,"Q722ee214d"));
        return new Fixture(new SmaPrincipal(user.id(),user.username(),tenant,"admin",null,false,DataAccess.unrestricted(),grants.keys(Set.of(role.id()),false),"UTC"),role.id());
    });}
    private SmaPrincipal admin(long tenant){return new SmaPrincipal(101,"admin",tenant,"admin",null,true,DataAccess.unrestricted(),Set.of(),"UTC");}
    private String submit(Fixture f,CacheTasks.Action action,Set<String> regions){return Users.as(f.actor(),()->{var preview=tasks.preview(action,new CacheTasks.Selection(regions,null,null,null),"native resource test");return tasks.submit(preview.id(),UUID.randomUUID().toString()).id();});}
    private Optional<CacheTaskExecutor.Lease> claim(Fixture f,String owner){return job(f,"cache-dispatch",()->executor.claim(owner));}
    private boolean run(Fixture f,CacheTaskExecutor.Lease lease){return job(f,"cache-task:"+lease.taskId(),()->executor.executeOne(lease));}
    private CacheTaskEntity row(String id){return rows.selectOne(new LambdaQueryWrapper<CacheTaskEntity>().eq(CacheTaskEntity::getPublicId,id));}
    private CacheTaskItemEntity item(String id){return items.selectOne(new LambdaQueryWrapper<CacheTaskItemEntity>().eq(CacheTaskItemEntity::getTaskId,row(id).getId()));}
    private void expire(Fixture f,String id){Users.as(f.actor(),()->{var task=row(id);task.setLeaseUntil(Instant.EPOCH);rows.updateById(task);return null;});}
    private <T> T job(Fixture f,String work,Supplier<T> action){var previous=SecurityContextHolder.getContext();SecurityContextHolder.clearContext();try{return ActorContext.withinJob(f.actor().tenantId(),work,action);}finally{SecurityContextHolder.setContext(previous);}}
}
