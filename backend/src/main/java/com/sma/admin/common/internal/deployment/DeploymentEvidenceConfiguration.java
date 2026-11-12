package com.sma.admin.common.internal.deployment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import com.sma.admin.common.internal.cache.CacheNodeIdentity;
import com.sma.admin.common.internal.cache.CacheNodeRuntime;
import com.sma.admin.common.internal.service.DeploymentEvidenceStore;

/** Opt-in for staged single-mode cluster verification; full multi/readiness qualification remains M3c/d. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="sma.deployment.evidence.enabled",havingValue="true")
class DeploymentEvidenceConfiguration {
    @Bean DeploymentEvidenceRuntime deploymentEvidenceRuntime(CacheNodeIdentity identity,CacheNodeRuntime membership,
            DeploymentEvidenceStore store,DeploymentEvidenceRegistry registry,SchemaValidationConfiguration.Properties schema,Environment environment){
        if(!identity.properties().enabled()||identity.properties().expected().size()<2
                ||schema.schemaMode()!=SchemaValidationConfiguration.Properties.SchemaMode.VALIDATE
                ||environment.getProperty("spring.main.lazy-initialization",Boolean.class,false))
            throw new IllegalArgumentException("Deployment evidence requires enabled node leases, a multi-node manifest and eager schema validation");
        return new DeploymentEvidenceRuntime(identity,membership,store,registry);
    }
    @Bean(name="deploymentEvidence") HealthIndicator deploymentEvidenceHealth(DeploymentEvidenceRuntime runtime){
        return ()->{var state=runtime.status();return (state.matches()?Health.up():Health.down()).withDetail("state",state.state()).build();};
    }
}
