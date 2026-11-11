package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.*;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.persistence.*;
import com.sma.admin.common.internal.service.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.*;
import com.sma.admin.monitor.internal.service.MonitorPermissionCatalog;
import com.sma.admin.support.*;

@IntegrationTest
@TestPropertySource(properties="sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class CacheTaskExecutionTests {
    private static final String REGION="config.dictionary-options", SECOND="config.parameters";
    @Autowired CacheTasks tasks;
    @Autowired CacheTaskExecutor executor;
    @Autowired CacheTaskMapper rows;
    @Autowired CacheTaskItemMapper items;
    @Autowired CacheRevisionStore revisions;
    @Autowired CacheTaskPlanner planner;
    @Autowired CacheGovernance controls;
    @Autowired ManagedCache cache;
    @Autowired UserService users;
    @Autowired RoleService roles;
    @Autowired RolePermissionService grants;
    @Autowired PermissionService permissions;
    @Autowired MonitorPermissionCatalog catalog;
    @Autowired ObjectProvider<CacheTaskActorResolver> actors;
    @Autowired TenantEntryQuery tenants;
    @Autowired CacheTaskDispatchProperties properties;
    @Autowired PlatformTransactionManager transactions;
    @Autowired CacheConfirmationStore confirmations;
    @Autowired CacheConfirmations confirmationQueries;
    private record Fixture(SmaPrincipal actor,long roleId) {}

    @Test void realIamRevocationAndAccountDisableAreCheckedInsteadOfSubmittedGrants(){
        var fixture=fixture(97201);var id=submit(fixture,CacheTasks.Action.CLEAR,Set.of(REGION));
        Users.as(admin(97201),()->{grants.replace(fixture.roleId(),Set.of());return null;});
        execute(fixture);
        Users.as(fixture.actor(),()->{
            assertThat(tasks.get(id).state()).isEqualTo(CacheTasks.State.FAILED);assertThat(tasks.get(id).items().getFirst().state()).isEqualTo("DENIED");
            assertThat(controls.state(REGION).dataRevision()).isZero();return null;
        });
        var disabled=fixture(97202);var disabledId=submit(disabled,CacheTasks.Action.CLEAR,Set.of(REGION));
        Users.as(admin(97202),()->{users.update(disabled.actor().userId(),input("worker",disabled.roleId(),false));return null;});
        execute(disabled);
        Users.as(disabled.actor(),()->{assertThat(tasks.get(disabledId).items().getFirst().failureCode()).isEqualTo("AUTHORIZATION_REVOKED");return null;});
    }
    @Test void competingWorkersAndOldFencesCannotApplyTwice() throws Exception {
        var fixture=fixture(97203);var id=submit(fixture,CacheTasks.Action.CLEAR,Set.of(REGION));var start=new CountDownLatch(1);
        var claims=new ArrayList<CacheTaskExecutor.Lease>();
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var first=pool.submit(()->{start.await();return claim(fixture);});var second=pool.submit(()->{start.await();return claim(fixture);});start.countDown();
            first.get(20,TimeUnit.SECONDS).ifPresent(claims::add);second.get(20,TimeUnit.SECONDS).ifPresent(claims::add);
        }
        assertThat(claims).hasSize(1);expire(fixture,id);var replacement=claim(fixture).orElseThrow();
        assertThat(replacement.fence()).isGreaterThan(claims.getFirst().fence());assertThat(run(fixture,claims.getFirst())).isFalse();
        assertThat(run(fixture,replacement)).isTrue();assertThat(run(fixture,replacement)).isFalse();
        Users.as(fixture.actor(),()->{assertThat(tasks.get(id).state()).isEqualTo(CacheTasks.State.SUCCEEDED);assertThat(controls.state(REGION).dataRevision()).isEqualTo(1);return null;});
    }
    @Test void changedControlProducesAnAccuratePartialResultAndDoesNotUndoTheNewPolicy(){
        var fixture=fixture(97204);var id=submit(fixture,CacheTasks.Action.CLEAR,Set.of(REGION,SECOND));
        Users.as(fixture.actor(),()->{controls.change(SECOND,new CacheGovernance.Command(CacheGovernance.Action.PAUSE,0,null,"another editor"));return null;});
        execute(fixture);execute(fixture);
        Users.as(fixture.actor(),()->{
            var view=tasks.get(id);assertThat(view.state()).isEqualTo(CacheTasks.State.PARTIAL);assertThat(view.items()).extracting(CacheTasks.Item::state).containsExactly("APPLIED","CHANGED");
            assertThat(controls.state(SECOND).paused()).isTrue();assertThat(controls.state(SECOND).dataRevision()).isEqualTo(1);return null;
        });
    }
    @Test void cancellationAfterOneItemKeepsCommittedEffectsAndStopsRemainingItems(){
        var fixture=fixture(97205);var id=submit(fixture,CacheTasks.Action.CLEAR,Set.of(REGION,SECOND));execute(fixture);
        Users.as(fixture.actor(),()->{var requested=tasks.cancel(id);assertThat(requested.cancelRequested()).isTrue();assertThat(requested.state()).isEqualTo(CacheTasks.State.RUNNING);return null;});
        assertThat(claim(fixture)).isEmpty();
        Users.as(fixture.actor(),()->{
            var view=tasks.get(id);assertThat(view.state()).isEqualTo(CacheTasks.State.PARTIAL);assertThat(view.items()).extracting(CacheTasks.Item::state).containsExactly("APPLIED","CANCELLED");
            assertThat(controls.state(REGION).dataRevision()).isEqualTo(1);assertThat(controls.state(SECOND).dataRevision()).isZero();return null;
        });
    }
    @Test void threeCrashedClaimsAreBoundedAndTenantGenerationMismatchIsDenied(){
        var fixture=fixture(97206);var id=submit(fixture,CacheTasks.Action.CLEAR,Set.of(REGION));
        for(int i=0;i<3;i++){assertThat(claim(fixture)).isPresent();expire(fixture,id);}
        assertThat(claim(fixture)).isEmpty();
        Users.as(fixture.actor(),()->{assertThat(tasks.get(id).items().getFirst().failureCode()).isEqualTo("RETRY_EXHAUSTED");assertThat(controls.state(REGION).dataRevision()).isZero();return null;});
        var generation=fixture(97207);var stale=submit(generation,CacheTasks.Action.CLEAR,Set.of(REGION));
        Users.as(generation.actor(),()->{var row=row(stale);row.setTenantGeneration(2L);rows.updateById(row);return null;});execute(generation);
        Users.as(generation.actor(),()->{assertThat(tasks.get(stale).items().getFirst().state()).isEqualTo("DENIED");return null;});
    }
    @Test void pauseResumeAndStatisticsResetUseGovernanceAndAuditTheFreshActor(){
        var fixture=fixture(97208);
        for(var action:List.of(CacheTasks.Action.PAUSE,CacheTasks.Action.RESUME,CacheTasks.Action.RESET_STATS)){
            if(action==CacheTasks.Action.RESET_STATS)Users.as(fixture.actor(),()->{cache.get(REGION,"retained",String.class,()->"value");return null;});
            var id=submit(fixture,action,Set.of(REGION));execute(fixture);
            Users.as(fixture.actor(),()->{assertThat(tasks.get(id).state()).isEqualTo(CacheTasks.State.SUCCEEDED);return null;});
        }
        Users.as(fixture.actor(),()->{
            assertThat(cache.get(REGION,"retained",String.class,()->"wrong")).isEqualTo("value");assertThat(controls.state(REGION).dataRevision()).isEqualTo(2);
            assertThat(controls.history(REGION,10)).hasSize(3).allSatisfy(history->assertThat(history.actorId()).isEqualTo(fixture.actor().userId()));return null;
        });
    }
    @Test void lostCommitAcknowledgementReadsCommittedItemsInsteadOfReapplying(){
        var fixture=fixture(97209);var id=submit(fixture,CacheTasks.Action.CLEAR,Set.of(REGION,SECOND));var lease=claim(fixture).orElseThrow();
        var uncertain=new PlatformTransactionManager(){
            @Override public TransactionStatus getTransaction(TransactionDefinition definition){return transactions.getTransaction(definition);}
            @Override public void commit(TransactionStatus status){transactions.commit(status);throw new IllegalStateException("Injected lost commit acknowledgement");}
            @Override public void rollback(TransactionStatus status){transactions.rollback(status);}
        };
        var worker=new CacheTaskExecutor(rows,items,revisions,planner,cache,controls,actors,tenants,properties,uncertain,confirmations);
        assertThatThrownBy(()->job(fixture.actor().tenantId(),"cache-task:"+id,()->worker.executeOne(lease))).isInstanceOf(IllegalStateException.class);
        execute(fixture);
        Users.as(fixture.actor(),()->{assertThat(tasks.get(id).state()).isEqualTo(CacheTasks.State.SUCCEEDED);assertThat(controls.state(REGION).dataRevision()).isEqualTo(1);assertThat(controls.state(SECOND).dataRevision()).isEqualTo(1);
            var report=confirmationQueries.task(id);assertThat(report.allTargetsConfirmed()).isFalse();assertThat(report.nodes()).allSatisfy(node->assertThat(node.confirmation()).isEqualTo("UNKNOWN"));return null;});
    }
    private Fixture fixture(long tenant){return Users.as(admin(tenant),()->{
        permissions.sync(catalog.permissions());var role=roles.save(null,new RoleService.Input(0L,"Cache operator","cache-operator",0,true,DataScope.SELF,Set.of(),null));
        grants.replace(role.id(),permissions.list().stream().filter(row->Set.of("monitor:cache:clear","monitor:cache:pause","monitor:cache:reset-stats").contains(row.permissionKey())).map(PermissionService.View::id).collect(java.util.stream.Collectors.toSet()));
        var user=users.create(new UserService.Create(input("worker",role.id(),true),"Q722ee214d"));
        return new Fixture(new SmaPrincipal(user.id(),user.username(),tenant,"admin",null,false,DataAccess.own(),grants.keys(Set.of(role.id()),false),"UTC"),role.id());
    });}
    private UserService.Input input(String name,long role,boolean enabled){return new UserService.Input(name,"Cache operator",null,null,null,"UNSPECIFIED",enabled,Set.of(role),Set.of(),"zh-CN","UTC");}
    private SmaPrincipal admin(long tenant){return new SmaPrincipal(101,"admin",tenant,"admin",null,true,DataAccess.unrestricted(),Set.of(),"UTC");}
    private String submit(Fixture fixture,CacheTasks.Action action,Set<String> regions){return Users.as(fixture.actor(),()->{var preview=tasks.preview(action,new CacheTasks.Selection(regions,null,null,null),"worker test");return tasks.submit(preview.id(),UUID.randomUUID().toString()).id();});}
    private Optional<CacheTaskExecutor.Lease> claim(Fixture fixture){return job(fixture.actor().tenantId(),"cache-dispatch",()->executor.claim(UUID.randomUUID().toString()));}
    private boolean run(Fixture fixture,CacheTaskExecutor.Lease lease){return job(fixture.actor().tenantId(),"cache-task:"+lease.taskId(),()->executor.executeOne(lease));}
    private void execute(Fixture fixture){assertThat(run(fixture,claim(fixture).orElseThrow())).isTrue();}
    private CacheTaskEntity row(String id){return rows.selectOne(new LambdaQueryWrapper<CacheTaskEntity>().eq(CacheTaskEntity::getPublicId,id));}
    private void expire(Fixture fixture,String id){Users.as(fixture.actor(),()->{var row=row(id);row.setLeaseUntil(Instant.EPOCH);rows.updateById(row);return null;});}
    private <T> T job(long tenant,String work,Supplier<T> action){var previous=SecurityContextHolder.getContext();SecurityContextHolder.clearContext();try{return ActorContext.withinJob(tenant,work,action);}finally{SecurityContextHolder.setContext(previous);}}
}
