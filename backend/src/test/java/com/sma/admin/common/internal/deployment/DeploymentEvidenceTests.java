package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.sma.admin.common.deployment.DeploymentEvidence;
import com.sma.admin.common.deployment.DeploymentEvidenceSource;
import com.sma.admin.common.security.ActorContext;

class DeploymentEvidenceTests {
    private static final DeploymentEvidence.Challenge CHALLENGE = new DeploymentEvidence.Challenge("a".repeat(64), "b".repeat(64), "c".repeat(64));
    private static byte[] key(int value) { var result = new byte[32]; Arrays.fill(result, (byte)value); return result; }

    @Test void ringProofIsDeterministicButIncludesActiveRetainedMaterialAndDomain() {
        var ordered = new LinkedHashMap<String, byte[]>(); ordered.put("active", key(1)); ordered.put("old", key(2));
        var reversed = new LinkedHashMap<String, byte[]>(); reversed.put("old", key(2)); reversed.put("active", key(1));
        var proof = DeploymentEvidence.keyRing("common.test", CHALLENGE, "active", ordered);
        assertThat(proof).isEqualTo(DeploymentEvidence.keyRing("common.test", CHALLENGE, "active", reversed));
        assertThat(proof).isNotEqualTo(DeploymentEvidence.keyRing("common.test", CHALLENGE, "old", ordered));
        assertThat(proof).isNotEqualTo(DeploymentEvidence.keyRing("common.test", CHALLENGE, "active", Map.of("active", key(1))));
        assertThat(proof).isNotEqualTo(DeploymentEvidence.keyRing("common.test", CHALLENGE, "active", Map.of("active", key(1), "old", key(3))));
        assertThat(proof.digest()).isNotEqualTo(DeploymentEvidence.keyRing("common.other", CHALLENGE, "active", ordered).digest());
        assertThat(proof).isNotEqualTo(DeploymentEvidence.keyRing("common.test", new DeploymentEvidence.Challenge("a".repeat(64), "b".repeat(64), "d".repeat(64)), "active", ordered));
        assertThat(ordered.get("active")).containsExactly(key(1));
        assertThat(proof.toString()).doesNotContain(proof.digest(), Base64.getEncoder().encodeToString(key(1)));
        assertThat(CHALLENGE.toString()).doesNotContain(CHALLENGE.nonce());
    }

    @Test void settingsUseBoundedUnambiguousCanonicalEncodingAndMissingKeysNeverMatch() {
        assertThat(DeploymentEvidence.settings("common.test", CHALLENGE, Map.of("a", "x", "b", "y")))
                .isNotEqualTo(DeploymentEvidence.settings("common.test", CHALLENGE, Map.of("a", "x\nb=y")));
        assertThat(DeploymentEvidence.keyRing("common.test", CHALLENGE, "absent", Map.of()).available()).isFalse();
        assertThatThrownBy(() -> DeploymentEvidence.settings("common.test", CHALLENGE, Map.of("a", "x".repeat(8193)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeploymentEvidence.keyRing("common.test", CHALLENGE, "a", Map.of("a", new byte[31]))).isInstanceOf(IllegalArgumentException.class);
        var tooMany = new HashMap<String,byte[]>(); for (int i=0;i<17;i++) tooMany.put("k"+i,key(i));
        assertThatThrownBy(() -> DeploymentEvidence.keyRing("common.test", CHALLENGE, "k0", tooMany)).isInstanceOf(IllegalArgumentException.class);
        var large = new HashMap<String,String>(); for (int i=0;i<32;i++) large.put("k"+i,"x".repeat(8192));
        assertThatThrownBy(() -> DeploymentEvidence.settings("common.test", CHALLENGE, large)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeploymentEvidence.Challenge("bad", "b".repeat(64), "c".repeat(64))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsAnonymousHttpAuthenticatedJobsAndDatabaseTransactions() {
        Runnable collect = () -> DeploymentEvidence.settings("common.test", CHALLENGE, Map.of());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        try { assertThatThrownBy(collect::run).isInstanceOf(AccessDeniedException.class); }
        finally { RequestContextHolder.resetRequestAttributes(); }
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("test", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        try { assertThatThrownBy(collect::run).isInstanceOf(AccessDeniedException.class); }
        finally { SecurityContextHolder.clearContext(); }
        assertThatThrownBy(() -> ActorContext.withinJob(1,"evidence-test", () -> {collect.run(); return null;})).isInstanceOf(AccessDeniedException.class);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThatThrownBy(collect::run).isInstanceOf(IllegalStateException.class); }
        finally { TransactionSynchronizationManager.clear(); }
    }

    @Test void registryRejectsForgedOwnersDuplicatesWrongDomainsAndSanitizesFailures() {
        assertThatThrownBy(() -> new DeploymentEvidenceRegistry(List.of(new Source("auth.forged", false, false))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeploymentEvidenceRegistry(List.of(new Source("common.example", false, false), new Source("common.example", false, false))))
                .isInstanceOf(IllegalArgumentException.class);
        var registry = new DeploymentEvidenceRegistry(List.of(new Source("common.example", true, false), new Source("common.wrong", false, true)));
        var snapshot = registry.collect(CHALLENGE);
        assertThat(snapshot.ready()).isFalse(); assertThat(snapshot.digest()).isNull();
        assertThat(snapshot.unavailable()).contains("common.example", "common.wrong", "auth.security-keys", "openapi.master-key");
        assertThat(snapshot.toString()).doesNotContain("private-exception-material");
    }

    private record Source(String id, boolean fail, boolean wrong) implements DeploymentEvidenceSource {
        @Override public DeploymentEvidence.Proof collect(DeploymentEvidence.Challenge challenge) {
            if (fail) throw new IllegalStateException("private-exception-material");
            return DeploymentEvidence.settings(wrong ? "common.mismatched" : id, challenge, Map.of());
        }
    }
}
