package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.persistence.*;
import com.sma.admin.common.internal.service.*;
import com.sma.admin.common.security.*;
import com.sma.admin.config.internal.service.*;
import com.sma.admin.iam.internal.service.*;
import com.sma.admin.monitor.internal.service.MonitorPermissionCatalog;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
@TestPropertySource(properties={"sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","sma.cache.tasks.warmup.enabled=true"})
class CacheWarmupExecutionTests {
    static final String REGION="config.dictionary-options";
    @Autowired CacheTasks tasks;
    @Autowired CacheTaskExecutor executor;
    @Autowired CacheTaskMapper rows;
    @Autowired CacheTaskItemMapper items;
    @Autowired ManagedCache cache;
    @Autowired CacheGovernance controls;
    @Autowired CacheConfirmations confirmations;
    @Autowired CacheNodeIdentity identity;
    @Autowired DictionaryService dictionaries;
    @MockitoSpyBean DictionaryWarmupLoader loader;
    @Autowired UserService users;
    @Autowired RoleService roles;
    @Autowired RolePermissionService grants;
    @Autowired PermissionService permissions;
    @Autowired MonitorPermissionCatalog monitorCatalog;
    @Autowired ConfigPermissionCatalog configCatalog;
    @Autowired MockMvcTester mvc;
    record Fixture(SmaPrincipal actor,long roleId) {}
    long tenantBase(){return 97800;}

