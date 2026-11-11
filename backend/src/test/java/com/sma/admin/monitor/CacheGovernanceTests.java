package com.sma.admin.monitor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.service.CacheControlAuditStore;
import com.sma.admin.common.security.*;
import com.sma.admin.monitor.internal.service.CacheDirectoryService;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties="sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class CacheGovernanceTests {
    private static final String REGION="config.dictionary-options";
    @Autowired CacheGovernance governance;
    @Autowired ManagedCache cache;
    @Autowired CacheDirectoryService directory;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvcTester mvc;
    @MockitoSpyBean CacheControlAuditStore audit;
    private final CachePolicy policy=new CachePolicy(Duration.ofSeconds(60),Duration.ZERO,10,4096,1024);

    @Test void committedControlsAreAppliedAuditedAndStatisticsResetKeepsData(){
        Users.as(manager(9601),()->{
            var initial=governance.state(REGION);assertThat(initial.version()).isZero();
            var configured=governance.change(REGION,command(CacheGovernance.Action.SET_POLICY,0,policy));
            cache.get(REGION,"test",String.class,()->"cached");cache.get(REGION,"test",String.class,()->"wrong");
            assertThat(directory.detail(REGION).observation().policy().sources()).containsEntry("ttl","TENANT_RUNTIME");
            assertThat(directory.detail(REGION).observation().policy().effective()).isEqualTo(policy);
            var reset=governance.change(REGION,command(CacheGovernance.Action.RESET_STATS,1,null));
            assertThat(reset.dataRevision()).isEqualTo(configured.dataRevision());assertThat(cache.snapshot(REGION).hits()).isZero();
            assertThat(cache.get(REGION,"test",String.class,()->"wrong")).isEqualTo("cached");
            var paused=governance.change(REGION,command(CacheGovernance.Action.PAUSE,2,null));assertThat(paused.paused()).isTrue();
            assertThat(directory.detail(REGION).state()).isEqualTo("PAUSED");
            assertThat(cache.get(REGION,"test",String.class,()->"source")).isEqualTo("source");
            governance.change(REGION,command(CacheGovernance.Action.RESUME,3,null));
            assertThat(cache.get(REGION,"test",String.class,()->"new")).isEqualTo("new");
            var defaults=governance.change(REGION,command(CacheGovernance.Action.RESET_POLICY,4,null));
            assertThat(defaults.requestedPolicy()).isNull();assertThat(defaults.effectivePolicy()).isEqualTo(defaults.deploymentPolicy());
            var history=governance.history(REGION,100);assertThat(history).hasSize(5);
            assertThat(history.getFirst().before().version()).isEqualTo(4);assertThat(history.getFirst().after().version()).isEqualTo(5);
            assertThat(history.getFirst().actorId()).isEqualTo(101);assertThat(history.getFirst().portal()).isEqualTo("admin");
            assertThat(history.toString()).doesNotContain("cached","namespace","password");
            assertThat(governance.history(REGION,2)).hasSize(2);
            Users.as(manager(9602),()->{assertThat(governance.state(REGION).version()).isZero();assertThat(governance.history(REGION,100)).isEmpty();return null;});return null;
        });
    }
    @Test void auditFailureAndOuterRollbackDoNotPublishControlChanges(){
        Users.as(manager(9603),()->{
            cache.get(REGION,"test",String.class,()->"original");
            CacheControlAuditStore auditTarget=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
            doThrow(new IllegalStateException("Injected audit failure")).when(auditTarget).append(eq(REGION),eq(CacheGovernance.Action.PAUSE),eq("audit-failure"),anyString(),any(),any());
            assertThatThrownBy(()->governance.change(REGION,new CacheGovernance.Command(CacheGovernance.Action.PAUSE,0,null,"audit-failure"))).isInstanceOf(IllegalStateException.class);
            assertThat(governance.state(REGION).version()).isZero();assertThat(governance.history(REGION,10)).isEmpty();
            new TransactionTemplate(transactions).execute(status->{
                governance.change(REGION,command(CacheGovernance.Action.PAUSE,0,null));
                assertThat(governance.state(REGION).version()).isZero();status.setRollbackOnly();return null;
            });
            assertThat(governance.state(REGION).paused()).isFalse();assertThat(governance.history(REGION,10)).isEmpty();
            assertThat(cache.get(REGION,"test",String.class,()->"wrong")).isEqualTo("original");return null;
        });
    }
    @Test void concurrentEditsHaveOneWinnerAndOneAuditRecord() throws Exception {
        var start=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var results=new ArrayList<Future<Boolean>>();
            for(var action:List.of(CacheGovernance.Action.PAUSE,CacheGovernance.Action.RESET_STATS))results.add(executor.submit(()->{
                start.await();return Users.as(manager(9604),()->{try{governance.change(REGION,command(action,0,null));return true;}catch(CacheControlConflictException stale){return false;}});
            }));start.countDown();int succeeded=0;for(var result:results)if(result.get(20,TimeUnit.SECONDS))succeeded++;assertThat(succeeded).isEqualTo(1);
        }
        Users.as(manager(9604),()->{assertThat(governance.history(REGION,10)).hasSize(1);assertThat(governance.state(REGION).version()).isEqualTo(1);return null;});
    }
    @Test void httpUsesGranularPermissionsAndLocalized409ForStaleEditors(){
        var manager=manager(9605);
        String body="""
            {"expectedVersion":0,"reason":"Tune test region","ttlMillis":2000,"negativeTtlMillis":0,"maxEntries":10,"maxBytes":4096,"maxEntryBytes":1024}
            """;
        Users.as(manager,()->{assertThat(mvc.put().uri("/api/monitor/caches/"+REGION+"/policy").with(Users.request(CurrentUser.require())).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatusOk().bodyJson().hasPathSatisfying("$.data.version",v->v.assertThat().isEqualTo("1"));return null;});
        Users.as(manager,()->{assertThat(mvc.post().uri("/api/monitor/caches/"+REGION+"/pause").with(Users.request(CurrentUser.require())).header("Idempotency-Key",UUID.randomUUID().toString()).header("Accept-Language","en-US").contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"reason\":\"stale editor\"}"))
                .hasStatus(409).bodyJson().hasPathSatisfying("$.error",v->v.assertThat().isEqualTo("common.error.cache-control-conflict"))
                .hasPathSatisfying("$.message",v->v.assertThat().isEqualTo("Cache governance state has changed; refresh before trying again"));return null;});
        Users.as(manager,()->{assertThat(governance.history(REGION,10)).hasSize(1);return null;});
        Users.as(manager,()->{assertThat(mvc.post().uri("/api/monitor/caches/"+REGION+"/pause").with(Users.request(CurrentUser.require())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"version is required\"}")).hasStatus(400);return null;});
        Users.as(new SmaPrincipal(101,"viewer",9605,"admin",null,false,DataAccess.none(),Set.of("monitor:cache:view"),"UTC"),()->{
            assertThat(mvc.post().uri("/api/monitor/caches/"+REGION+"/statistics/reset").with(Users.request(CurrentUser.require())).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1,\"reason\":\"denied\"}")).hasStatus(403);return null;
        });
        Users.as(manager,()->{assertThat(mvc.get().uri("/api/monitor/caches/"+REGION+"/history").with(Users.request(CurrentUser.require())))
                .hasStatusOk().bodyJson().hasPath("$.data[0].before.deploymentPolicy").doesNotHavePath("$.data[0].password");return null;});
    }
    @Test void policyEditorDoesNotNeedClearAndClearDoesNotGrantPause(){
        var editor=new SmaPrincipal(101,"editor",9606,"admin",null,false,DataAccess.none(),Set.of("monitor:cache:view","monitor:cache:policy"),"UTC");
        Users.as(editor,()->{
            assertThat(governance.change(REGION,command(CacheGovernance.Action.SET_POLICY,0,policy)).version()).isEqualTo(1);
            assertThat(directory.detail(REGION).allowedOperations()).containsExactly("EDIT_POLICY");
            assertThatThrownBy(()->governance.change(REGION,command(CacheGovernance.Action.PAUSE,1,null))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);return null;
        });
    }
    private CacheGovernance.Command command(CacheGovernance.Action action,long version,CachePolicy requested){return new CacheGovernance.Command(action,version,requested,"test operation");}
    private SmaPrincipal manager(long tenant){return new SmaPrincipal(101,"manager",tenant,"admin",null,false,DataAccess.unrestricted(),Set.of("monitor:cache:view","monitor:cache:clear","monitor:cache:policy","monitor:cache:pause","monitor:cache:reset-stats"),"UTC");}
}
