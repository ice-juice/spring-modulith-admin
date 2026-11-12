package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import com.sma.admin.common.deployment.DeploymentEvidence;
import com.sma.admin.common.deployment.DeploymentEvidenceSource;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.support.IntegrationTest;

@IntegrationTest
@TestPropertySource(properties = {
        "sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "sma.common.credentials.key-file=src/test/resources/deployment-evidence-test.keys",
        "sma.openapi.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class DeploymentEvidenceAssemblyTests {
    @Autowired DeploymentEvidenceRegistry registry;
    @Autowired ApplicationContext context;
    @Autowired SharedStorageRegistry storage;

    @Test void actualHostIncludesAllCriticalKeySourcesAndDoesNotGrantTenantJobsAccess() throws Exception {
        assertThat(context.getBeansOfType(DeploymentEvidenceSource.class).values().stream().map(DeploymentEvidenceSource::id).toList())
                .containsExactlyInAnyOrder("auth.security-keys", "common.request-fingerprint", "common.tenant-credentials", "openapi.master-key",
                        "common.host-policy", "common.cache-policy", "auth.session-policy", "message.socket-policy",
                        "identity.session-policy", "file.storage-policy", "mail.work-policy", "tenant.platform-policy", "defense.storage-policy");
        // Spring's servlet test listener binds a mock HTTP request to the test thread.
        // Exercise the real host-worker boundary, without clearing or weakening that guard.
        try (var worker = java.util.concurrent.Executors.newSingleThreadExecutor(
                Thread.ofPlatform().inheritInheritableThreadLocals(false).factory())) {
            worker.submit(() -> {
                var challenge = new DeploymentEvidence.Challenge("a".repeat(64), "b".repeat(64), "c".repeat(64));
                var snapshot = registry.collect(challenge);
                assertThat(snapshot.ready()).isTrue(); assertThat(snapshot.unavailable()).isEmpty();
                assertThat(snapshot.digest()).matches("[a-f0-9]{64}").isEqualTo(registry.collect(challenge).digest());
                var reversed = new java.util.ArrayList<>(context.getBeansOfType(DeploymentEvidenceSource.class).values()).reversed();
                assertThat(new DeploymentEvidenceRegistry(reversed).collect(challenge).digest()).isEqualTo(snapshot.digest());
                // Undeclared roots may serve the compatible single-node application, but are never qualified as shared.
                assertThatThrownBy(storage::resources).isInstanceOf(IllegalStateException.class)
                        .hasMessage("Shared storage witness unavailable");
                assertThatThrownBy(() -> ActorContext.withinJob(1, "test:deployment", () -> registry.collect(challenge)))
                        .isInstanceOf(AccessDeniedException.class);
            }).get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
    }
}
