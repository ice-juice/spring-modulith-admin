package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import com.sma.admin.common.BizException;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.persistence.*;
import com.sma.admin.common.internal.service.*;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.*;
import com.sma.admin.monitor.internal.service.MonitorPermissionCatalog;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
@TestPropertySource(properties={"sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "sma.cache.nodes.cluster=p08-node-confirmation-tests","sma.cache.nodes.id=node-a","sma.cache.nodes.expected=node-a,node-b"})
class CacheConfirmationTests {
    private static final String REGION="config.dictionary-options";
    @Autowired CacheTasks tasks;
    @Autowired CacheConfirmations reports;
    @Autowired CacheTaskExecutor executor;
    @Autowired CacheTaskMapper taskRows;
    @Autowired CacheConfirmationMapper evidence;
    @Autowired CacheNodeLeaseMapper leaseRows;
    @Autowired CacheNodeLeaseStore leases;
    @Autowired CacheNodeIdentity a;
    @Autowired ManagedCache cache;
    @Autowired CacheRegistry registry;
    @Autowired CacheRevisionStore revisions;
    @Autowired CacheGovernance controls;
    @Autowired CacheConfirmationProcessor processor;
    @MockitoSpyBean CacheConfirmationStore store;
    @Autowired UserService users;
    @Autowired RoleService roles;
    @Autowired RolePermissionService grants;
    @Autowired PermissionService permissions;
    @Autowired MonitorPermissionCatalog catalog;
    @Autowired MockMvcTester mvc;
    @Autowired TenantWorkQuery tenantWork;
    private CacheNodeIdentity b;
    private long aEpoch,bEpoch;
    enum Provider {LOCAL,NONE,REDIS}
    @BeforeEach void registerBothProcesses(){
        expireNode(a,"node-a");expireNode(a,"node-b");b=new CacheNodeIdentity(new CacheNodeProperties(a.properties().cluster(),"node-b",a.properties().expected(),false));
        renew();
    }
    private void renew(){aEpoch=host(()->leases.heartbeat(a)).epoch();bEpoch=host(()->leases.heartbeat(b)).epoch();}

    @ParameterizedTest @EnumSource(Provider.class)
    void independentLocalNoneAndRedisProvidersOnlyConfirmAfterApplyingCommittedState(Provider provider){
        var actor=actor(97600+provider.ordinal());
        try(var providers=new Providers(provider,actor.tenantId())){
            Users.as(actor,()->{providers.a.get(REGION,"key",String.class,()->"old");providers.b.get(REGION,"key",String.class,()->"old");return null;});
            String id=submit(actor,CacheTasks.Action.CLEAR);execute(actor);renew();
            Users.as(actor,()->{var report=reports.task(id);assertThat(report.logicalState()).isEqualTo("SUCCEEDED");assertThat(report.allTargetsConfirmed()).isFalse();
                assertThat(report.nodes()).extracting(CacheConfirmations.Node::confirmation).containsExactly("WAITING","WAITING");return null;});
            assertThat(process(actor,a,aEpoch,new CacheConfirmationProcessor(providers.a,store,a))).isEqualTo(1);
            Users.as(actor,()->{assertThat(reports.task(id).nodes()).extracting(CacheConfirmations.Node::confirmation).containsExactly("CONFIRMED","WAITING");return null;});
            assertThat(process(actor,b,bEpoch,new CacheConfirmationProcessor(providers.b,store,b))).isEqualTo(1);
            Users.as(actor,()->{var report=reports.task(id);assertThat(report.allTargetsConfirmed()).isTrue();assertThat(report.allExpectedProcessesOnline()).isTrue();
                assertThat(report.reclamation()).isEqualTo("NOT_MEASURED");assertThat(providers.a.get(REGION,"key",String.class,()->"new")).isEqualTo("new");
                assertThat(providers.b.get(REGION,"key",String.class,()->"new")).isEqualTo("new");
                assertThat(controls.state(REGION).dataRevision()).isEqualTo(1);return null;});
            assertThat(process(actor,a,aEpoch,new CacheConfirmationProcessor(providers.a,store,a))).isZero();
        }
    }
    @Test void evidencePublicationFailureRollsBackTheEffectAndTheEvidenceTogether(){
        var actor=actor(97610);String id=submit(actor,CacheTasks.Action.CLEAR);
        Users.as(actor,()->{cache.get(REGION,"retained",String.class,()->"old");return null;});
        CacheConfirmationStore target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(store);
        doAnswer(call->{call.callRealMethod();throw new IllegalStateException("Injected publication failure");}).when(target).publish(any(),any());
        var lease=job(actor.tenantId(),"cache-dispatch",()->executor.claim(a.processId()).orElseThrow());
        assertThatThrownBy(()->job(actor.tenantId(),"cache-task:"+id,()->executor.executeOne(lease))).isInstanceOf(IllegalStateException.class);
        reset(target);
        Users.as(actor,()->{assertThat(controls.state(REGION).dataRevision()).isZero();assertThat(evidence.selectCount(new LambdaQueryWrapper<>())).isZero();
            assertThat(cache.get(REGION,"retained",String.class,()->"wrong")).isEqualTo("old");
            var row=row(id);row.setLeaseUntil(Instant.EPOCH);taskRows.updateById(row);return null;});
        execute(actor);Users.as(actor,()->{assertThat(controls.state(REGION).dataRevision()).isEqualTo(1);assertThat(evidence.selectCount(new LambdaQueryWrapper<>())).isEqualTo(2);return null;});
    }
    @Test void bothVersionsAreRequiredAndDuplicateEvidenceCannotRewriteItsTimestamp(){
        var actor=actor(97611);String id=submit(actor,CacheTasks.Action.RESET_STATS);execute(actor);renew();
        long request=Users.as(actor,()->entry(id,"node-a").getId());
        assertThat(job(actor.tenantId(),"cache-node:"+a.processId(),()->store.confirm(request,a,aEpoch,new CacheRevisionStore.State(100,0,0,false,null)))).isFalse();
        assertThat(process(actor,a,aEpoch,processor)).isEqualTo(1);
        var before=Users.as(actor,()->reports.task(id).nodes().getFirst());
        assertThat(job(actor.tenantId(),"cache-node:"+a.processId(),()->store.confirm(request,a,aEpoch,new CacheRevisionStore.State(100,100,100,false,null)))).isFalse();
        Users.as(actor,()->{assertThat(reports.task(id).nodes().getFirst()).isEqualTo(before);return null;});
        Users.as(actor,()->{assertThatThrownBy(()->store.confirm(request,a,aEpoch,CacheRevisionStore.State.initial())).isInstanceOf(AccessDeniedException.class);return null;});
        assertThat(job(97612,"cache-node:"+a.processId(),()->store.confirm(request,a,aEpoch,new CacheRevisionStore.State(100,100,100,false,null)))).isFalse();
    }
    @Test void replacementCannotConfirmForAnOldIncarnationAndOfflineIsNotSuccess(){
        var actor=actor(97613);String id=submit(actor,CacheTasks.Action.CLEAR);execute(actor);renew();
        expireNode(a,"node-a");
        Users.as(actor,()->{assertThat(reports.task(id).nodes().getFirst().availability()).isEqualTo("OFFLINE");assertThat(reports.task(id).allTargetsConfirmed()).isFalse();return null;});
        var replacement=new CacheNodeIdentity(a.properties());host(()->leases.heartbeat(replacement));
        long request=Users.as(actor,()->entry(id,"node-a").getId());
        assertThat(job(actor.tenantId(),"cache-node:"+a.processId(),()->store.confirm(request,a,aEpoch,new CacheRevisionStore.State(1,0,0,false,null)))).isFalse();
        Users.as(actor,()->{var report=reports.task(id);assertThat(report.nodes().getFirst().availability()).isEqualTo("REPLACED");
            assertThat(report.nodes().getFirst().confirmation()).isEqualTo("WAITING");assertThat(report.allExpectedProcessesOnline()).isFalse();return null;});
    }
    @Test void newerStateIsExplicitAndHistoricalConfirmationSurvivesLaterOfflineStatus(){
        var actor=actor(97614);String id=submit(actor,CacheTasks.Action.CLEAR);execute(actor);
        Users.as(actor,()->{cache.invalidate(REGION);return null;});renew();
        process(actor,a,aEpoch,processor);process(actor,b,bEpoch,new CacheConfirmationProcessor(cache,store,b));
        expireNode(b,"node-b");
        Users.as(actor,()->{var report=reports.task(id);assertThat(report.allTargetsConfirmed()).isTrue();assertThat(report.allExpectedProcessesOnline()).isFalse();
            assertThat(report.nodes()).allSatisfy(node->assertThat(node.confirmation()).isEqualTo("CONFIRMED_NEWER"));
            assertThat(report.nodes().get(1).availability()).isEqualTo("OFFLINE");return null;});
    }
    @Test void failedApplicationRetriesWithBackoffAndLegacyCoverageRemainsUnknown(){
        var actor=actor(97615);String id=submit(actor,CacheTasks.Action.CLEAR);execute(actor);renew();
        var broken=mock(DurableManagedCache.class);when(broken.controlState(REGION)).thenThrow(new CacheUnavailableException());
        assertThat(process(actor,a,aEpoch,new CacheConfirmationProcessor(broken,store,a))).isZero();
        job(actor.tenantId(),"cache-node:"+a.processId(),()->{assertThat(store.due(a,aEpoch,16)).isEmpty();return null;});
        Users.as(actor,()->{var node=reports.task(id).nodes().getFirst();assertThat(node.confirmation()).isEqualTo("WAITING");assertThat(node.lastError()).isEqualTo("APPLY_FAILED");
            evidence.update(null,new LambdaUpdateWrapper<CacheConfirmationEntity>().eq(CacheConfirmationEntity::getId,entry(id,"node-a").getId()).set(CacheConfirmationEntity::getNextAttemptAt,Instant.EPOCH));return null;});
        assertThat(process(actor,a,aEpoch,processor)).isEqualTo(1);
        var legacy=actor(97616);String old=submit(legacy,CacheTasks.Action.CLEAR);
        Users.as(legacy,()->{taskRows.update(null,new LambdaUpdateWrapper<CacheTaskEntity>().eq(CacheTaskEntity::getPublicId,old).set(CacheTaskEntity::getNodeScopeJson,null));return null;});
        execute(legacy);
        Users.as(legacy,()->{var report=reports.task(old);assertThat(report.coverage()).isEqualTo("LEGACY_UNKNOWN");assertThat(report.nodes()).isEmpty();assertThat(report.allTargetsConfirmed()).isFalse();return null;});
    }
    @Test void httpIsOwnerScopedAndNeverReturnsValuesOrConnectionParameters(){
        var actor=actor(97617);String id=submit(actor,CacheTasks.Action.CLEAR);execute(actor);
        Users.as(actor,()->{assertThat(mvc.get().uri("/api/monitor/caches/tasks/"+id+"/confirmation").with(Users.request(actor))).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.data.logicalState",v->v.assertThat().isEqualTo("SUCCEEDED"))
                .hasPathSatisfying("$.data.allTargetsConfirmed",v->v.assertThat().isEqualTo(false))
                .doesNotHavePath("$.data.nodes[0].value").doesNotHavePath("$.data.password");return null;});
        var stranger=new SmaPrincipal(101,"stranger",97618,"admin",null,false,DataAccess.none(),Set.of("monitor:cache:view"),"UTC");
        Users.as(stranger,()->{assertThatThrownBy(()->reports.task(id)).isInstanceOf(BizException.class);return null;});
        Users.as(actor,()->{assertThat(mvc.get().uri("/api/monitor/caches/nodes").with(Users.request(actor))).hasStatusOk().bodyJson()
                .hasPath("$.data.localProcessId").doesNotHavePath("$.data.host").doesNotHavePath("$.data.namespace");return null;});
    }
    @Test void backgroundRuntimeHeartbeatsDiscoversTheTenantAndPersistsRealConfirmation(){
        var actor=actor(97619);expireNode(a,"node-a");
        var enabled=new CacheNodeIdentity(new CacheNodeProperties(a.properties().cluster(),"node-a",a.properties().expected(),true,a.properties().version()));
        try(var providers=new Providers(Provider.LOCAL,actor.tenantId())){
            var background=new CacheNodeRuntime(enabled,leases,new CacheConfirmationProcessor(providers.a,store,enabled),tenantWork);
            try{
                host(()->{background.heartbeatTick();return null;});
                await().atMost(Duration.ofSeconds(8)).until(()->background.status().state().equals("OWNED"));
                Users.as(actor,()->{providers.a.get(REGION,"background",String.class,()->"old");return null;});
                String id=submit(actor,CacheTasks.Action.CLEAR);execute(actor);
                await().atMost(Duration.ofSeconds(12)).pollInterval(Duration.ofMillis(100)).untilAsserted(()->{
                    host(()->{background.confirmationTick();return null;});
                    Users.as(actor,()->{assertThat(reports.task(id).nodes().getFirst().confirmation()).isEqualTo("CONFIRMED");return null;});
                });
                Users.as(actor,()->{assertThat(providers.a.get(REGION,"background",String.class,()->"new")).isEqualTo("new");
                    assertThat(reports.task(id).nodes().getFirst().expectedProcessId()).isEqualTo(enabled.processId());return null;});
            }finally{background.close();}
        }
    }
    private SmaPrincipal actor(long tenant){return Users.as(new SmaPrincipal(101,"admin",tenant,"admin",null,true,DataAccess.unrestricted(),Set.of(),"UTC"),()->{
        permissions.sync(catalog.permissions());var role=roles.save(null,new RoleService.Input(0L,"Cache operator","cache-operator",0,true,DataScope.SELF,Set.of(),null));
        grants.replace(role.id(),permissions.list().stream().filter(row->Set.of("monitor:cache:clear","monitor:cache:reset-stats").contains(row.permissionKey())).map(PermissionService.View::id).collect(java.util.stream.Collectors.toSet()));
        var user=users.create(new UserService.Create(new UserService.Input("worker","Operator",null,null,null,"UNSPECIFIED",true,Set.of(role.id()),Set.of(),"zh-CN","UTC"),"Q722ee214d"));
        return new SmaPrincipal(user.id(),user.username(),tenant,"admin",null,false,DataAccess.own(),grants.keys(Set.of(role.id()),false),"UTC");
    });}
    private String submit(SmaPrincipal actor,CacheTasks.Action action){return Users.as(actor,()->{var preview=tasks.preview(action,new CacheTasks.Selection(Set.of(REGION),null,null,null),"confirmation test");return tasks.submit(preview.id(),UUID.randomUUID().toString()).id();});}
    private void execute(SmaPrincipal actor){var lease=job(actor.tenantId(),"cache-dispatch",()->executor.claim(a.processId()).orElseThrow());assertThat(job(actor.tenantId(),"cache-task:"+lease.taskId(),()->executor.executeOne(lease))).isTrue();}
    private int process(SmaPrincipal actor,CacheNodeIdentity identity,long epoch,CacheConfirmationProcessor processor){return job(actor.tenantId(),"cache-node:"+identity.processId(),()->processor.processTenant(epoch));}
    private CacheTaskEntity row(String id){return taskRows.selectOne(new LambdaQueryWrapper<CacheTaskEntity>().eq(CacheTaskEntity::getPublicId,id));}
    private CacheConfirmationEntity entry(String id,String node){return evidence.selectOne(new LambdaQueryWrapper<CacheConfirmationEntity>().eq(CacheConfirmationEntity::getTaskId,row(id).getId()).eq(CacheConfirmationEntity::getNodeId,node));}
    private void expireNode(CacheNodeIdentity identity,String id){host(()->{var row=leaseRows.selectOne(new LambdaQueryWrapper<CacheNodeLeaseEntity>().eq(CacheNodeLeaseEntity::getClusterHash,identity.clusterHash()).eq(CacheNodeLeaseEntity::getNodeId,id));if(row!=null){row.setLeaseUntil(Instant.EPOCH);leaseRows.updateById(row);}return null;});}
    private <T> T host(Supplier<T> work){var previous=SecurityContextHolder.getContext();SecurityContextHolder.clearContext();try{return work.get();}finally{SecurityContextHolder.setContext(previous);}}
    private <T> T job(long tenant,String work,Supplier<T> action){return host(()->ActorContext.withinJob(tenant,work,action));}
    private final class Providers implements AutoCloseable {
        final String namespace="p08-confirm-"+UUID.randomUUID();final long tenant;final Provider provider;
        final List<LettuceConnectionFactory> factories=new ArrayList<>();final List<StringRedisTemplate> templates=new ArrayList<>();final DurableManagedCache a,b;
        Providers(Provider provider,long tenant){this.provider=provider;this.tenant=tenant;a=create();b=create();}
        DurableManagedCache create(){
            var properties=new CacheProperties(CacheProperties.Provider.valueOf(provider.name()),namespace,null,null,null,0.0,null);var clock=Clock.systemUTC();
            ManagedCache delegate;CacheInvalidationTarget target;
            if(provider==Provider.REDIS){
                String portText=System.getenv("SMA_CACHE_REDIS_TEST_PORT");Assumptions.assumeTrue(portText!=null,"Dedicated Redis required");int port=Integer.parseInt(portText);
                if(port<10000||port>65535)throw new IllegalArgumentException("Dedicated Redis port required");
                var factory=new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1",port),LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build());
                factory.afterPropertiesSet();factory.start();factories.add(factory);var template=new StringRedisTemplate(factory);templates.add(template);
                var redis=new RedisManagedCache(registry,properties,CacheResilienceProperties.defaults(),new RedisCacheStore(template,namespace));delegate=redis;target=redis;
            }else{var local=new LocalManagedCache(registry,properties,clock);delegate=local;target=local;}
            return new DurableManagedCache(delegate,target,revisions,registry,properties,clock);
        }
        public void close(){a.close();b.close();try{if(!templates.isEmpty())templates.getFirst().delete(new RedisCacheStore(templates.getFirst(),namespace).epochKey(REGION,tenant));}finally{factories.forEach(LettuceConnectionFactory::destroy);}}
    }
}
