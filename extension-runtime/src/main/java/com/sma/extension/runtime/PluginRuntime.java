package com.sma.extension.runtime;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.sma.extension.api.*;
import com.sma.extension.protocol.*;
import com.sma.extension.protocol.ExecutionDocuments.*;

/** Management mutations are synchronized; invocation pins an instance under that same lock and runs outside it. */
public final class PluginRuntime implements AutoCloseable {
    private final String issuer, audience, environment, nodeId;
    private final Clock clock;
    private ResourcePolicy policy;
    private volatile Map<String, DocumentSignatures.TrustedKey> trust;
    private final RuntimeHost host;
    private final RuntimeCheckpoint checkpoint;
    private final Map<String, Instance> instances = new HashMap<>();
    private final Map<String, Bundle> retainedBundles = new HashMap<>();
    private final Map<String, Long> monotonicLeaseDeadlines = new HashMap<>();
    private final Map<String, LeaseState> leases = new HashMap<>();
    private final Map<String, Release> active = new HashMap<>();
    private final Set<String> revoked = new HashSet<>();
    private final ThreadLocal<CallScope> scope = new ThreadLocal<>();
    private boolean closed;
    public PluginRuntime(String issuer, String audience, String environment, String nodeId, Clock clock, ResourcePolicy policy,
            Map<String, DocumentSignatures.TrustedKey> trust, RuntimeHost host, RuntimeCheckpoint checkpoint) {
        this.issuer = Checks.id(issuer); this.audience = Checks.id(audience); this.environment = Checks.id(environment); this.nodeId = Checks.id(nodeId);
        this.clock = clock; this.policy = policy; this.trust = Map.copyOf(trust); this.host = host; this.checkpoint = checkpoint;
    }
    public synchronized Status prepare(byte[] jar, SignedEnvelope proofEnvelope, SignedEnvelope releaseEnvelope) throws Exception {
        Checks.require(!closed, "runtime-closed");
        var claims = verify(proofEnvelope, SignedClaims.Purpose.ARTIFACT, Duration.ofHours(24));
        var proof = ProtocolJson.read(claims.body(), ArtifactProof.class);
        Checks.require(proof.policyRevision() == policy.revision() && proof.bytes() == jar.length && Hashes.sha256(jar).equals(proof.sha256()), "artifact-proof-mismatch");
        var releaseClaims = verify(releaseEnvelope, SignedClaims.Purpose.PREPARE, Duration.ofMinutes(15));
        var planned = ProtocolJson.read(releaseClaims.body(), Release.class);
        Checks.require(planned.targetNodes().contains(nodeId) && planned.policyRevision() <= policy.revision()
                && planned.revision() >= checkpoint.revision(planned.deploymentId())
                && planned.bundles().stream().flatMap(b -> b.members().stream()).anyMatch(m -> m.pluginId().equals(proof.pluginId())
                        && m.artifactId().equals(proof.artifactId()) && m.sha256().equals(proof.sha256())), "artifact-not-authorized-for-release");
        requireLease(planned.deploymentId(), planned.revision(), proof.sha256());
        Checks.require(!revoked.contains(proof.sha256()), "artifact-revoked");
        rememberBundles(planned);
        if (instances.containsKey(proof.sha256())) {
            var existing=instances.get(proof.sha256());
            Checks.require(existing.manifest.pluginId().equals(proof.pluginId()) && existing.manifest.version().equals(proof.version())
                    && Hashes.sha256(ProtocolJson.write(existing.manifest)).equals(proof.manifestSha256()),"manifest-proof-mismatch");
            existing.artifactIds.add(proof.artifactId());existing.proofExpiresAt=claims.expiresAt();return status(existing);
        }
        var snapshot = ArchiveSnapshot.inspect(jar, policy, proof.dependencyAllowlist());
        var manifest = snapshot.manifest();
        Checks.require(manifest.pluginId().equals(proof.pluginId()) && manifest.version().equals(proof.version())
                && Hashes.sha256(ProtocolJson.write(manifest)).equals(proof.manifestSha256()), "manifest-proof-mismatch");
        Checks.require(instances.values().stream().filter(i -> i.manifest.pluginId().equals(manifest.pluginId())).count() < policy.residentVersions(), "resident-version-quota-exceeded");
        Checks.require(instances.values().stream().noneMatch(i -> i.manifest.pluginId().equals(manifest.pluginId()) && i.manifest.version().equals(manifest.version())), "version-content-conflict");
        var loader = new SnapshotClassLoader(snapshot);
        var type = loader.loadClass(manifest.implementationClass()).asSubclass(BusinessPlugin.class);
        BusinessPlugin plugin = null;
        try {
            plugin = type.getConstructor().newInstance();
            String hash = proof.sha256();
            var context = new PluginContext(manifest.pluginId(), manifest.version(), (capability, json) -> {
                CallScope current = scope.get();
                Checks.require(current != null && current.artifactHash.equals(hash), "capability-outside-invocation");
                Checks.require(manifest.capabilities().contains(capability), "capability-not-declared");
                ProtocolJson.validateValue(json);
                return host.capability(manifest.pluginId(), manifest.version(), current.invocationId, current.identity, capability, json);
            }, (metric, amount) -> {
                Checks.require(manifest.metrics().contains(metric) && amount >= 0, "metric-not-declared");
                host.metric(manifest.pluginId(), manifest.version(), metric, amount);
            });
            plugin.initialize(context);
            var instance = new Instance(proof, manifest, plugin, claims.expiresAt()); instances.put(proof.sha256(), instance);
            return status(instance);
        } catch (Exception | LinkageError failure) {
            if (plugin != null) try { plugin.close(); } catch (Exception closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }
    public synchronized void renewLease(SignedEnvelope envelope) {
        Checks.require(!closed, "runtime-closed");
        var claims = verify(envelope, SignedClaims.Purpose.LEASE, Duration.ofMinutes(15));
        var lease = ProtocolJson.read(claims.body(), Lease.class);
        Checks.require(lease.nodeId().equals(nodeId), "lease-node-mismatch");
        Checks.require(lease.releaseRevision() >= checkpoint.revision(lease.deploymentId()), "stale-lease");
        String leaseKey = lease.deploymentId() + ":" + lease.releaseRevision();
        var previous = leases.get(leaseKey);
        Checks.require(previous == null || lease.releaseRevision() >= previous.lease.releaseRevision()
                && !claims.issuedAt().isBefore(previous.issuedAt), "stale-lease");
        revoked.addAll(lease.revokedHashes());
        // Replaying an identical lease must not reset its monotonic lifetime after a wall-clock rollback.
        long deadline = monotonicLeaseDeadlines.computeIfAbsent(claims.documentId(), ignored ->
                System.nanoTime() + Duration.between(clock.instant(), claims.expiresAt()).toNanos());
        leases.put(leaseKey, new LeaseState(lease,claims.keyId(), claims.issuedAt(), claims.expiresAt(), deadline));
    }
    public synchronized void applyRevocations(SignedEnvelope envelope) {
        var claims=verify(envelope,SignedClaims.Purpose.REVOCATIONS,Duration.ofMinutes(15));
        revoked.addAll(ProtocolJson.read(claims.body(),RevocationList.class).artifactHashes());
    }
    public synchronized ResourcePolicy policy(){return policy;}
    public java.util.Map<String,String> trustFingerprints(){return trust.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,e -> Hashes.sha256(e.getValue().publicKey().getEncoded())));}
    /** Only the host's out-of-band protected configuration reader calls this; never accepts keys from a plugin/upload. */
    public synchronized void replaceTrustedKeys(Map<String,DocumentSignatures.TrustedKey> keys) {
        Checks.require(!keys.isEmpty() && keys.size()<=100,"invalid-trust-file");
        trust=Map.copyOf(keys);
        leases.entrySet().removeIf(e -> !trust.containsKey(e.getValue().keyId()) || !trust.get(e.getValue().keyId()).purposes().contains(SignedClaims.Purpose.LEASE));
    }
    public synchronized void applyPolicy(SignedEnvelope envelope) {
        var claims=verify(envelope,SignedClaims.Purpose.POLICY,Duration.ofMinutes(15));
        var candidate=ProtocolJson.read(claims.body(),ResourcePolicy.class);long persisted=checkpoint.revision("!policy");
        Checks.require(candidate.revision()>=persisted && candidate.revision()>=policy.revision(),"policy-revision-conflict");
        if(candidate.revision()==policy.revision())Checks.require(candidate.equals(policy),"policy-revision-content-conflict");
        if(candidate.revision()>persisted)checkpoint.advance("!policy",persisted,candidate.revision());
        policy=candidate;
    }
    public synchronized void activate(SignedEnvelope envelope) {
        Checks.require(!closed, "runtime-closed");
        var claims = verify(envelope, SignedClaims.Purpose.RELEASE, Duration.ofMinutes(15));
        var release = ProtocolJson.read(claims.body(), Release.class);
        Checks.require(release.targetNodes().contains(nodeId) && release.policyRevision() <= policy.revision(), "release-node-or-policy-mismatch");
        long current = checkpoint.revision(release.deploymentId());
        if (current == release.revision() && active.containsKey(release.deploymentId())) {
            Checks.require(Hashes.sha256(ProtocolJson.write(active.get(release.deploymentId()))).equals(Hashes.sha256(ProtocolJson.write(release))), "release-revision-content-conflict"); return;
        }
        // Equal revision is allowed only for cold recovery with a fresh signed release and lease.
        // Offline/new nodes may miss intermediate revisions. A current signed full vector can advance any older checkpoint.
        Checks.require(current < release.revision() || current == release.revision() && !active.containsKey(release.deploymentId()), "release-revision-conflict");
        for (var bundle : release.bundles()) {
            for (var member : bundle.members()) {
                var instance = instance(member.sha256());
                Checks.require(instance.manifest.pluginId().equals(member.pluginId()) && instance.artifactIds.contains(member.artifactId()), "release-artifact-mismatch");
                Checks.require(clock.instant().isBefore(instance.proofExpiresAt), "artifact-proof-expired");
                requireLease(release.deploymentId(), release.revision(), member.sha256());
            }
            var old = retainedBundles.get(bundleKey(release.deploymentId(),bundle.id()));
            Checks.require(old == null || old.equals(bundle), "bundle-id-content-conflict");
        }
        if (current != release.revision()) checkpoint.advance(release.deploymentId(), current, release.revision());
        rememberBundles(release);
        active.put(release.deploymentId(), release);
    }
    public synchronized Selection select(String deploymentId, InvocationIdentity identity) {
        var release = active.get(deploymentId); Checks.require(release != null && !closed, "deployment-unavailable");
        String subject = identity.tenantId() + ":" + identity.kind() + ":" + identity.subject();
        String digest = Hashes.sha256((release.bucketSalt()+":"+subject).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        int bucket = (int)(Long.parseUnsignedLong(digest.substring(0, 8), 16) % 10000);
        var bundle = release.canary() != null && (release.canarySubjects().contains(subject) || bucket < release.canaryBasisPoints()) ? release.canary() : release.stable();
        return new Selection(deploymentId, release.revision(), bundle.id());
    }
    /** Holds the lifecycle lock until the host has durably retained the exact version vector. */
    public synchronized <T> T registerWork(Selection selection,String pluginId,String entryId,InvocationIdentity identity,
            java.util.function.BiFunction<List<Member>,ArtifactManifest.Entry,T> persist) {
        Checks.require(!closed,"runtime-closed");
        var release=active.get(selection.deploymentId());Checks.require(release!=null,"deployment-unavailable");
        var bundle=retainedBundles.get(bundleKey(selection.deploymentId(),selection.bundleId()));Checks.require(bundle!=null,"bundle-unavailable");
        Checks.require(selection.releaseRevision()<=release.revision(),"invalid-selection");
        for(var member:bundle.members()) {instance(member.sha256());requireLease(selection.deploymentId(),release.revision(),member.sha256());}
        var member=bundle.members().stream().filter(m -> m.pluginId().equals(pluginId)).findFirst().orElseThrow(() -> new ProtocolException("plugin-not-in-bundle"));
        var selected=instance(member.sha256());
        var entry=selected.manifest.entries().stream().filter(e -> e.id().equals(entryId)).findFirst().orElseThrow(() -> new ProtocolException("entry-not-found"));
        Checks.require(entry.identities().contains(identity.kind().name()),"identity-not-allowed");host.authorize(pluginId,entry,identity);
        return persist.apply(bundle.members(),entry);
    }
    /** Selection is host-owned; durable business pins must be retrieved from authenticated host storage, never client headers. */
    public PluginResult invoke(Selection selection, String pluginId, String entryId, InvocationIdentity identity,
            String businessKey, byte[] input, Instant deadline) throws Exception {
        Checks.require(input != null && input.length <= 1_048_576, "input-too-large"); ProtocolJson.validateValue(input);
        Checks.require(deadline != null && clock.instant().isBefore(deadline), "invocation-deadline-exceeded");
        Instance selected; String invocationId = UUID.randomUUID().toString();
        synchronized (this) {
            Checks.require(!closed, "runtime-closed");
            var release = active.get(selection.deploymentId()); Checks.require(release != null, "deployment-unavailable");
            var bundle = retainedBundles.get(bundleKey(selection.deploymentId(),selection.bundleId())); Checks.require(bundle != null, "bundle-unavailable");
            Checks.require(selection.releaseRevision()<=release.revision(),"invalid-selection");
            for(var required:bundle.members()){instance(required.sha256());requireLease(selection.deploymentId(),release.revision(),required.sha256());}
            var member = bundle.members().stream().filter(m -> m.pluginId().equals(pluginId)).findFirst().orElseThrow(() -> new ProtocolException("plugin-not-in-bundle"));
            selected = instance(member.sha256()); requireLease(selection.deploymentId(), release.revision(), member.sha256());
            var entry = selected.manifest.entries().stream().filter(e -> e.id().equals(entryId)).findFirst().orElseThrow(() -> new ProtocolException("entry-not-found"));
            Checks.require(entry.identities().contains(identity.kind().name()), "identity-not-allowed");
            Checks.require(!entry.stateful() || businessKey != null && !businessKey.isBlank(), "business-key-required");
            host.authorize(pluginId, entry, identity); selected.inFlight++;
        }
        CallScope previous = scope.get();
        scope.set(new CallScope(selected.proof.sha256(), invocationId, identity));
        long started=System.nanoTime();boolean success=false;
        try {
            var result = selected.plugin.execute(new PluginCall(invocationId, entryId, businessKey, identity, selected.manifest.version(), selection.releaseRevision(), deadline, input));
            Checks.require(result != null, "plugin-result-required"); ProtocolJson.validateValue(result.json());
            success=true;
            return result;
        } finally {
            if (previous == null) scope.remove(); else scope.set(previous);
            synchronized (this) { selected.inFlight--; }
            try {host.observe(new InvocationObservation(invocationId,selection.deploymentId(),selection.releaseRevision(),selection.bundleId(),pluginId,
                    selected.manifest.version(),selected.proof.sha256(),entryId,identity,success,Math.max(0,System.nanoTime()-started)));}
            catch(RuntimeException unavailable) { /* Telemetry cannot turn a committed success into a retryable business error. */ }
        }
    }
    public synchronized boolean unload(String sha256) throws Exception {
        var instance = instances.get(sha256); if (instance == null) return true;
        Checks.require(active.values().stream().flatMap(r -> r.bundles().stream()).flatMap(b -> b.members().stream()).noneMatch(m -> m.sha256().equals(sha256)), "artifact-still-routed");
        if (instance.inFlight != 0 || host.durableReferences(sha256) != 0) return false;
        instance.plugin.close(); instances.remove(sha256);
        retainedBundles.entrySet().removeIf(e -> e.getValue().members().stream().anyMatch(m -> m.sha256().equals(sha256)));
        return true;
    }
    public synchronized List<Status> statuses() { return instances.values().stream().map(this::status).toList(); }
    public synchronized Set<String> permissionKeys(){return instances.values().stream().flatMap(i -> i.manifest.entries().stream()).map(ArtifactManifest.Entry::permission).collect(java.util.stream.Collectors.toUnmodifiableSet());}
    public synchronized void reap() throws Exception {
        Set<String> routed=active.values().stream().flatMap(r -> r.bundles().stream()).flatMap(b -> b.members().stream()).map(Member::sha256).collect(java.util.stream.Collectors.toSet());
        for(String hash:new ArrayList<>(instances.keySet())) if(!routed.contains(hash)) unload(hash);
        leases.entrySet().removeIf(e -> !clock.instant().isBefore(e.getValue().expiresAt));
        monotonicLeaseDeadlines.entrySet().removeIf(e -> System.nanoTime()-e.getValue()>=0);
    }
    private Status status(Instance value) { return new Status(value.proof.pluginId(), value.manifest.version(), value.proof.sha256(), value.inFlight, revoked.contains(value.proof.sha256())); }
    private static String bundleKey(String deployment,String bundle){return deployment+":"+bundle;}
    private void rememberBundles(Release release) {
        for(var bundle:release.bundles()) {
            String key=bundleKey(release.deploymentId(),bundle.id());var old=retainedBundles.get(key);
            Checks.require(old==null || old.equals(bundle),"bundle-id-content-conflict");retainedBundles.put(key,bundle);
        }
    }
    private Instance instance(String hash) { var instance = instances.get(hash); Checks.require(instance != null, "artifact-not-prepared"); return instance; }
    private void requireLease(String deploymentId, long revision, String hash) {
        var lease = leases.get(deploymentId + ":" + revision);
        Checks.require(lease != null && lease.lease.releaseRevision() == revision && clock.instant().isBefore(lease.expiresAt)
                && System.nanoTime() - lease.monotonicDeadline < 0 && lease.lease.artifactHashes().contains(hash) && !revoked.contains(hash), "execution-lease-unavailable");
    }
    private SignedClaims verify(SignedEnvelope envelope, SignedClaims.Purpose purpose, Duration maxAge) {
        return DocumentSignatures.verify(envelope, trust, purpose, issuer, audience, environment, clock.instant(), maxAge);
    }
    @Override public synchronized void close() throws Exception {
        closed = true; active.clear(); Exception failure = null;
        for (var item : new ArrayList<>(instances.values())) {
            if (item.inFlight != 0) continue;
            try { item.plugin.close(); instances.remove(item.proof.sha256()); } catch (Exception ex) { if (failure == null) failure = ex; else failure.addSuppressed(ex); }
        }
        if (failure != null) throw failure;
    }
    public record Selection(String deploymentId, long releaseRevision, String bundleId) {
        public Selection { Checks.id(deploymentId); Checks.id(bundleId); Checks.require(releaseRevision > 0, "invalid-selection"); }
    }
    public record Status(String pluginId, String version, String sha256, long inFlight, boolean revoked) {}
    private static final class Instance {
        final ArtifactProof proof; final ArtifactManifest manifest; final BusinessPlugin plugin; Instant proofExpiresAt;
        final Set<String> artifactIds=new HashSet<>();
        long inFlight;
        Instance(ArtifactProof proof, ArtifactManifest manifest, BusinessPlugin plugin, Instant expires) { this.proof = proof; this.manifest = manifest; this.plugin = plugin; this.proofExpiresAt = expires;artifactIds.add(proof.artifactId()); }
    }
    private record LeaseState(Lease lease,String keyId, Instant issuedAt, Instant expiresAt, long monotonicDeadline) {}
    private record CallScope(String artifactHash, String invocationId, InvocationIdentity identity) {}
}
