package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.service.CacheTaskStore;
import com.sma.admin.common.request.*;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;

class CacheTaskBoundaryTests {
    @Test void previewBudgetIsClaimedBeforePotentiallyExpensiveVersionReads(){
        var planner=mock(CacheTaskPlanner.class);var store=mock(CacheTaskStore.class);var requests=mock(RequestExecution.class);var tenants=mock(TenantEntryQuery.class);
        var service=new CacheTaskService(planner,store,requests,tenants);
        when(tenants.securityState(9701)).thenReturn(Optional.of(new TenantEntryQuery.SecurityState(9701,1,true,true)));
        doThrow(new RequestRejected(RequestRejected.Reason.RATE_LIMITED,10)).when(requests).claimBudget("cache.preview",30,Duration.ofMinutes(1));
        Users.as(actor(Set.of("monitor:cache:view","monitor:cache:clear")),()->{
            assertThatThrownBy(()->service.preview(CacheTasks.Action.CLEAR,new CacheTasks.Selection(Set.of(),null,null,null),"bounded"))
                    .isInstanceOf(RequestRejected.class);return null;
        });verifyNoInteractions(planner,store);
        verify(requests,never()).claimTenantBudget(anyString(),anyInt(),any());
    }
    @Test void suspendedTenantAndMissingActionPermissionCannotCreatePreviews(){
        var planner=mock(CacheTaskPlanner.class);var store=mock(CacheTaskStore.class);var requests=mock(RequestExecution.class);var tenants=mock(TenantEntryQuery.class);
        var service=new CacheTaskService(planner,store,requests,tenants);
        when(tenants.securityState(9701)).thenReturn(Optional.of(new TenantEntryQuery.SecurityState(9701,2,false,false)));
        Users.as(actor(Set.of("monitor:cache:view","monitor:cache:clear")),()->{
            assertThatThrownBy(()->service.preview(CacheTasks.Action.CLEAR,new CacheTasks.Selection(Set.of(),null,null,null),"suspended"))
                    .isInstanceOf(AccessDeniedException.class);return null;
        });
        Users.as(actor(Set.of("monitor:cache:view")),()->{
            assertThatThrownBy(()->service.preview(CacheTasks.Action.CLEAR,new CacheTasks.Selection(Set.of(),null,null,null),"unauthorized"))
                    .isInstanceOf(AccessDeniedException.class);return null;
        });verifyNoInteractions(planner,store,requests);
    }
    @Test void previewLifetimeAndPersistentCapacityAreStrictlyBounded(){
        assertThat(new CacheTaskProperties(null,null).previewTtl()).isEqualTo(Duration.ofMinutes(5));
        for(var ttl:List.of(Duration.ZERO,Duration.ofSeconds(29),Duration.ofMinutes(11),Duration.ofSeconds(30).plusNanos(1)))
            assertThatThrownBy(()->new CacheTaskProperties(ttl,100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CacheTaskProperties(null,0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CacheTaskProperties(null,100001)).isInstanceOf(IllegalArgumentException.class);
    }
    private SmaPrincipal actor(Set<String> permissions){return new SmaPrincipal(101,"manager",9701,"admin",null,false,DataAccess.none(),permissions,"UTC");}
}
