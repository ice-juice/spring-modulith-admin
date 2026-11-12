package com.sma.admin.common.internal.deployment;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import com.sma.admin.common.ErrorResponseWriter;
import com.sma.admin.common.internal.cache.CacheNodeIdentity;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sma.deployment.traffic.enabled", havingValue = "true")
class DeploymentTrafficConfiguration {
    @Bean DeploymentTrafficGate deploymentTrafficGate(ApplicationAvailability availability, DeploymentEvidenceRuntime evidence,
            SharedStorageRuntime storage, ApplicationContext context, CacheNodeIdentity identity,
            SchemaValidationConfiguration.Properties schema, Environment environment, DeploymentWorkCoordinator work) {
        if (!identity.properties().enabled() || identity.properties().expected().size() < 2
                || schema.schemaMode() != SchemaValidationConfiguration.Properties.SchemaMode.VALIDATE
                || environment.getProperty("spring.main.lazy-initialization", Boolean.class, false)
                || !environment.getProperty("sma.deployment.evidence.enabled", Boolean.class, false)
                || !environment.getProperty("sma.deployment.storage.enabled", Boolean.class, false))
            throw new IllegalArgumentException("Traffic admission requires eager schema validation, node leases, configuration and storage evidence");
        return new DeploymentTrafficGate(availability, evidence, storage, context, work);
    }

    @Bean FilterRegistrationBean<DeploymentTrafficFilter> deploymentTrafficFilter(DeploymentTrafficGate gate, ErrorResponseWriter errors) {
        var registration = new FilterRegistrationBean<>(new DeploymentTrafficFilter(gate, errors));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setAsyncSupported(true);
        return registration;
    }

    @Bean(name = "deploymentTraffic") HealthIndicator deploymentTrafficHealth(DeploymentTrafficGate gate) {
        return () -> {
            var state = gate.status();
            return (state.accepts() ? Health.up() : Health.down()).withDetail("state", state.state()).build();
        };
    }

    @Bean SmartInitializingSingleton deploymentTrafficHealthPolicy(Environment environment, ObjectProvider<HealthEndpointGroups> groups) {
        return () -> DeploymentTrafficHealthPolicy.validate(environment, groups.getObject());
    }
}
