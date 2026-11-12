package com.sma.admin.common.internal.deployment;

import java.util.Set;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.contributor.Status;
import org.springframework.core.env.Environment;

/** Fixed probe contract for the opt-in gate. Check effective aggregation and HTTP status mapping too. */
final class DeploymentTrafficHealthPolicy {
    private static final Set<String> READY = Set.of("readinessState", "deploymentTraffic", "deploymentEvidence", "sharedStorage");
    private static final Set<String> LIVE = Set.of("livenessState");
    private DeploymentTrafficHealthPolicy() {}

    static void validate(Environment env, HealthEndpointGroups groups) {
        require(env.getProperty("management.endpoint.health.probes.enabled", Boolean.class, false));
        require(env.getProperty("management.server.port", Integer.class, env.getProperty("server.port", Integer.class, 8080))
                .equals(env.getProperty("server.port", Integer.class, 8080)));
        require(env.getProperty("management.endpoints.web.base-path", "/actuator").equals("/actuator"));
        require(env.getProperty("management.endpoints.web.path-mapping.health", "health").equals("health"));
        require(env.getProperty("spring.mvc.servlet.path", "/").equals("/"));
        require(!env.getProperty("management.endpoint.health.access", "unrestricted").equalsIgnoreCase("none"));
        for (String disclosure : Set.of("show-details", "show-components"))
            require(env.getProperty("management.endpoint.health." + disclosure, "never").equalsIgnoreCase("never"));
        var exposed = values(env, "management.endpoints.web.exposure.include");
        var excluded = values(env, "management.endpoints.web.exposure.exclude");
        require((exposed.contains("health") || exposed.contains("*")) && !excluded.contains("health") && !excluded.contains("*"));
        require(groups.getNames().equals(Set.of("readiness", "liveness")));
        validateStatus(groups.getPrimary());
        validateGroup(env, groups.get("readiness"), "readiness", READY);
        validateGroup(env, groups.get("liveness"), "liveness", LIVE);
    }

    private static void validateGroup(Environment env, HealthEndpointGroup group, String name, Set<String> members) {
        String prefix = "management.endpoint.health.group." + name + ".";
        require(group != null && values(env, prefix + "include").equals(members) && values(env, prefix + "exclude").isEmpty());
        for (String member : Set.of("readinessState", "livenessState", "deploymentTraffic", "deploymentEvidence", "sharedStorage", "db", "diskSpace"))
            require(group.isMember(member) == members.contains(member));
        require(group.getAdditionalPath() == null);
        for (String disclosure : Set.of("show-details", "show-components"))
            require(env.getProperty(prefix + disclosure, env.getProperty("management.endpoint.health." + disclosure, "never")).equalsIgnoreCase("never"));
        validateStatus(group);
    }

    private static void validateStatus(HealthEndpointGroup group) {
        require(group != null);
        require(group.getHttpCodeStatusMapper().getStatusCode(Status.UP) == 200
                && group.getHttpCodeStatusMapper().getStatusCode(Status.DOWN) == 503
                && group.getHttpCodeStatusMapper().getStatusCode(Status.OUT_OF_SERVICE) == 503);
        require(group.getStatusAggregator().getAggregateStatus(Set.of(Status.UP, Status.DOWN)).equals(Status.DOWN));
        require(group.getStatusAggregator().getAggregateStatus(Set.of(Status.UP, Status.OUT_OF_SERVICE)).equals(Status.OUT_OF_SERVICE));
        require(group.getStatusAggregator().getAggregateStatus(Set.of(Status.UP, Status.DOWN, Status.OUT_OF_SERVICE)).equals(Status.DOWN));
    }

    private static Set<String> values(Environment env, String name) {
        return Binder.get(env).bind(name, Bindable.setOf(String.class)).orElse(Set.of());
    }
    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Traffic admission requires fixed, private readiness/liveness probes on the application port with failure HTTP 503");
    }
}
