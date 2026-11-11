package com.sma.admin.monitor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.common.BizException;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.security.*;
import com.sma.admin.monitor.internal.service.*;
import com.sma.admin.support.Users;

class CacheDirectoryTests {
    private final CacheDescriptor basic=region("config.test","config","monitor:cache:clear",CacheDescriptor.Kind.DATA,Set.of(CacheDescriptor.Operation.CLEAR));
    private final CacheDescriptor business=region("shop.test","shop","shop:cache:manage",CacheDescriptor.Kind.DATA,Set.of(CacheDescriptor.Operation.CLEAR));
    private final CacheDescriptor protectedState=region("auth.sessions","auth","auth:session:view",CacheDescriptor.Kind.PROTECTED,Set.of());
    private final CacheDescriptor readonly=region("shop.readonly","shop","shop:cache:manage",CacheDescriptor.Kind.DATA,Set.of());
    private final CacheRegions regions=()->List.of(basic,business,protectedState,readonly);
    private final ManagedCache cache=mock(ManagedCache.class);
    private final CacheInspection inspection=mock(CacheInspection.class);
    private final StaticMessageSource messages=new StaticMessageSource();
    private final CacheDirectoryService directory=new CacheDirectoryService(regions,inspection,messages);
    private final CacheService legacy=new CacheService(List.of(),messages,regions,cache);
    private final CacheDirectoryService.Filter all=new CacheDirectoryService.Filter(null,null,null,null,null,null,null);

    CacheDirectoryTests(){
        for(var descriptor:List.of(basic,business,readonly))when(inspection.inspect(descriptor.id())).thenReturn(observation(descriptor.id(),"local",1));
    }
    @Test void directoryFiltersAreIntersectedAndOperationsRequireBothPermissions() {
        Users.as(actor("monitor:cache:view","monitor:cache:clear"),()->{
            assertThat(directory.list(all)).hasSize(4);
            assertThat(directory.detail("config.test").allowedOperations()).containsExactly("CLEAR");
            assertThat(directory.detail("shop.test").allowedOperations()).isEmpty();
            assertThat(directory.detail("auth.sessions").state()).isEqualTo("PROTECTED");
            assertThat(directory.detail("auth.sessions").observation()).isNull();
            assertThat(directory.list(new CacheDirectoryService.Filter("shop","group","scenario","DATA","local","ACTIVE","test")))
                    .extracting(CacheDirectoryService.View::id).containsExactly("shop.test");
            assertThat(directory.list(new CacheDirectoryService.Filter("config","other",null,null,null,null,null))).isEmpty();
            assertThatThrownBy(()->directory.detail("missing.region")).isInstanceOf(BizException.class);
            return null;
        });
        verify(inspection,never()).inspect("auth.sessions");
    }
    @Test void unauthorizedAndProtectedOldMutationsCannotBypassGovernance() {
        Users.as(actor("monitor:cache:clear"),()->{
            assertThatThrownBy(()->directory.list(all)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->legacy.clear("shop.test")).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->legacy.clear("auth.sessions")).isInstanceOf(BizException.class);
            assertThatThrownBy(()->legacy.clear("missing.region")).isInstanceOf(BizException.class);
            legacy.clearAll(); return null;
        });
        verify(cache).invalidate("config.test");verify(cache,never()).invalidate("shop.test");
        Users.as(actor("shop:cache:manage"),()->{
            assertThatThrownBy(()->legacy.clear("shop.test")).isInstanceOf(AccessDeniedException.class);return null;
        });
        Users.as(actor("monitor:cache:clear","shop:cache:manage"),()->{
            legacy.clear("shop.test");
            assertThatThrownBy(()->legacy.clear("shop.readonly")).isInstanceOf(AccessDeniedException.class);return null;
        });
        verify(cache).invalidate("shop.test");verify(cache,never()).invalidate("shop.readonly");
    }
    @Test void disabledAndUnknownCountsRemainDistinctAndMetadataCannotBeMutated() {
        when(inspection.inspect("config.test")).thenReturn(observation("config.test","redis",-1));
        when(inspection.inspect("shop.test")).thenReturn(observation("shop.test","none",0));
        Users.as(actor("monitor:cache:view"),()->{
            assertThat(directory.detail("config.test").state()).isEqualTo("UNAVAILABLE");
            assertThat(directory.detail("shop.test").state()).isEqualTo("DISABLED");
            assertThat(directory.detail("config.test").observation().snapshot().size()).isEqualTo(-1);
            assertThatThrownBy(()->directory.detail("config.test").allowedOperations().add("CLEAR")).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(()->directory.list(new CacheDirectoryService.Filter(null,null,null,null,null,null,"a".repeat(161))))
                    .isInstanceOf(BizException.class);return null;
        });
    }
    @Test void confirmedPauseIsVisibleEvenWithNoneAndUnconfirmedControlIsNotShownAsActive(){
        var old=observation("config.test","none",0);
        when(inspection.inspect("config.test")).thenReturn(new CacheInspection.Observation(old.provider(),old.processId(),old.sampledAt(),old.entryScope(),old.counterScope(),
                old.byteMeasurement(),old.providerByteLimit(),old.providerByteLimitScope(),old.policy(),old.snapshot(),new CacheInspection.Control(1,1,0,true,null,true)));
        Users.as(actor("monitor:cache:view"),()->{assertThat(directory.detail("config.test").state()).isEqualTo("PAUSED");return null;});
        when(inspection.inspect("config.test")).thenReturn(new CacheInspection.Observation(old.provider(),old.processId(),old.sampledAt(),old.entryScope(),old.counterScope(),
                old.byteMeasurement(),old.providerByteLimit(),old.providerByteLimitScope(),old.policy(),old.snapshot(),new CacheInspection.Control(-1,-1,-1,null,null,false)));
        Users.as(actor("monitor:cache:view"),()->{assertThat(directory.detail("config.test").state()).isEqualTo("UNAVAILABLE");return null;});
    }
    private CacheDescriptor region(String id,String owner,String permission,CacheDescriptor.Kind kind,Set<CacheDescriptor.Operation> operations){
        return new CacheDescriptor(id,owner,owner+".cache.test","group","scenario",kind,CacheDescriptor.Scope.TENANT,1,
                kind==CacheDescriptor.Kind.DATA?CachePolicy.defaults():null,operations,Set.of("save"),permission);
    }
    private CacheInspection.Observation observation(String id,String provider,long size){
        return new CacheInspection.Observation(provider,"process",Instant.now(),"PROCESS_TENANT_REGION","PROCESS_OBSERVATION_LIFETIME",
                "SERIALIZED_PAYLOAD",1024,"PROCESS_ALL_REGIONS",new CacheInspection.Policy(CachePolicy.defaults(),CachePolicy.defaults(),Map.of()),
                new CacheSnapshot(id,provider,size,0,0,0,0,0));
    }
    private SmaPrincipal actor(String... permissions){return new SmaPrincipal(101,"test",9200,"admin",null,false,DataAccess.none(),Set.of(permissions),"UTC");}
}
