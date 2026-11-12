package com.sma.admin.common.internal.deployment;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DeploymentTrafficProperties.class)
class DeploymentWorkConfiguration {
    @Bean DeploymentWorkCoordinator deploymentWorkCoordinator(ApplicationContext context, ApplicationAvailability availability,
            DeploymentTrafficProperties settings, ObjectProvider<DeploymentTrafficGate> gates) {
        return new DeploymentWorkCoordinator(context, () -> {
            if (availability.getLivenessState() != LivenessState.CORRECT
                    || availability.getReadinessState() != ReadinessState.ACCEPTING_TRAFFIC) return false;
            if (!settings.enabled()) return true;
            var gate = gates.getIfAvailable();
            return gate != null && gate.status().accepts();
        });
    }
}
