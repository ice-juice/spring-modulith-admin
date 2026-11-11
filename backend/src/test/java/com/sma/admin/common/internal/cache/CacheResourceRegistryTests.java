package com.sma.admin.common.internal.cache;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;

class CacheResourceRegistryTests {
    private final CacheRegistrationOwnership ownership=mock(CacheRegistrationOwnership.class);
    private final CacheNodeIdentity identity=new CacheNodeIdentity(new CacheNodeProperties(null,null,null,null));
    private final CacheRegistry catalog=new CacheRegistry(List.of(()->List.of(region("shop.runtime",CacheDescriptor.Kind.RUNTIME),region("shop.resource",CacheDescriptor.Kind.RESOURCE),region("shop.protected",CacheDescriptor.Kind.PROTECTED))));
    @Test void onlyTheOwningResourceAdapterCanRegisterAndEveryDuplicateOrProtectedAdapterIsRejected(){
        var adapter=adapter("shop.runtime");new CacheResourceRegistry(catalog,identity,List.of(adapter),ownership);
        verify(ownership).validateAdapter(adapter,catalog.require("shop.runtime"));
        assertThatThrownBy(()->new CacheResourceRegistry(catalog,identity,List.of(adapter,adapter),ownership)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
        assertThatThrownBy(()->new CacheResourceRegistry(catalog,identity,List.of(adapter("shop.protected")),ownership)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CacheResourceRegistry(catalog,identity,List.of(adapter("shop.unknown")),ownership)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void restrictedFailedAndMissingSamplesRemainUnknownAndDoNotLeakExceptions(){
        var runtime=adapter("shop.runtime");var resource=adapter("shop.resource");
        var registry=new CacheResourceRegistry(catalog,identity,List.of(runtime,resource),ownership);
        Users.as(actor(),()->{
            when(runtime.inspect()).thenReturn(new CacheResourceAdapter.Snapshot(3,10,-1,1,"shop.cache.lifecycle.runtime"));
            var sample=registry.inspect("shop.runtime");assertThat(sample.state()).isEqualTo("ACTIVE");assertThat(sample.entries()).isEqualTo(3);assertThat(sample.processId()).isEqualTo(identity.processId());
            when(runtime.inspect()).thenThrow(new AccessDeniedException("secret scope"));
            assertThat(registry.inspect("shop.runtime").state()).isEqualTo("RESTRICTED");assertThat(registry.inspect("shop.runtime").entries()).isEqualTo(-1);
            when(resource.inspect()).thenThrow(new IllegalStateException("secret path and endpoint"));
            var failed=registry.inspect("shop.resource");assertThat(failed.state()).isEqualTo("UNAVAILABLE");assertThat(failed.toString()).doesNotContain("secret path","endpoint");
            assertThat(new CacheResourceRegistry(catalog,identity,List.of(),ownership).inspect("shop.runtime").state()).isEqualTo("ADAPTER_REQUIRED");
            assertThat(registry.inspect("shop.protected").entries()).isEqualTo(-1);assertThat(registry.inspect("shop.protected").state()).isEqualTo("PROTECTED");return null;
        });
    }
    @Test void malformedSamplesAndMissingViewPermissionFailClosed(){
        assertThatThrownBy(()->new CacheResourceAdapter.Snapshot(-2,0,0,0,"shop.lifecycle")).isInstanceOf(IllegalArgumentException.class);
        var resource=adapter("shop.resource");when(resource.inspect()).thenReturn(new CacheResourceAdapter.Snapshot(2,2,-1,2,"another.private-label"));
        var registry=new CacheResourceRegistry(catalog,identity,List.of(resource),ownership);
        Users.as(actor(),()->{assertThat(registry.inspect("shop.resource").state()).isEqualTo("UNAVAILABLE");return null;});
        Users.as(new SmaPrincipal(101,"viewer",98101,"admin",null,false,DataAccess.unrestricted(),Set.of(),"UTC"),()->{
            assertThatThrownBy(()->registry.inspect("shop.resource")).isInstanceOf(AccessDeniedException.class);return null;
        });
    }
    @Test void operationsAreDisabledByDefaultAndCannotChangeProcessOrRunInsideATransaction(){
        var descriptor=new CacheDescriptor("shop.runtime","shop","shop.cache.runtime","runtime","read",CacheDescriptor.Kind.RUNTIME,CacheDescriptor.Scope.TENANT,1,null,Set.of(CacheDescriptor.Operation.CLEAR),Set.of("owner-lifecycle"),"shop:cache:manage");
        var declared=new CacheRegistry(List.of(()->List.of(descriptor)));var adapter=adapter("shop.runtime");
        when(adapter.inspect()).thenReturn(new CacheResourceAdapter.Snapshot(1,10,-1,0,"shop.cache.lifecycle.runtime"));
        var disabled=new CacheResourceRegistry(declared,identity,List.of(adapter),ownership);
        var enabled=new CacheResourceRegistry(declared,identity,List.of(adapter),ownership,new CacheResourceTaskProperties(true));
        var actor=new SmaPrincipal(101,"operator",98101,"admin",null,false,DataAccess.unrestricted(),Set.of("monitor:cache:view","monitor:cache:clear","shop:cache:manage"),"UTC");
        Users.as(actor,()->{
            assertThat(disabled.capability("shop.runtime",CacheDescriptor.Operation.CLEAR).reason()).isEqualTo("DISABLED");
            assertThat(enabled.capability("shop.runtime",CacheDescriptor.Operation.CLEAR).available()).isTrue();
            assertThat(enabled.capability("shop.runtime",CacheDescriptor.Operation.PAUSE).reason()).isEqualTo("UNSUPPORTED");
            var wrong=new CacheTasks.ResourceTarget(UUID.randomUUID().toString(),"other",1);
            assertThatThrownBy(()->enabled.execute("shop.runtime",CacheDescriptor.Operation.CLEAR,wrong)).isInstanceOf(IllegalStateException.class);
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
            try{assertThatThrownBy(()->enabled.execute("shop.runtime",CacheDescriptor.Operation.CLEAR,enabled.target("shop.runtime"))).isInstanceOf(IllegalStateException.class);}
            finally{org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);}
            return null;
        });verify(adapter,never()).execute(any());
    }
    private CacheDescriptor region(String id,CacheDescriptor.Kind kind){return new CacheDescriptor(id,"shop","shop.cache.objects","runtime","read",kind,CacheDescriptor.Scope.TENANT,1,null,Set.of(),Set.of("owner-lifecycle"),"shop:cache:manage");}
    @Test void legacyReadOnlyAdapterAndPersistedDataPayloadRemainCompatibleWithoutEnablingEffects(){
        var legacy=new CacheResourceAdapter(){
            public String region(){return "shop.runtime";}
            public Snapshot inspect(){return new Snapshot(1,10,-1,0,"shop.cache.lifecycle.runtime");}
        };
        assertThat(legacy.operationVersion()).isEqualTo(1);
        assertThatThrownBy(()->legacy.authorize(CacheDescriptor.Operation.CLEAR)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->legacy.execute(CacheDescriptor.Operation.CLEAR)).isInstanceOf(RuntimeException.class);
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        var target=json.readValue("""
                {"region":"shop.items","nameKey":"shop.cache.items","scope":"TENANT","managePermission":"shop:cache:manage","controlVersion":0,"registrationHash":"old"}
                """,CacheTasks.Target.class);
        assertThat(target.resource()).isNull();assertThat(target.warmup()).isNull();
        var item=json.readValue("""
                {"region":"shop.items","state":"APPLIED","dataRevision":1,"controlVersion":0,"failureCode":""}
                """,CacheTasks.Item.class);
        assertThat(item.resource()).isNull();assertThat(item.warmup()).isNull();
    }
    private CacheResourceAdapter adapter(String region){var adapter=mock(CacheResourceAdapter.class);when(adapter.region()).thenReturn(region);when(adapter.operationVersion()).thenReturn(1);return adapter;}
    private SmaPrincipal actor(){return new SmaPrincipal(101,"viewer",98101,"admin",null,false,DataAccess.unrestricted(),Set.of("monitor:cache:view"),"UTC");}
}
