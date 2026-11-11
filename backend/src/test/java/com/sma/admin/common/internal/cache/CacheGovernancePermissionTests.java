package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.common.BizException;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.service.*;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;

class CacheGovernancePermissionTests {
    private final CacheRevisionStore revisions=mock(CacheRevisionStore.class);
    private final CacheControlAuditStore audit=mock(CacheControlAuditStore.class);
    private final ManagedCache cache=mock(ManagedCache.class);
    private final CacheRegistry registry=new CacheRegistry(List.of(()->List.of(
            descriptor("shop.items",CacheDescriptor.Kind.DATA,Set.of(CacheDescriptor.Operation.EDIT_POLICY,CacheDescriptor.Operation.PAUSE,CacheDescriptor.Operation.RESET_STATS)),
            descriptor("shop.readonly",CacheDescriptor.Kind.DATA,Set.of()),descriptor("shop.sessions",CacheDescriptor.Kind.PROTECTED,Set.of()))));
    private final CacheGovernanceService service=new CacheGovernanceService(registry,new CacheProperties(null,null,null,null,null,null,null,
            new CacheProperties.RegionOverride(Duration.ofSeconds(30),Duration.ofSeconds(5),20L,4096L,1024)),revisions,audit,cache);
    @Test void everyMutationNeedsItsOwnOperationAndRegionPermissionAndProtectedStateHasNoBypass(){
        for(var action:CacheGovernance.Action.values()){
            Users.as(actor("monitor:cache:clear","monitor:cache:view","shop:cache:manage"),()->{
                assertThatThrownBy(()->service.change("shop.items",command(action))).isInstanceOf(AccessDeniedException.class);return null;
            });
            Users.as(actor("monitor:cache:policy","monitor:cache:pause","monitor:cache:reset-stats"),()->{
                assertThatThrownBy(()->service.change("shop.items",command(action))).isInstanceOf(AccessDeniedException.class);return null;
            });
        }
        Users.as(actor("monitor:cache:view","monitor:cache:policy","shop:cache:manage"),()->{
            assertThatThrownBy(()->service.change("shop.readonly",command(CacheGovernance.Action.RESET_POLICY))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->service.change("shop.sessions",command(CacheGovernance.Action.RESET_POLICY))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->service.change("missing.items",command(CacheGovernance.Action.RESET_POLICY))).isInstanceOf(BizException.class);return null;
        });
        Users.as(SmaPrincipal.forApplication(9600,"admin","machine"),()->{
            assertThatThrownBy(()->service.change("shop.items",command(CacheGovernance.Action.RESET_STATS))).isInstanceOf(AccessDeniedException.class);return null;
        });
        verifyNoInteractions(revisions,audit,cache);
    }
    @Test void deploymentLimitsAndExactMillisecondsAreEnforcedBeforeAnyWrite(){
        Users.as(actor("monitor:cache:policy","shop:cache:manage"),()->{
            var tooHigh=new CachePolicy(Duration.ofSeconds(31),Duration.ZERO,1,1024,128);
            var fractional=new CachePolicy(Duration.ofNanos(1_500_000),Duration.ZERO,1,1024,128);
            for(var policy:List.of(tooHigh,fractional))assertThatThrownBy(()->service.change("shop.items",new CacheGovernance.Command(CacheGovernance.Action.SET_POLICY,0,policy,"bounded"))).isInstanceOf(BizException.class);
            assertThatThrownBy(()->service.change("shop.items",new CacheGovernance.Command(CacheGovernance.Action.RESET_POLICY,0,null,"reason\nwith control"))).isInstanceOf(BizException.class);return null;
        });verifyNoInteractions(revisions,audit,cache);
    }
    @Test void policyPermissionDoesNotImplicitlyRequireClearPermission(){
        var policy=new CachePolicy(Duration.ofSeconds(10),Duration.ZERO,2,2048,512);
        when(revisions.lockedState("shop.items")).thenReturn(CacheRevisionStore.State.initial());
        when(revisions.change("shop.items",0,CacheRevisionStore.Change.POLICY,policy)).thenReturn(new CacheRevisionStore.State(1,1,0,false,policy));
        Users.as(actor("monitor:cache:policy","shop:cache:manage"),()->{
            assertThat(service.change("shop.items",new CacheGovernance.Command(CacheGovernance.Action.SET_POLICY,0,policy,"test")).effectivePolicy()).isEqualTo(policy);return null;
        });verify(audit).append(eq("shop.items"),eq(CacheGovernance.Action.SET_POLICY),eq("test"),eq("admin"),any(),any());
    }
    @Test void providerByteCeilingCannotBeExpandedThroughARegionPolicy(){
        var limited=new CacheGovernanceService(registry,new CacheProperties(null,null,null,1024L,null,null,null),revisions,audit,cache);
        Users.as(actor("monitor:cache:policy","shop:cache:manage"),()->{
            assertThatThrownBy(()->limited.change("shop.items",new CacheGovernance.Command(CacheGovernance.Action.SET_POLICY,0,
                    new CachePolicy(Duration.ofSeconds(10),Duration.ZERO,2,2048,512),"too large"))).isInstanceOf(BizException.class);return null;
        });verifyNoInteractions(revisions,audit,cache);
    }
    private CacheGovernance.Command command(CacheGovernance.Action action){return new CacheGovernance.Command(action,0,action==CacheGovernance.Action.SET_POLICY?new CachePolicy(Duration.ofSeconds(10),Duration.ZERO,2,2048,512):null,"reason");}
    private CacheDescriptor descriptor(String id,CacheDescriptor.Kind kind,Set<CacheDescriptor.Operation> operations){return new CacheDescriptor(id,"shop","shop.cache.items","shop","items",kind,CacheDescriptor.Scope.TENANT,1,kind==CacheDescriptor.Kind.DATA?CachePolicy.defaults():null,operations,Set.of("save"),"shop:cache:manage");}
    private SmaPrincipal actor(String... permissions){return new SmaPrincipal(101,"manager",9600,"admin",null,false,DataAccess.none(),Set.of(permissions),"UTC");}
}
