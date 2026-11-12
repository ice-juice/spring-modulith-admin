package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.*;
import org.springframework.boot.health.actuate.endpoint.*;
import org.springframework.boot.health.contributor.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.Ordered;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import com.sma.admin.common.*;
import com.sma.admin.common.internal.cache.*;

class DeploymentTrafficTests {
    private final ApplicationAvailability availability = mock(ApplicationAvailability.class);
    private final DeploymentEvidenceRuntime evidence = mock(DeploymentEvidenceRuntime.class);
    private final SharedStorageRuntime storage = mock(SharedStorageRuntime.class);
    private final ApplicationContext context = mock(ApplicationContext.class);
    private final DeploymentWorkCoordinator work = new DeploymentWorkCoordinator(context, () -> true);
    private final DeploymentTrafficGate gate = new DeploymentTrafficGate(availability, evidence, storage, context, work);
    private static final ErrorResponseWriter ERRORS = (request,response,error) -> response.setStatus(error.status());

    private void ready() {
        when(availability.getLivenessState()).thenReturn(LivenessState.CORRECT);
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        when(evidence.status()).thenReturn(new DeploymentEvidenceRuntime.Status("MATCH"));
        when(storage.status()).thenReturn(new SharedStorageRuntime.Status("READY"));
    }

    @Test void readinessAndEvidenceFailuresRejectAndRecoveryRequiresEveryCondition() {
        assertThat(gate.status().accepts()).isFalse(); verifyNoInteractions(evidence,storage);
        ready(); assertThat(gate.status().accepts()).isTrue();
        when(evidence.status()).thenReturn(new DeploymentEvidenceRuntime.Status("STALE"));
        assertThat(gate.status().state()).isEqualTo("WAITING_CONFIGURATION");
        ready(); when(storage.status()).thenReturn(new SharedStorageRuntime.Status("WAITING_LEASE"));
        assertThat(gate.status().state()).isEqualTo("WAITING_STORAGE");
        ready(); when(availability.getLivenessState()).thenReturn(LivenessState.BROKEN);
        assertThat(gate.status().accepts()).isFalse();
        ready(); assertThat(gate.status().accepts()).isTrue();
    }

    @Test void observationExceptionsAreClosedAndRedacted() {
        ready(); when(storage.status()).thenThrow(new IllegalStateException("private-location"));
        assertThat(gate.status().state()).isEqualTo("UNAVAILABLE");
        assertThat(gate.status().toString()).doesNotContain("private-location");
    }

    @Test void onlyOwnContextClosePermanentlyStopsNewAdmission() {
        ready(); gate.onApplicationEvent(new ContextClosedEvent(mock(ApplicationContext.class)));
        assertThat(gate.status().accepts()).isTrue();
        gate.onApplicationEvent(new ContextClosedEvent(context)); ready();
        assertThat(gate.status().state()).isEqualTo("DRAINING");
    }

