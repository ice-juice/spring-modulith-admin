package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.context.request.RequestContextHolder;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.cache.*;
import com.sma.admin.common.internal.service.CacheTaskExecutor;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.*;
import com.sma.admin.monitor.internal.service.MonitorPermissionCatalog;
import com.sma.admin.support.*;

@IntegrationTest
@TestPropertySource(properties="sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class CacheDispatcherDrainTests {
    private static final long TENANT=97230;
    private static final String REGION="config.dictionary-options";
    @Autowired DeploymentWorkCoordinator work;
    @Autowired CacheNodeIdentity identity;
    @Autowired CacheTasks tasks;
    @Autowired CacheGovernance controls;
    @Autowired UserService users;
    @Autowired RoleService roles;
    @Autowired RolePermissionService grants;
    @Autowired PermissionService permissions;
    @Autowired MonitorPermissionCatalog catalog;
    @MockitoSpyBean CacheTaskExecutor executor;

    @Test void drainingFinishesTheAdmittedDatabaseMutationButLeavesUnclaimedWorkQueued() throws Exception {
        var actor=operator();String first=submit(actor),second=submit(actor);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(call->{entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("Execution was not released");return call.callRealMethod();})
                .when(executor).executeOne(any());
        TenantWorkQuery directory=(after,limit)->after<TENANT?List.of(new TenantWorkQuery.Entry(TENANT,true)):List.of();
        var dispatcher=new CacheTaskDispatcher(executor,directory,new CacheTaskDispatchProperties(true,1,4,null),identity,work);
        try {
            host(()->{dispatcher.tick();return null;});assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            assertThat(work.snapshot().activeWork()).isOne();assertThat(host(work::beginDrain)).isTrue();
            Users.as(actor,()->{
                assertThat(tasks.get(first).state()).isEqualTo(CacheTasks.State.RUNNING);
                assertThat(tasks.get(second).state()).isEqualTo(CacheTasks.State.QUEUED);
                assertThat(controls.state(REGION).dataRevision()).isZero();return null;
            });
            assertThat(host(work::tryStart)).isEmpty();release.countDown();
            await().atMost(Duration.ofSeconds(10)).until(()->work.snapshot().activeWork()==0);
            Users.as(actor,()->{
                assertThat(tasks.get(first).state()).isEqualTo(CacheTasks.State.SUCCEEDED);
                assertThat(tasks.get(first).items().getFirst().state()).isEqualTo("APPLIED");
                assertThat(controls.state(REGION).dataRevision()).isEqualTo(1);
                assertThat(tasks.get(second).state()).isEqualTo(CacheTasks.State.QUEUED);
                assertThat(tasks.get(second).items().getFirst().state()).isEqualTo("PENDING");return null;
            });
            host(()->{dispatcher.tick();return null;});verify(executor,times(1)).claim(identity.processId());
            verify(executor,times(1)).executeOne(any());
            assertThat(work.snapshot().draining()).isTrue();
        } finally { release.countDown();dispatcher.close(); }
    }
    private SmaPrincipal operator(){
        var admin=new SmaPrincipal(101,"admin",TENANT,"admin",null,true,DataAccess.unrestricted(),Set.of(),"UTC");
        return Users.as(admin,()->{
            permissions.sync(catalog.permissions());
            var role=roles.save(null,new RoleService.Input(0L,"Drain operator","drain-operator",0,true,DataScope.SELF,Set.of(),null));
            grants.replace(role.id(),permissions.list().stream().filter(row->row.permissionKey().equals("monitor:cache:clear"))
                    .map(PermissionService.View::id).collect(java.util.stream.Collectors.toSet()));
            var input=new UserService.Input("drain-worker","Drain operator",null,null,null,"UNSPECIFIED",true,Set.of(role.id()),Set.of(),"zh-CN","UTC");
            var user=users.create(new UserService.Create(input,"Q722ee214d"));
            return new SmaPrincipal(user.id(),user.username(),TENANT,"admin",null,false,DataAccess.own(),grants.keys(Set.of(role.id()),false),"UTC");
        });
    }
    private String submit(SmaPrincipal actor){return Users.as(actor,()->{
        var preview=tasks.preview(CacheTasks.Action.CLEAR,new CacheTasks.Selection(Set.of(REGION),null,null,null),"drain verification");
        return tasks.submit(preview.id(),UUID.randomUUID().toString()).id();
    });}
    private static <T>T host(Supplier<T> operation){
        var request=RequestContextHolder.getRequestAttributes();var authentication=SecurityContextHolder.getContext();
        RequestContextHolder.resetRequestAttributes();SecurityContextHolder.clearContext();
        try{return operation.get();}finally{RequestContextHolder.setRequestAttributes(request);SecurityContextHolder.setContext(authentication);}
    }
}
