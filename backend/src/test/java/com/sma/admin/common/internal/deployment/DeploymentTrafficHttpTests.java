package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.sma.admin.support.IntegrationTest;

/** Real servlet listener and security chain; evidence failures are injected (not a two-JVM proof). */
@IntegrationTest
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties={"server.address=127.0.0.1", "sma.deployment.schema-mode=validate",
        "sma.deployment.evidence.enabled=true", "sma.deployment.storage.enabled=true", "sma.deployment.traffic.enabled=true",
        "sma.cache.nodes.enabled=true", "sma.cache.nodes.cluster=p08-traffic-http", "sma.cache.nodes.id=a", "sma.cache.nodes.expected=a,b",
        "management.endpoint.health.probes.enabled=true",
        "management.endpoint.health.group.readiness.include=readinessState,deploymentTraffic,deploymentEvidence,sharedStorage",
        "management.endpoint.health.group.liveness.include=livenessState"})
@TestExecutionListeners(listeners=DeploymentEvidenceRuntimeHttpTests.PrepareSchema.class,mergeMode=TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class DeploymentTrafficHttpTests {
    @LocalServerPort int port;
    @Autowired ConfigurableApplicationContext context;
    @Autowired DeploymentTrafficGate gate;
    @Autowired DeploymentWorkCoordinator work;
    @MockitoBean DeploymentEvidenceRuntime evidence;
    @MockitoBean SharedStorageRuntime storage;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    private HttpResponse<String> request(String method,String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(5))
                .method(method,method.equals("POST")?HttpRequest.BodyPublishers.ofString("not-json"):HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    private void probe(String path,int expected) throws Exception {
        var response=request("GET","/actuator/health/"+path);
        assertThat(response.statusCode()).as(path).isEqualTo(expected);
        assertThat(response.body()).doesNotContain("components","details","private");
    }

    @Test void realHttpSeparatesProbeHealthFromAdmissionAndNeverReopensAfterClose() throws Exception {
        when(evidence.status()).thenReturn(new DeploymentEvidenceRuntime.Status("MATCH"));
        when(storage.status()).thenReturn(new SharedStorageRuntime.Status("READY"));
        assertThat(gate.status().accepts()).isTrue(); probe("readiness",200); probe("liveness",200);
        assertThat(request("GET","/api/auth/captcha").statusCode()).isEqualTo(200);
        // Exercise actual availability events separately from dependency health.
        AvailabilityChangeEvent.publish(context,ReadinessState.REFUSING_TRAFFIC);
        probe("readiness",503);probe("liveness",200);
        assertThat(request("GET","/api/auth/captcha").statusCode()).isEqualTo(503);
        AvailabilityChangeEvent.publish(context,ReadinessState.ACCEPTING_TRAFFIC);
        when(evidence.status()).thenReturn(new DeploymentEvidenceRuntime.Status("MISMATCH"));
        assertThat(work.tryStart()).isEmpty();
        probe("readiness",503);probe("liveness",200);
        for(String path:List.of("/api/auth/captcha","/api/auth/login","/api/message/ws?ticket=unused",
                "/app/identity/sessions","/open/login","/actuator/info","/actuator/health/readiness/extra")) {
            var response=request("POST",path);
            assertThat(response.statusCode()).as(path).isEqualTo(503);
            assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
            assertThat(response.body()).contains("common.error.unavailable").doesNotContain("unused","MISMATCH");
        }
        // A real WebSocket upgrade is denied before ticket consumption/handshake interceptors.
        assertThatThrownBy(()->client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://127.0.0.1:"+port+"/api/message/ws?ticket=unused"),new WebSocket.Listener(){}).join())
                .isInstanceOf(CompletionException.class).satisfies(error ->
                    assertThat(((WebSocketHandshakeException)error.getCause()).getResponse().statusCode()).isEqualTo(503));
        when(evidence.status()).thenReturn(new DeploymentEvidenceRuntime.Status("MATCH"));
        when(storage.status()).thenReturn(new SharedStorageRuntime.Status("STALE"));
        probe("readiness",503);probe("liveness",200);
        when(storage.status()).thenReturn(new SharedStorageRuntime.Status("READY"));
        probe("readiness",200);assertThat(request("GET","/api/auth/captcha").statusCode()).isEqualTo(200);
        assertThat(request("HEAD","/actuator/health/liveness").statusCode()).isEqualTo(200);
        // Real host control changes the same state observed by the actual servlet filter.
        var admitted=work.tryStart().orElseThrow();
        assertThat(work.beginDrain()).isTrue();
        AvailabilityChangeEvent.publish(context,ReadinessState.ACCEPTING_TRAFFIC);
        probe("readiness",503);probe("liveness",200);
        assertThat(request("GET","/api/auth/captcha").statusCode()).isEqualTo(503);
        assertThat(work.tryStart()).isEmpty();assertThat(work.snapshot().activeWork()).isOne();
        admitted.close();assertThat(work.snapshot().activeWork()).isZero();
        gate.onApplicationEvent(new ContextClosedEvent(context));
        assertThat(gate.status().state()).isEqualTo("DRAINING");
    }
}
