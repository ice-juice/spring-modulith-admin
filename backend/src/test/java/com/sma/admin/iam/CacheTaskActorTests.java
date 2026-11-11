package com.sma.admin.iam;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.CacheTaskActorService;
import com.sma.admin.support.Users;

class CacheTaskActorTests {
    private final AccountQuery accounts=mock(AccountQuery.class);
    private final PermissionQuery permissions=mock(PermissionQuery.class);
    private final RoleAccessQuery scopes=mock(RoleAccessQuery.class);
    private final CacheTaskActorService resolver=new CacheTaskActorService(accounts,permissions,scopes);
    private final String work="cache-task:00000000-0000-0000-0000-000000000001";
    @Test void onlyPersistedTaskJobsMayResolveAnIdentity(){
        Users.as(new SmaPrincipal(101,"manager",97200,"admin",null,false,DataAccess.none(),Set.of("monitor:cache:clear"),"UTC"),()->{
            assertThatThrownBy(()->resolver.resolve(102,"admin")).isInstanceOf(AccessDeniedException.class);return null;
        });
        ActorContext.withinJob(97200,"different-job",()->{
            assertThatThrownBy(()->resolver.resolve(102,"admin")).isInstanceOf(AccessDeniedException.class);return null;
        });verifyNoInteractions(accounts,permissions,scopes);
    }
    @Test void freshGrantsAndDataScopeAreResolvedAndJobContextIsRestored(){
        when(accounts.findById(102)).thenReturn(Optional.of(account(97200,true,"admin")));
        when(scopes.resolve(Set.of(1L),10L)).thenReturn(DataAccess.own());when(permissions.keys(Set.of(1L),false)).thenReturn(Set.of("monitor:cache:view","monitor:cache:clear"));
        ActorContext.withinJob(97200,work,()->{
            var principal=resolver.resolve(102,"admin").orElseThrow();assertThat(principal.dataAccess()).isEqualTo(DataAccess.own());
            assertThat(principal.permissions()).contains("monitor:cache:clear");assertThat(ActorContext.require().kind()).isEqualTo(ActorRef.Kind.SYSTEM_JOB);
            when(permissions.keys(Set.of(1L),false)).thenReturn(Set.of("monitor:cache:view"));
            assertThat(resolver.resolve(102,"admin").orElseThrow().permissions()).doesNotContain("monitor:cache:clear");return null;
        });
    }
    @Test void disabledCrossTenantAndWrongPortalAccountsCannotBeRecreated(){
        ActorContext.withinJob(97200,work,()->{
            for(var account:List.of(account(97200,false,"admin"),account(97201,true,"admin"),account(97200,true,"other"))){
                when(accounts.findById(102)).thenReturn(Optional.of(account));assertThat(resolver.resolve(102,"admin")).isEmpty();
            }return null;
        });verifyNoInteractions(permissions,scopes);
    }
    private AccountView account(long tenant,boolean enabled,String portal){return new AccountView(102L,tenant,"worker","Worker",portal,10L,false,enabled,Set.of(1L),Set.of(),"zh-CN","UTC",Instant.now(),1,null);}
}
