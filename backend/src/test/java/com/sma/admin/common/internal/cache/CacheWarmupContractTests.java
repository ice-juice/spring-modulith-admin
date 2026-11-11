package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.internal.service.CacheRevisionStore;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;

class CacheWarmupContractTests {
    private final CacheRevisionStore revisions=mock(CacheRevisionStore.class);
    private final CacheWarmupLoader.Limits limits=new CacheWarmupLoader.Limits(4,20,5,Duration.ofSeconds(10));
    @Test void budgetsAndPagesAreBoundedAndImmutable(){
        assertThatThrownBy(()->new CacheWarmupLoader.Limits(17,1000,20,Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CacheWarmupLoader.Limits(1,1001,20,Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CacheWarmupLoader.Limits(1,2,21,Duration.ofSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CacheWarmupLoader.Limits(1,2,20,Duration.ofSeconds(121))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CacheWarmupLoader.Page(List.of(),"cursor",false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CacheWarmupLoader.Page(Collections.nCopies(17,"key"),"cursor",false)).isInstanceOf(IllegalArgumentException.class);
        var keys=new ArrayList<>(List.of("key"));var page=new CacheWarmupLoader.Page(keys,"cursor",false);keys.clear();assertThat(page.keys()).containsExactly("key");
        assertThatThrownBy(()->page.keys().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(()->new CacheWarmupLoader.Spec("shop.items",1,"monitor:cache:view",limits)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void duplicateUnknownAndPrivateRegistrationsFailAtStartup(){
        var loader=loader();var regions=regions(CacheDescriptor.Scope.TENANT);
        assertThatThrownBy(()->registry(List.of(loader,loader),regions,CacheProperties.Provider.LOCAL,true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->registry(List.of(loader),new CacheRegistry(List.of()),CacheProperties.Provider.LOCAL,true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->registry(List.of(loader),regions(CacheDescriptor.Scope.TENANT_USER),CacheProperties.Provider.LOCAL,true)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void disabledNoneMissingPermissionAndPausedCannotAdvertisePrewarm(){
        var regions=regions(CacheDescriptor.Scope.TENANT);var loader=loader();
        Users.as(actor(true),()->{
            assertThat(registry(List.of(loader),regions,CacheProperties.Provider.LOCAL,false).inspect("shop.items").reason()).isEqualTo("DISABLED");
            assertThat(registry(List.of(loader),regions,CacheProperties.Provider.NONE,true).inspect("shop.items").reason()).isEqualTo("DISABLED");
            assertThat(registry(List.of(),regions,CacheProperties.Provider.LOCAL,true).inspect("shop.items").reason()).isEqualTo("UNSUPPORTED");
            var registry=registry(List.of(loader),regions,CacheProperties.Provider.LOCAL,true);
            when(revisions.state("shop.items")).thenReturn(new CacheRevisionStore.State(1,1,0,true,null));assertThat(registry.inspect("shop.items").reason()).isEqualTo("PAUSED");
            when(revisions.state("shop.items")).thenReturn(CacheRevisionStore.State.initial());assertThat(registry.inspect("shop.items").available()).isTrue();return null;
        });
        Users.as(actor(false),()->{assertThat(registry(List.of(loader),regions,CacheProperties.Provider.LOCAL,true).inspect("shop.items").reason()).isEqualTo("FORBIDDEN");return null;});
        assertThat(new CacheWarmupProperties(null).enabled()).isFalse();
    }
    @Test void frozenLoaderChangesAreRejectedButDisablingWarmupDoesNotPreventOwnedCancellation(){
        var regions=regions(CacheDescriptor.Scope.TENANT);var loader=loader();var properties=new CacheProperties(null,null,null,null,null,null,null);
        var warmups=registry(List.of(loader),regions,CacheProperties.Provider.LOCAL,true);var planner=new CacheTaskPlanner(regions,properties,revisions,warmups);
        when(revisions.state("shop.items")).thenReturn(CacheRevisionStore.State.initial());when(revisions.lockedState("shop.items")).thenReturn(CacheRevisionStore.State.initial());
        Users.as(actor(true),()->{
            var targets=planner.preview(CacheTasks.Action.PREWARM,new CacheTasks.Selection(Set.of("shop.items"),null,null,null)).targets();
            assertThat(targets.getFirst().warmup()).isEqualTo(loader.specification());planner.validateFrozen(targets);
            var disabled=new CacheTaskPlanner(regions,properties,revisions,registry(List.of(loader),regions,CacheProperties.Provider.LOCAL,false));
            disabled.authorize(CacheTasks.Action.PREWARM,targets); // Existing receipt remains readable/replayable.
            assertThatThrownBy(()->disabled.validateFrozen(targets)).isInstanceOf(CacheTaskRejected.class);
            Users.as(actor(false),()->{disabled.authorizeCancellation(CacheTasks.Action.PREWARM,targets);return null;});
            when(loader.specification()).thenReturn(new CacheWarmupLoader.Spec("shop.items",2,"shop:item:read",limits));
            var changed=new CacheTaskPlanner(regions,properties,revisions,registry(List.of(loader),regions,CacheProperties.Provider.LOCAL,true));
            assertThatThrownBy(()->changed.validateFrozen(targets)).isInstanceOf(CacheTaskRejected.class);return null;
        });
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        var legacy=json.readValue("{\"region\":\"shop.items\",\"nameKey\":\"shop.cache.items\",\"scope\":\"TENANT\",\"managePermission\":\"shop:cache:manage\",\"controlVersion\":0,\"registrationHash\":\"legacy\"}",CacheTasks.Target.class);
        assertThat(legacy.warmup()).isNull();
    }
    private CacheWarmupLoader loader(){var loader=mock(CacheWarmupLoader.class);when(loader.specification()).thenReturn(new CacheWarmupLoader.Spec("shop.items",1,"shop:item:read",limits));return loader;}
    private CacheRegistry regions(CacheDescriptor.Scope scope){return new CacheRegistry(List.of(()->List.of(new CacheDescriptor("shop.items","shop","shop.cache.items","catalog","read",CacheDescriptor.Kind.DATA,scope,1,CachePolicy.defaults(),Set.of(CacheDescriptor.Operation.PREWARM),Set.of("save"),"shop:cache:manage"))));}
    private CacheWarmupRegistry registry(List<CacheWarmupLoader> loaders,CacheRegistry regions,CacheProperties.Provider provider,boolean enabled){return new CacheWarmupRegistry(loaders,regions,new CacheProperties(provider,null,null,null,null,null,null),new CacheWarmupProperties(enabled),revisions);}
    private SmaPrincipal actor(boolean read){var grants=new HashSet<>(Set.of("monitor:cache:view","monitor:cache:prewarm","shop:cache:manage"));if(read)grants.add("shop:item:read");return new SmaPrincipal(101,"operator",97501,"admin",null,false,DataAccess.own(),grants,"UTC");}
}
