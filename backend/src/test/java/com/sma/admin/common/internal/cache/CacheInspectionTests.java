package com.sma.admin.common.internal.cache;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.cache.*;

class CacheInspectionTests {
    @org.junit.jupiter.api.Test void unavailableAuthorityIsNotReportedAsAnUnpausedDeploymentPolicy(){
        var descriptor=new CacheDescriptor("config.test","config","config.cache.test","configuration","test",
                CacheDescriptor.Kind.DATA,CacheDescriptor.Scope.TENANT,1,CachePolicy.defaults(),Set.of(CacheDescriptor.Operation.CLEAR),Set.of("save"),"monitor:cache:clear");
        var cache=mock(DurableManagedCache.class);
        when(cache.controlState("config.test")).thenThrow(new CacheUnavailableException());
        when(cache.snapshot("config.test")).thenReturn(new CacheSnapshot("config.test","local",-1,-1,0,0,0,0));
        var result=new CacheInspectionService(new CacheRegistry(List.of(()->List.of(descriptor))),
                new CacheProperties(null,null,null,null,null,null,null),cache).inspect("config.test");
        assertThat(result.control().confirmed()).isFalse();assertThat(result.control().paused()).isNull();
        assertThat(result.policy().effective()).isNull();assertThat(result.policy().sources().values()).containsOnly("UNAVAILABLE");
        assertThat(result.providerByteLimit()).isEqualTo(-1);
    }
    @Test void reportsFieldLevelPolicySourcesActualProviderBudgetsAndNoConnectionDetails() {
        var descriptor=new CacheDescriptor("config.test","config","config.cache.test","configuration","test",
                CacheDescriptor.Kind.DATA,CacheDescriptor.Scope.TENANT,1,CachePolicy.defaults(),Set.of(CacheDescriptor.Operation.CLEAR),Set.of("save"),"monitor:cache:clear");
        var registry=new CacheRegistry(List.of(()->List.of(descriptor)));
        var properties=new CacheProperties(CacheProperties.Provider.REDIS,"private-namespace",null,1024L*1024,null,null,
                Map.of("config.test",new CacheProperties.RegionOverride(Duration.ofSeconds(60),null,null,null,null)),
                new CacheProperties.RegionOverride(Duration.ofSeconds(30),Duration.ofSeconds(5),null,null,null));
        var cache=mock(ManagedCache.class);
        when(cache.snapshot("config.test")).thenReturn(new CacheSnapshot("config.test","redis",-1,-1,1,2,3,4));
        var service=new CacheInspectionService(registry,properties,cache);
        var result=service.inspect("config.test");
        assertThat(result.policy().sources()).containsEntry("ttl","DEPLOYMENT_REGION")
                .containsEntry("negativeTtl","DEPLOYMENT_DEFAULT").containsEntry("maxEntries","DECLARED");
        assertThat(result.policy().effective().ttl()).isEqualTo(Duration.ofSeconds(60));
        assertThat(result.providerByteLimit()).isEqualTo(1024L*1024);
        assertThat(result.providerByteLimitScope()).isEqualTo("SHARED_TENANT_REGION");
        assertThat(result.counterScope()).isEqualTo("PROCESS_OBSERVATION_LIFETIME");
        assertThat(result.snapshot().size()).isEqualTo(-1);
        assertThat(service.inspect("config.test").processId()).isEqualTo(result.processId());
        assertThat(result.toString()).doesNotContain("private-namespace","password","127.0.0.1");
        assertThatThrownBy(()->result.policy().sources().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(()->service.inspect("missing.region")).isInstanceOf(IllegalArgumentException.class);
    }
}
