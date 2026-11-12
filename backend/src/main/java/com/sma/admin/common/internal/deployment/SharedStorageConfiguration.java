package com.sma.admin.common.internal.deployment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.sma.admin.common.internal.cache.CacheNodeIdentity;
import com.sma.admin.common.internal.cache.CacheNodeRuntime;
import com.sma.admin.common.internal.service.SharedStorageStore;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="sma.deployment.storage.enabled",havingValue="true")
class SharedStorageConfiguration {
    @Bean SharedStorageRuntime sharedStorageRuntime(CacheNodeIdentity identity,CacheNodeRuntime membership,DeploymentEvidenceRuntime evidence,
            SharedStorageStore store,SharedStorageRegistry registry){return new SharedStorageRuntime(identity,membership,evidence,store,registry);}
    @Bean(name="sharedStorage") HealthIndicator sharedStorageHealth(SharedStorageRuntime runtime){
        return ()->{var status=runtime.status();return (status.ready()?Health.up():Health.down()).withDetail("state",status.state()).build();};
    }
}
