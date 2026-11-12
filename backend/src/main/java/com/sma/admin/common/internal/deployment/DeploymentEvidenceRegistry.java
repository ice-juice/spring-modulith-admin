package com.sma.admin.common.internal.deployment;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.aop.support.AopUtils;
import org.springframework.modulith.ApplicationModule;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import com.sma.admin.common.deployment.DeploymentEvidence;
import com.sma.admin.common.deployment.DeploymentEvidenceSource;

/** Collection only. Persistent lease binding and readiness are separate M3b2c/M3c gates. */
@Component
public class DeploymentEvidenceRegistry {
    private static final Set<String> REQUIRED = Set.of("auth.security-keys", "common.request-fingerprint",
            "common.tenant-credentials", "openapi.master-key", "common.host-policy", "common.cache-policy",
            "auth.session-policy", "message.socket-policy", "identity.session-policy", "file.storage-policy", "mail.work-policy", "tenant.platform-policy", "defense.storage-policy");
    private final Map<String, DeploymentEvidenceSource> sources;

    public DeploymentEvidenceRegistry(List<DeploymentEvidenceSource> providers) {
        if (providers.size() > 64) throw new IllegalArgumentException("Too many deployment evidence sources");
        var indexed = new TreeMap<String, DeploymentEvidenceSource>();
        for (var provider : providers) {
            String id = provider.id();
            new DeploymentEvidence.Proof(id, null);
            validateOwner(provider, id);
            if (indexed.putIfAbsent(id, provider) != null) throw new IllegalArgumentException("Duplicate deployment evidence source");
        }
        sources = Map.copyOf(indexed);
    }

    public record Snapshot(String digest, Set<String> unavailable) {
        public Snapshot { unavailable = Set.copyOf(unavailable); }
        public boolean ready() { return digest != null && unavailable.isEmpty(); }
        @Override public String toString() { return "DeploymentEvidenceSnapshot[ready=" + ready() + "]"; }
    }

    public Snapshot collect(DeploymentEvidence.Challenge challenge) {
        DeploymentEvidence.requireHost();
        var unavailable = new TreeSet<>(REQUIRED); unavailable.removeAll(sources.keySet());
        var proofs = new java.util.ArrayList<DeploymentEvidence.Proof>();
        for (var entry : new TreeMap<>(sources).entrySet()) {
            try {
                var proof = entry.getValue().collect(challenge);
                if (proof == null || !proof.domain().equals(entry.getKey()) || !proof.available()) unavailable.add(entry.getKey());
                else proofs.add(proof);
            } catch (RuntimeException failure) { unavailable.add(entry.getKey()); } // No exception text, raw value or secret in observations.
        }
        return new Snapshot(unavailable.isEmpty()
                ? DeploymentEvidence.combine("common.deployment-manifest", challenge, proofs).digest() : null, unavailable);
    }

    private static void validateOwner(Object provider, String id) {
        var type = ClassUtils.getUserClass(AopUtils.getTargetClass(provider));
        var match = java.util.regex.Pattern.compile("^(com\\.sma\\.(admin|business))\\.([a-z]+)(?:\\..*)?$").matcher(type.getPackageName());
        if (!match.matches() || !id.startsWith(match.group(3) + "."))
            throw new IllegalArgumentException("Deployment evidence source must belong to its owning module");
        try {
            if (Class.forName(match.group(1) + "." + match.group(3) + ".package-info", false, type.getClassLoader())
                    .getAnnotation(ApplicationModule.class) == null) throw new IllegalArgumentException("Deployment module is not declared");
        } catch (ClassNotFoundException missing) { throw new IllegalArgumentException("Deployment module is not declared"); }
    }
}