    @Test void lifecycleObservabilityClassProxyPreservesGateState() {
        ready();
        var factory=new org.springframework.aop.framework.ProxyFactory(gate);
        factory.setProxyTargetClass(true);
        factory.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> invocation.proceed());
        var proxied=(DeploymentTrafficGate)factory.getProxy();
        assertThat(proxied.status().accepts()).isTrue();
        proxied.onApplicationEvent(new ContextClosedEvent(context));
        assertThat(proxied.status().state()).isEqualTo("DRAINING");
        assertThat(gate.status().state()).isEqualTo("DRAINING");
    }

    @Test void rejectedRequestsDoNotReadBodiesOrReachTenantAuthOrBusiness() throws Exception {
        var filter = new DeploymentTrafficFilter(gate, ERRORS);
        for (String path : List.of("/api/auth/login", "/api/message/socket", "/app/identity/sessions", "/open/login", "/error", "/actuator/info")) {
            var request = spy(new MockHttpServletRequest("POST",path));
            request.addHeader("Authorization","Bearer private-token"); request.addHeader("Upgrade","websocket");
            var response = new MockHttpServletResponse(); var calls = new AtomicInteger();
            filter.doFilter(request,response,(a,b)->calls.incrementAndGet());
            assertThat(response.getStatus()).isEqualTo(503); assertThat(calls.get()).isZero();
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            assertThat(response.getHeader("Retry-After")).isNull();
            verify(request,never()).getInputStream(); verify(request,never()).getReader();
        }
    }

    @Test void onlyExactReadOnlyProbePathsBypassIncludingWithContextPath() throws Exception {
        var filter = new DeploymentTrafficFilter(gate, ERRORS);
        for (String method : List.of("GET","HEAD","POST","OPTIONS"))
            for (String path : List.of("/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness",
                    "/actuator/health/", "/actuator/health/db", "/actuator/health/readiness/extra", "/actuator/health;foo")) {
                var request = new MockHttpServletRequest(method,"/base"+path); request.setContextPath("/base");
                var response = new MockHttpServletResponse(); var calls = new AtomicInteger();
                filter.doFilter(request,response,(a,b)->calls.incrementAndGet());
                boolean allowed = Set.of("GET","HEAD").contains(method)
                        && Set.of("/actuator/health","/actuator/health/readiness","/actuator/health/liveness").contains(path);
                assertThat(calls.get()).as("%s %s",method,path).isEqualTo(allowed?1:0);
                assertThat(response.getStatus()).isEqualTo(allowed?200:503);
            }
    }

    @Test void admittedWorkFinishesOnceEvenIfGateClosesDuringExecution() throws Exception {
        ready(); var calls = new AtomicInteger(); var response = new MockHttpServletResponse();
        new DeploymentTrafficFilter(gate,ERRORS).doFilter(new MockHttpServletRequest("POST","/api/write"),response,(a,b)-> {
            gate.onApplicationEvent(new ContextClosedEvent(context)); calls.incrementAndGet(); b.getWriter().write("accepted");
        });
        assertThat(calls.get()).isOne(); assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("accepted"); assertThat(gate.status().accepts()).isFalse();
    }

    @Test void optInValidatesDependenciesAndRegistersAheadOfTenantFilter() {
        var runner = new ApplicationContextRunner().withUserConfiguration(DeploymentTrafficConfiguration.class, DeploymentWorkConfiguration.class)
                .withBean(ApplicationAvailability.class,()->availability).withBean(DeploymentEvidenceRuntime.class,()->evidence)
                .withBean(SharedStorageRuntime.class,()->storage).withBean(ErrorResponseWriter.class,()->ERRORS)
                .withBean(CacheNodeIdentity.class,()->new CacheNodeIdentity(new CacheNodeProperties("traffic-test","a",List.of("a","b"),true)))
                .withBean(SchemaValidationConfiguration.Properties.class,()->new SchemaValidationConfiguration.Properties(SchemaValidationConfiguration.Properties.SchemaMode.VALIDATE))
                .withBean(HealthEndpointGroups.class,DeploymentTrafficTests::groups)
                .withPropertyValues(probeProperties());
        runner.run(c->assertThat(c).doesNotHaveBean(DeploymentTrafficGate.class));
        var enabled=runner.withPropertyValues("sma.deployment.traffic.enabled=true","sma.deployment.evidence.enabled=true","sma.deployment.storage.enabled=true");
        enabled.run(c->{
            assertThat(c).hasNotFailed().hasSingleBean(DeploymentTrafficGate.class);
            var filter=c.getBean("deploymentTrafficFilter",org.springframework.boot.web.servlet.FilterRegistrationBean.class);
            assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE+10);
            assertThat(c.getBean("deploymentTraffic",HealthIndicator.class).health().getStatus()).isEqualTo(Status.DOWN);
        });
        for(String invalid:List.of("spring.main.lazy-initialization=true","sma.deployment.storage.enabled=false","sma.deployment.evidence.enabled=false",
                "management.endpoint.health.probes.enabled=false","management.server.port=9999"))
            enabled.withPropertyValues(invalid).run(c->assertThat(c).hasFailed());
        new ApplicationContextRunner().withUserConfiguration(DeploymentTrafficConfiguration.class)
                .withPropertyValues("sma.deployment.traffic.enabled=true").run(c->assertThat(c).hasFailed());
    }

    @Test void probePolicyRejectsMissingMembersLivenessDependenciesDisclosureAndRewrittenEndpoints() {
        DeploymentTrafficHealthPolicy.validate(environment(),groups());
        for(String invalid:List.of("management.endpoint.health.group.readiness.include=readinessState",
                "management.endpoint.health.group.liveness.include=livenessState,db",
                "management.endpoint.health.group.readiness.exclude=deploymentTraffic",
                "management.endpoint.health.show-details=always", "management.endpoint.health.group.readiness.show-components=when-authorized",
                "management.endpoints.web.path-mapping.health=status", "management.endpoints.web.base-path=/private",
                "spring.mvc.servlet.path=/backend", "management.endpoints.web.exposure.exclude=health",
                "management.endpoint.health.access=none")) {
            String[] pair=invalid.split("=",2);
            assertThatThrownBy(()->DeploymentTrafficHealthPolicy.validate(environment().withProperty(pair[0],pair[1]),groups()))
                    .as(invalid).isInstanceOf(IllegalArgumentException.class);
        }
        var wrong=groups(); when(wrong.get("readiness").getHttpCodeStatusMapper()).thenReturn(status->200);
        assertThatThrownBy(()->DeploymentTrafficHealthPolicy.validate(environment(),wrong)).isInstanceOf(IllegalArgumentException.class);
        var wrongRoot=groups();when(wrongRoot.getPrimary().getHttpCodeStatusMapper()).thenReturn(status->200);
        assertThatThrownBy(()->DeploymentTrafficHealthPolicy.validate(environment(),wrongRoot)).isInstanceOf(IllegalArgumentException.class);
        var wrongOrder=groups();when(wrongOrder.get("readiness").getStatusAggregator()).thenReturn(statuses->Status.UP);
        assertThatThrownBy(()->DeploymentTrafficHealthPolicy.validate(environment(),wrongOrder)).isInstanceOf(IllegalArgumentException.class);
        var exposedRoot=environment().withProperty("management.endpoint.health.show-details","always")
                .withProperty("management.endpoint.health.group.readiness.show-details","never")
                .withProperty("management.endpoint.health.group.liveness.show-details","never");
        assertThatThrownBy(()->DeploymentTrafficHealthPolicy.validate(exposedRoot,groups())).isInstanceOf(IllegalArgumentException.class);
    }

    static String[] probeProperties() {return new String[]{"management.endpoint.health.probes.enabled=true",
            "management.endpoints.web.exposure.include=health,info",
            "management.endpoint.health.group.readiness.include=readinessState,deploymentTraffic,deploymentEvidence,sharedStorage",
            "management.endpoint.health.group.liveness.include=livenessState"};}
    private static MockEnvironment environment() {
        var env=new MockEnvironment();for(String property:probeProperties()){String[] pair=property.split("=",2);env.withProperty(pair[0],pair[1]);}return env;
    }
    private static HealthEndpointGroups groups() {
        var groups=mock(HealthEndpointGroups.class);when(groups.getNames()).thenReturn(Set.of("readiness","liveness"));
        var primary=mock(HealthEndpointGroup.class);when(groups.getPrimary()).thenReturn(primary);
        when(primary.getHttpCodeStatusMapper()).thenReturn(HttpCodeStatusMapper.getDefault());
        when(primary.getStatusAggregator()).thenReturn(StatusAggregator.getDefault());
        for(String name:List.of("readiness","liveness")){
            var group=mock(HealthEndpointGroup.class);when(groups.get(name)).thenReturn(group);
            var members=name.equals("readiness")?Set.of("readinessState","deploymentTraffic","deploymentEvidence","sharedStorage"):Set.of("livenessState");
            when(group.isMember(anyString())).thenAnswer(call->members.contains(call.getArgument(0)));
            when(group.getHttpCodeStatusMapper()).thenReturn(HttpCodeStatusMapper.getDefault());
            when(group.getStatusAggregator()).thenReturn(StatusAggregator.getDefault());
        }return groups;
    }
}
