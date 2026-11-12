package com.sma.extension.runtime;

import static org.assertj.core.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sma.extension.api.*;
import com.sma.extension.protocol.*;
import com.sma.extension.protocol.ExecutionDocuments.*;

class PluginRuntimeTests {
    @TempDir Path temporary;
    private KeyPair signing;
    private MutableClock clock;
    private PluginRuntime runtime;
    private final Map<String, Long> durable = new ConcurrentHashMap<>();
    private final Map<String,Long> checkpoints=new ConcurrentHashMap<>();
    private final List<InvocationObservation> observations=new java.util.ArrayList<>();
    private boolean telemetryUnavailable;
    private java.util.concurrent.CountDownLatch capabilityEntered,capabilityRelease;
    private static final InvocationIdentity USER = new InvocationIdentity(1, "42", InvocationIdentity.Kind.USER);
    @BeforeEach void setup() throws Exception {
        signing = KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); clock = new MutableClock();
        runtime=freshRuntime();
    }
    private PluginRuntime freshRuntime() {
        var trust = Map.of("signing", new DocumentSignatures.TrustedKey(signing.getPublic(), Set.of(SignedClaims.Purpose.ARTIFACT, SignedClaims.Purpose.PREPARE, SignedClaims.Purpose.RELEASE, SignedClaims.Purpose.LEASE, SignedClaims.Purpose.REVOCATIONS), clock.instant().minusSeconds(60), clock.instant().plus(Duration.ofDays(2))));
        return new PluginRuntime("security", "host", "production", "node", clock, ResourcePolicy.defaults(), trust, new RuntimeHost() {
            @Override public void authorize(String plugin, ArtifactManifest.Entry entry, InvocationIdentity identity) { Checks.require(identity.equals(USER), "forbidden"); }
            @Override public byte[] capability(String p, String v, String invocation, InvocationIdentity identity, String c, byte[] j) throws Exception {
                if(capabilityEntered!=null){capabilityEntered.countDown();if(!capabilityRelease.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Capability not released");}
                return j;
            }
            @Override public void metric(String p, String v, String m, long n) {}
            @Override public long durableReferences(String hash) { return durable.getOrDefault(hash, 0L); }
            @Override public void observe(InvocationObservation observation){if(telemetryUnavailable)throw new IllegalStateException("audit unavailable");observations.add(observation);}
        }, new RuntimeCheckpoint() {
            @Override public long revision(String deployment) { return checkpoints.getOrDefault(deployment, 0L); }
            @Override public void advance(String deployment, long expected, long next) { checkpoints.compute(deployment, (key, old) -> { Checks.require((old == null ? 0 : old) == expected, "revision-conflict"); return next; }); }
        });
    }
    @Test void realJarsCoexistAndSwitchWithoutRestartingTheJvm() throws Exception {
        long pid = ProcessHandle.current().pid();
        var first = build("1.0.0", "old"); var second = build("2.0.0", "new");
        var release1 = release(1, 0, first, null, 0); install(release1, List.of(first));
        var oldSelection = runtime.select("demo", USER);
        assertThat(call(oldSelection)).isEqualTo("\"old\"");
        var release2 = release(2, 1, first, second, 10000); install(release2, List.of(first, second));
        assertThat(call(runtime.select("demo", USER))).isEqualTo("\"new\"");
        assertThat(call(oldSelection)).isEqualTo("\"old\"");
        assertThat(runtime.statuses()).hasSize(2);
        // Rollback is a new monotonic revision, never replay of the old release document.
        var rollback = release(3, 2, first, null, 0); install(rollback, List.of(first, second));
        assertThat(call(runtime.select("demo", USER))).isEqualTo("\"old\"");
        assertThatThrownBy(() -> runtime.activate(sign(SignedClaims.Purpose.RELEASE, release2, 600))).hasMessage("release-revision-conflict");
        assertThat(runtime.unload(second.hash())).isTrue();
        assertThat(ProcessHandle.current().pid()).isEqualTo(pid);
        assertThat(observations).hasSize(4);assertThat(observations).allMatch(InvocationObservation::success);
        assertThat(observations.get(1).version()).isEqualTo("2.0.0");
    }
    @Test void telemetryFailureCannotChangeSuccessfulPluginResult()throws Exception {
        var artifact=build("1.0.0","success");install(release(1,0,artifact,null,0),List.of(artifact));telemetryUnavailable=true;
        assertThat(call(runtime.select("demo",USER))).isEqualTo("\"success\"");assertThat(runtime.statuses().getFirst().inFlight()).isZero();
    }
    @Test void newlyProvisionedAndOfflineNodesCanJoinLatestSignedVectorWithoutAcceptingRollback()throws Exception {
        var artifact=build("1.0.0","current");var third=release(3,2,artifact,null,0);install(third,List.of(artifact));
        assertThat(call(runtime.select("demo",USER))).isEqualTo("\"current\"");assertThat(checkpoints.get("demo")).isEqualTo(3);
        var sixth=release(6,5,artifact,null,0);install(sixth,List.of(artifact));assertThat(checkpoints.get("demo")).isEqualTo(6);
        assertThatThrownBy(() -> runtime.activate(sign(SignedClaims.Purpose.RELEASE,third,600))).hasMessage("release-revision-conflict");
    }
    @Test void coldRecoveryRestoresOnlySignedHistoricalVectorAndKeepsCurrentRouting()throws Exception {
        var old=build("1.0.0","old");var current=build("2.0.0","current");
        var first=release(1,0,old,null,0);install(first,List.of(old));var pin=runtime.select("demo",USER);
        durable.put(old.hash(),1L);var next=release(2,1,current,null,0);install(next,List.of(old,current));
        runtime.close();runtime=freshRuntime();
        runtime.renewLease(lease(next,Set.of(old.hash(),current.hash()),Set.of()));
        runtime.prepare(current.jar(),proof(current),sign(SignedClaims.Purpose.PREPARE,next,600));
        var historical=new Release(2,1,1,"demo",bundle(old),null,0,"stable-salt",Set.of(),Set.of("node"),"a".repeat(64));
        runtime.prepare(old.jar(),proof(old),sign(SignedClaims.Purpose.PREPARE,historical,600));
        runtime.activate(sign(SignedClaims.Purpose.RELEASE,next,600));
        assertThat(call(pin)).isEqualTo("\"old\"");assertThat(call(runtime.select("demo",USER))).isEqualTo("\"current\"");
        assertThat(runtime.unload(old.hash())).isFalse();durable.clear();assertThat(runtime.unload(old.hash())).isTrue();
    }
    @Test void noClassInitializationWithoutReleaseAuthorizationAndExactBytes() throws Exception {
        var artifact = build("1.0.0", "value"); var planned = release(1, 0, artifact, null, 0);
        var proof = proof(artifact);
        assertThatThrownBy(() -> runtime.prepare(artifact.jar(), proof, sign(SignedClaims.Purpose.PREPARE, planned, 600))).hasMessage("execution-lease-unavailable");
        runtime.renewLease(lease(planned, Set.of(artifact.hash()), Set.of()));
        byte[] changed = artifact.jar().clone(); changed[changed.length-1] ^= 1;
        assertThatThrownBy(() -> runtime.prepare(changed, proof, sign(SignedClaims.Purpose.PREPARE, planned, 600))).hasMessage("artifact-proof-mismatch");
        assertThat(runtime.statuses()).isEmpty();
    }
    @Test void leaseExpiryRevocationAndDurableReferencesAreEnforced() throws Exception {
        var first = build("1.0.0", "old"); var next = build("2.0.0", "new");
        var one = release(1, 0, first, null, 0); install(one, List.of(first));
        var two = release(2, 1, next, null, 0); install(two, List.of(first, next));
        durable.put(first.hash(), 1L); assertThat(runtime.unload(first.hash())).isFalse();
        durable.clear(); assertThat(runtime.unload(first.hash())).isTrue();
        clock.advance(901);
        assertThatThrownBy(() -> call(runtime.select("demo", USER))).hasMessage("execution-lease-unavailable");
        runtime.renewLease(lease(two, Set.of(next.hash()), Set.of(next.hash())));
        assertThatThrownBy(() -> call(runtime.select("demo", USER))).hasMessage("execution-lease-unavailable");
    }
    @Test void authorizationAndDeterministicGreyCohortsUseHostIdentity() throws Exception {
        var first = build("1.0.0", "old"); var next = build("2.0.0", "new");
        var planned = release(1, 0, first, next, 5000); install(planned, List.of(first, next));
        var selected = runtime.select("demo", USER);
        for (int i=0; i<100; i++) assertThat(runtime.select("demo", USER)).isEqualTo(selected);
        assertThatThrownBy(() -> runtime.invoke(selected, "example", "execute", new InvocationIdentity(2, "42", InvocationIdentity.Kind.USER), null, "{}".getBytes(StandardCharsets.UTF_8), clock.instant().plusSeconds(10))).hasMessage("forbidden");
    }
    @Test void revocationStopsNewCallsBeforeTheLeaseExpiresAndCannotBeUndoneByEmptyList() throws Exception {
        var artifact=build("1.0.0","value");var release=release(1,0,artifact,null,0);install(release,List.of(artifact));
        var selection=runtime.select("demo",USER);
        runtime.applyRevocations(sign(SignedClaims.Purpose.REVOCATIONS,new RevocationList(Set.of(artifact.hash())),900));
        assertThatThrownBy(() -> call(selection)).hasMessage("execution-lease-unavailable");
        runtime.applyRevocations(sign(SignedClaims.Purpose.REVOCATIONS,new RevocationList(Set.of()),900));
        assertThatThrownBy(() -> call(selection)).hasMessage("execution-lease-unavailable");
    }
    @Test void leaseExpiryDoesNotCancelPinnedExecutionOrAllowAnotherInvocation()throws Exception{
        var artifact=build("1.0.0","held",true);install(release(1,0,artifact,null,0),List.of(artifact));
        var selection=runtime.select("demo",USER);
        capabilityEntered=new java.util.concurrent.CountDownLatch(1);capabilityRelease=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()){
            var current=executor.submit(()->call(selection));
            try{
                assertThat(capabilityEntered.await(3,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(runtime.statuses().getFirst().inFlight()).isOne();clock.advance(901);
                assertThatThrownBy(()->call(selection)).hasMessage("execution-lease-unavailable");
                runtime.reap();assertThat(runtime.statuses().getFirst().inFlight()).isOne();
            }finally{capabilityRelease.countDown();}
            assertThat(current.get(3,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("{}");
            assertThat(runtime.statuses().getFirst().inFlight()).isZero();runtime.close();assertThat(runtime.statuses()).isEmpty();
        }finally{capabilityRelease.countDown();}
    }
    private String call(PluginRuntime.Selection selection) throws Exception {
        return new String(runtime.invoke(selection, "example", "execute", USER, null, "{}".getBytes(StandardCharsets.UTF_8), clock.instant().plusSeconds(30)).json(), StandardCharsets.UTF_8);
    }
    private void install(Release release, List<Built> artifacts) throws Exception {
        runtime.renewLease(lease(release, artifacts.stream().map(Built::hash).collect(java.util.stream.Collectors.toSet()), Set.of()));
        var signed = sign(SignedClaims.Purpose.RELEASE, release, 600);
        for (var artifact : artifacts) if (release.bundles().stream().flatMap(b -> b.members().stream()).anyMatch(m -> m.sha256().equals(artifact.hash())))
            runtime.prepare(artifact.jar(), proof(artifact), sign(SignedClaims.Purpose.PREPARE, release, 600));
        runtime.activate(signed);
    }
    private SignedEnvelope proof(Built built) {
        return sign(SignedClaims.Purpose.ARTIFACT, new ArtifactProof("artifact-"+built.manifest().version(), "general", 1, "example", built.manifest().version(), built.hash(), built.jar().length,
                Hashes.sha256(ProtocolJson.write(built.manifest())), 1, "a".repeat(64), "IMPORT", Map.of()), 3600);
    }
    private SignedEnvelope lease(Release release, Set<String> hashes, Set<String> revoked) { return sign(SignedClaims.Purpose.LEASE, new Lease("node", "demo", release.revision(), hashes, revoked), 900); }
    private Release release(long revision, long previous, Built stable, Built canary, int weight) {
        return new Release(revision, previous, 1, "demo", bundle(stable), canary == null ? null : bundle(canary), weight, "stable-salt", Set.of(), Set.of("node"), "a".repeat(64));
    }
    private Bundle bundle(Built artifact) { return new Bundle("bundle-"+artifact.manifest().version(), List.of(new Member("example", "artifact-"+artifact.manifest().version(), artifact.hash()))); }
    private SignedEnvelope sign(SignedClaims.Purpose purpose, Object body, long seconds) {
        return DocumentSignatures.sign(new SignedClaims(1, purpose, "signing", "security", "host", "production", java.util.UUID.randomUUID().toString(), clock.instant(), clock.instant().plusSeconds(seconds), ProtocolJson.write(body)), signing.getPrivate());
    }
    private Built build(String version, String output) throws Exception {
        return build(version,output,false);
    }
    private Built build(String version,String output,boolean capability)throws Exception{
        Path dir = Files.createDirectories(temporary.resolve(version)); Path source = dir.resolve("Example.java");
        Files.writeString(source, capability
                ? "package example; import com.sma.extension.api.*; public class Example implements BusinessPlugin { private PluginContext context; public void initialize(PluginContext c){context=c;} public PluginResult execute(PluginCall c)throws Exception{return new PluginResult(context.capabilities().invoke(\"fixture.hold\",c.input()));}}"
                : "package example; import com.sma.extension.api.*; public class Example implements BusinessPlugin { public PluginResult execute(PluginCall c) { return new PluginResult(\"\\\""+output+"\\\"\".getBytes(java.nio.charset.StandardCharsets.UTF_8)); } }");
        String sdk = Path.of(BusinessPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "21", "-proc:none", "-classpath", sdk, "-d", dir.toString(), source.toString())).isZero();
        var manifest = new ArtifactManifest(1, "example", version, 1, "example.Example", List.of(new ArtifactManifest.Entry("execute", ArtifactManifest.EntryKind.SERVICE, "v1", "extension:invoke:example", Set.of("USER"), false)), List.of(), capability?Set.of("fixture.hold"):Set.of(), Set.of());
        var bytes = new ByteArrayOutputStream();
        try (var jar = new JarOutputStream(bytes)) {
            jar.putNextEntry(new JarEntry(ArtifactManifest.PATH)); jar.write(ProtocolJson.write(manifest)); jar.closeEntry();
            jar.putNextEntry(new JarEntry("example/Example.class")); jar.write(Files.readAllBytes(dir.resolve("example/Example.class"))); jar.closeEntry();
        }
        return new Built(manifest, bytes.toByteArray(), Hashes.sha256(bytes.toByteArray()));
    }
    private record Built(ArtifactManifest manifest, byte[] jar, String hash) {}
    private static final class MutableClock extends Clock {
        private Instant time = Instant.parse("2026-11-11T00:00:00Z");
        void advance(long seconds) { time = time.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return time; }
    }
}