    @Test void fourPagesReallyFillBothLocalesAndDoNotExhaustTheCrashRetryBudget(){
        var fixture=fixture(1,26);var actor=fixture.actor();String id=submit(actor);var first=claim(actor);
        assertThat(run(actor,first)).isTrue();
        Users.as(actor,()->{
            var view=tasks.get(id);assertThat(view.state()).isEqualTo(CacheTasks.State.RUNNING);
            assertThat(view.items().getFirst().warmup().checkpointedReads()).isEqualTo(16);
            assertThat(row(id).getLeaseUntil()).isAfter(rows.now());assertThat(item(id).getAttempts()).isZero();return null;
        });
        for(int i=0;i<3;i++){expire(actor,id);assertThat(run(actor,claim(actor))).isTrue();}
        Users.as(actor,()->{
            var view=tasks.get(id);assertThat(view.state()).isEqualTo(CacheTasks.State.SUCCEEDED);var progress=view.items().getFirst().warmup();
            assertThat(progress.checkpointedReads()).isEqualTo(52);assertThat(progress.processId()).isEqualTo(identity.processId());
            assertThat(cache.snapshot(REGION).size()).isEqualTo(52);
            assertThat(cache.get(REGION,"6:type_0:zh-CN",DictionaryService.OptionSnapshot.class,()->{throw new AssertionError("not warmed");})).isNotNull();
            assertThat(cache.get(REGION,"6:type_0:en-US",DictionaryService.OptionSnapshot.class,()->{throw new AssertionError("not warmed");})).isNotNull();
            assertThat(confirmations.task(id).coverage()).isEqualTo("NOT_APPLICABLE_PREWARM");return null;
        });
        job(actor,"cache-task:"+id,()->{assertThat(executor.executeOne(first)).isFalse();return null;});
    }
    @Test void failedReadReplaysTheUncheckpointedPageWithoutCountingItTwice(){
        var actor=fixture(2,1).actor();String id=submit(actor);var failOnce=new AtomicBoolean(true);
        doAnswer(call->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();call.callRealMethod();if(failOnce.getAndSet(false))throw new IllegalStateException("lost read response");return null;}).when(loader).load(anyString());
        var lease=claim(actor);assertThatThrownBy(()->run(actor,lease)).isInstanceOf(IllegalStateException.class);
        Users.as(actor,()->{assertThat(tasks.get(id).items().getFirst().warmup().checkpointedReads()).isZero();return null;});
        expire(actor,id);var replacement=claim(actor);assertThat(run(actor,lease)).isFalse();assertThat(run(actor,replacement)).isTrue();
        Users.as(actor,()->{assertThat(tasks.get(id).state()).isEqualTo(CacheTasks.State.SUCCEEDED);assertThat(tasks.get(id).items().getFirst().warmup().checkpointedReads()).isEqualTo(2);return null;});
    }
    @Test void cancellationDuringLoadingStopsBeforeTheNextKey(){
        var actor=fixture(3,2).actor();String id=submit(actor);var cancelOnce=new AtomicBoolean(true);
        doAnswer(call->{call.callRealMethod();if(cancelOnce.getAndSet(false))tasks.cancel(id);return null;}).when(loader).load(anyString());
        run(actor,claim(actor));
        Users.as(actor,()->{assertThat(tasks.get(id).state()).isEqualTo(CacheTasks.State.CANCELLED);assertThat(tasks.get(id).items().getFirst().warmup().checkpointedReads()).isZero();return null;});
        verify(loader,times(1)).load(anyString());
    }
    @Test void revokedBusinessReadPermissionIsRecheckedBetweenKeys(){
        var fixture=fixture(4,2);var actor=fixture.actor();String id=submit(actor);var revokeOnce=new AtomicBoolean(true);
        doAnswer(call->{call.callRealMethod();if(revokeOnce.getAndSet(false))Users.as(admin(actor.tenantId()),()->{grant(fixture.roleId(),false);return null;});return null;}).when(loader).load(anyString());
        run(actor,claim(actor));
        Users.as(actor,()->{assertThat(tasks.get(id).items().getFirst().state()).isEqualTo("DENIED");return null;});
        verify(loader,times(1)).load(anyString());
    }
    @Test void dataInvalidationOrPolicyChangeDuringReadsCannotProduceASuccessfulCheckpoint(){
        for(boolean policy:List.of(false,true)){
            reset(loader);var actor=fixture(policy?6:5,2).actor();String id=submit(actor);var changeOnce=new AtomicBoolean(true);
            doAnswer(call->{call.callRealMethod();if(changeOnce.getAndSet(false)){
                if(policy)controls.change(REGION,new CacheGovernance.Command(CacheGovernance.Action.PAUSE,0,null,"changed during warming"));else cache.invalidate(REGION);
            }return null;}).when(loader).load(anyString());
            run(actor,claim(actor));
            Users.as(actor,()->{assertThat(tasks.get(id).items().getFirst().state()).isEqualTo("CHANGED");assertThat(tasks.get(id).items().getFirst().warmup().checkpointedReads()).isZero();return null;});
            verify(loader,times(1)).load(anyString());
        }
    }
    @Test void deadlineItemCapAndChangedProcessProduceExplicitFailures(){
        for(String mode:List.of("time","count","process")){
            reset(loader);var actor=fixture(mode.equals("time")?7:mode.equals("count")?8:9,10).actor();String id=submit(actor);run(actor,claim(actor));
            Users.as(actor,()->{
                var task=row(id);var entry=item(id);
                if(mode.equals("time"))task.setWarmStartedAt(rows.now().minusSeconds(121));
                if(mode.equals("count"))entry.setWarmReads(999L);
                if(mode.equals("process"))entry.setWarmProcess(UUID.randomUUID().toString());
                task.setLeaseUntil(Instant.EPOCH);rows.updateById(task);items.updateById(entry);return null;
            });
            run(actor,claim(actor));
            Users.as(actor,()->{var entry=tasks.get(id).items().getFirst();assertThat(entry.state()).isEqualTo("FAILED");
                assertThat(entry.failureCode()).isEqualTo(mode.equals("time")?"TIME_LIMIT":mode.equals("count")?"ITEM_LIMIT":"EXECUTION_PROCESS_CHANGED");
                assertThat(entry.warmup().checkpointedReads()).isLessThanOrEqualTo(1000);return null;});
        }
    }
    @Test void httpPreviewFreezesTheLoaderBudgetAndCancellationStillWorksAfterPause(){
        var actor=fixture(10,1).actor();
        Users.as(actor,()->{assertThat(mvc.post().uri("/api/monitor/caches/tasks/preview").with(Users.request(actor)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"PREWARM\",\"regions\":[\"config.dictionary-options\",\"config.preferences\"],\"reason\":\"warm dictionary\"}"))
                .hasStatusOk().bodyJson().hasPathSatisfying("$.data.targets[0].warmup.readPermission",v->v.assertThat().isEqualTo("config:dictionary:view"))
                .hasPathSatisfying("$.data.targets[0].warmup.limits.maxItems",v->v.assertThat().isEqualTo(1000))
                .hasPathSatisfying("$.data.skipped[0].reason",v->v.assertThat().isEqualTo("UNSUPPORTED"));return null;});
        Users.as(actor,()->{
            var preview=tasks.recent(1).getFirst();var id=tasks.submit(preview.id(),UUID.randomUUID().toString()).id();
            controls.change(REGION,new CacheGovernance.Command(CacheGovernance.Action.PAUSE,0,null,"pause"));
            assertThat(tasks.cancel(id).state()).isEqualTo(CacheTasks.State.CANCELLED);return null;
        });
    }
    Fixture fixture(int offset,int count){return Users.as(admin(tenantBase()+offset),()->{
        permissions.sync(monitorCatalog.permissions());permissions.sync(configCatalog.permissions());
        var role=roles.save(null,new RoleService.Input(0L,"Warmup operator","warmup-operator",0,true,DataScope.SELF,Set.of(),null));grant(role.id(),true);
        var input=new UserService.Input("worker","Warmup operator",null,null,null,"UNSPECIFIED",true,Set.of(role.id()),Set.of(),"zh-CN","UTC");
        var user=users.create(new UserService.Create(input,"Q722ee214d"));
        for(int i=0;i<count;i++)dictionaries.saveType(null,new DictionaryService.TypeInput("type_"+i,"Type "+i,null,false,true,i,Map.of()));
        return new Fixture(new SmaPrincipal(user.id(),user.username(),tenantBase()+offset,"admin",null,false,DataAccess.own(),grants.keys(Set.of(role.id()),false),"UTC"),role.id());
    });}
    void grant(long role,boolean read){var keys=new HashSet<>(Set.of("monitor:cache:prewarm","monitor:cache:clear","monitor:cache:pause"));if(read)keys.add("config:dictionary:view");
        grants.replace(role,permissions.list().stream().filter(p->keys.contains(p.permissionKey())).map(PermissionService.View::id).collect(java.util.stream.Collectors.toSet()));}
    SmaPrincipal admin(long tenant){return new SmaPrincipal(101,"admin",tenant,"admin",null,true,DataAccess.unrestricted(),Set.of(),"UTC");}
    String submit(SmaPrincipal actor){return Users.as(actor,()->{var preview=tasks.preview(CacheTasks.Action.PREWARM,new CacheTasks.Selection(Set.of(REGION),null,null,null),"prewarm test");return tasks.submit(preview.id(),UUID.randomUUID().toString()).id();});}
    CacheTaskExecutor.Lease claim(SmaPrincipal actor){return job(actor,"cache-dispatch",()->executor.claim(identity.processId()).orElseThrow());}
    boolean run(SmaPrincipal actor,CacheTaskExecutor.Lease lease){return job(actor,"cache-task:"+lease.taskId(),()->executor.executeOne(lease));}
    CacheTaskEntity row(String id){return rows.selectOne(new LambdaQueryWrapper<CacheTaskEntity>().eq(CacheTaskEntity::getPublicId,id));}
    CacheTaskItemEntity item(String id){return items.selectOne(new LambdaQueryWrapper<CacheTaskItemEntity>().eq(CacheTaskItemEntity::getTaskId,row(id).getId()));}
    void expire(SmaPrincipal actor,String id){Users.as(actor,()->{var row=row(id);row.setLeaseUntil(Instant.EPOCH);rows.updateById(row);return null;});}
    <T> T job(SmaPrincipal actor,String work,Supplier<T> action){var previous=SecurityContextHolder.getContext();SecurityContextHolder.clearContext();try{return ActorContext.withinJob(actor.tenantId(),work,action);}finally{SecurityContextHolder.setContext(previous);}}
}
