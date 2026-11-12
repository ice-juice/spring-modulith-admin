package com.sma.release;

import static org.assertj.core.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.crypto.spec.SecretKeySpec;
import javax.tools.ToolProvider;
import org.apache.commons.codec.binary.Base32;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.sma.extension.api.*;
import com.sma.extension.protocol.*;
import com.sma.extension.protocol.ExecutionDocuments.*;
import com.sma.extension.runtime.*;
import com.sma.release.auth.*;
import com.sma.release.catalog.*;
import com.sma.release.catalog.CatalogRecords.*;
import com.sma.release.crypto.*;
import com.sma.release.deployment.*;
import com.sma.release.deployment.DeploymentRecords.*;
import com.sma.release.store.ReleaseDatabase;

/** Real Java/JAR, real database transactions and signatures. Scanner result is an explicit fixture, not a live vulnerability scan. */
class ReleaseWorkflowTests {
    @TempDir Path dir;
    private final MutableClock clock=new MutableClock();
    private ReleaseDatabase db; private IdentityService identities; private CatalogService catalog;
    private ValidationService validation; private DeploymentService deployments; private SigningService signing;
    private String session,secret;
    private AccountManagement accounts;
    private MachineManagement machineManagement;
    private static final String LOGIN="WorkflowLoginPassword!24",OPERATION="WorkflowOperationPassword!93";
    private static final String NODE="n".repeat(43),WORKER="w".repeat(43);
    @Test void unpublishedDraftsCannotEvictPublishedRetentionAndPublicationAgeIsAuthoritative()throws Exception {
        var release=validatedRelease(Set.of("node"));var member=release.stable().members().getFirst();
        db.transaction(tx -> {
            var artifact=tx.get("artifact",member.artifactId(),Artifact.class).orElseThrow();tx.put("history","fixture-history",new DeploymentService.History("production",release));
            assertThatThrownBy(() -> ArtifactRetention.requireDeletable(tx,artifact,ResourcePolicy.defaults())).hasMessage("artifact-publication-time-unknown");
            tx.put("publication-time","production:"+artifact.id(),tx.now());
            assertThatThrownBy(() -> ArtifactRetention.requireDeletable(tx,artifact,ResourcePolicy.defaults())).hasMessage("artifact-retention-period-active");
            tx.put("publication-time","production:"+artifact.id(),tx.now().minus(Duration.ofDays(31)));
            for(int i=0;i<25;i++){String id="draft-"+i;tx.put("artifact",id,new Artifact(id,"fixture",0,artifact.groupId(),artifact.pluginId(),"production",artifact.sha256(),artifact.bytes(),artifact.name(),"VERIFIED",artifact.manifest(),artifact.report(),tx.now().plusSeconds(i+1)));}
            assertThatThrownBy(() -> ArtifactRetention.requireDeletable(tx,artifact,ResourcePolicy.defaults())).hasMessage("artifact-retained-version-limit");return null;
        });
    }
    @Test void cleanupCannotDeleteReferencedBytesAndOnlyFreesQuotaAfterPhysicalDeletion()throws Exception {
        var release=validatedRelease(Set.of("node"));var member=release.stable().members().getFirst();
        long before=db.transaction(StorageUsage::repository);assertThat(before).isPositive();
        db.transaction(tx -> {tx.put("node-retention","held",new NodeRetention("node","production","deployment",new Retention(Set.of(member.sha256()),Set.of(release.stable().id())),Set.of(),tx.now().minusSeconds(1)));return null;});
        assertThatThrownBy(() -> execute(ActionIntent.Action.DELETE,1,1,List.of(),member.artifactId())).hasMessage("artifact-durably-referenced");
        db.transaction(tx -> {tx.delete("node-retention","held");return null;});
        execute(ActionIntent.Action.DELETE,1,1,List.of(),member.artifactId());assertThat(db.transaction(StorageUsage::repository)).isEqualTo(before);
        var properties=new ReleaseProperties("security","host",dir.resolve("store"),java.util.Base64.getEncoder().encodeToString(new byte[32]),"https://release.example.test","");
        var files=new ArtifactFiles(properties);new ControlMaintenance(db,files,deployments).expire();
        assertThat(db.transaction(StorageUsage::repository)).isZero();
        assertThat(catalog.artifacts(session,"example","production").getFirst().state()).isEqualTo("DELETED");
        assertThatThrownBy(() -> files.openArtifact(member.artifactId())).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> execute(ActionIntent.Action.VALIDATE,1,1,List.of(),member.artifactId())).isInstanceOf(ProtocolException.class);
    }
    @Test void requiredAuditContinuityRejectsMissingStaleOrWrongAnchors()throws Exception {
        var continuity=new com.sma.release.store.AuditContinuity(new AuditPolicy(true,120,100));
        assertThatThrownBy(() -> db.transaction(tx -> {continuity.requireHealthy(tx);return null;})).hasMessage("external-audit-unavailable");
        execute(ActionIntent.Action.MACHINE_CREATE,0,0,List.of(),new MachineManagement.Create("auditor",MachineService.Kind.AUDITOR,Set.of("security")));
        db.transaction(tx -> {
            var auditor=tx.get("machine","auditor",MachineService.Machine.class).orElseThrow();var entries=tx.auditPage(0,200);var tail=entries.getLast();
            assertThatThrownBy(() -> tx.auditExport(tail.entry().sequence(),"a".repeat(64),100)).hasMessage("audit-anchor-mismatch");
            continuity.acknowledge(tx,auditor,new com.sma.release.store.AuditContinuity.Acknowledgement(tail.entry().sequence(),tail.entryHash()));continuity.requireHealthy(tx);return null;
        });
        clock.advance(121);assertThatThrownBy(() -> db.transaction(tx -> {continuity.requireHealthy(tx);return null;})).hasMessage("external-audit-unavailable");
    }
    @Test void consoleQueriesExposeOnlyCurrentIdentityAndGrantedEnvironment()throws Exception {
        var queries=new ConsoleQueries(db,identities);var access=queries.access(session);assertThat(access.environments()).containsExactly("production");
        assertThat(new String(ProtocolJson.write(access),StandardCharsets.UTF_8)).doesNotContain(session).doesNotContain(NODE).doesNotContain(WORKER).doesNotContain("sessionId");
        assertThat(queries.storage(session).groups()).hasSize(1);
        assertThatThrownBy(() -> queries.artifacts(session,"example","production",0,201)).hasMessage("invalid-page");
        var actor=identities.principal(session);
        db.transaction(tx -> {var u=tx.get("user",actor.userId(),SecurityRecords.User.class).orElseThrow();tx.put("user",u.id(),new SecurityRecords.User(u.id(),u.username(),u.loginHash(),u.operationHash(),u.sealedTotp(),u.usedTotpStep(),u.credentialVersion(),true,true,false,u.enrollmentTokenHash(),u.enrollmentExpiresAt(),u.recoveryHashes()));
            tx.put("scope","scope-one",new SecurityRecords.ScopeGrant("scope-one",u.id(),"general","test",Set.of(ActionIntent.Action.UPLOAD),true));return null;});
        assertThat(queries.access(session).environments()).containsExactly("test");
        assertThat(queries.artifacts(session,"example","test",0,20).items()).isEmpty();
        assertThatThrownBy(() -> queries.artifacts(session,"example","production",0,20)).hasMessage("forbidden");
        assertThatThrownBy(() -> queries.storage(session)).hasMessage("forbidden");
        assertThatThrownBy(() -> queries.byKey(session,"unknown-command")).hasMessage("action-not-found");
    }
    @BeforeEach void initialize() throws Exception {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        db=new ReleaseDatabase(new JdbcTemplate(ds),new TransactionTemplate(new DataSourceTransactionManager(ds)),clock);
        var properties=new ReleaseProperties("security","host",dir.resolve("store"),java.util.Base64.getEncoder().encodeToString(new byte[32]),"https://release.example.test","");
        var vault=new SecretVault(properties);var credentials=new CredentialService(vault);
        identities=new IdentityService(db,credentials);var machines=new MachineService(credentials);signing=new SigningService(vault,properties);
        var files=new ArtifactFiles(properties);validation=new ValidationService(db,machines,files);
        var auditContinuity=new com.sma.release.store.AuditContinuity(new AuditPolicy(false,120,100));
        deployments=new DeploymentService(db,identities,machines,signing,validation,files,auditContinuity);
        accounts=new AccountManagement(db,identities,credentials,vault);machineManagement=new MachineManagement(db,identities,credentials,vault);
        catalog=new CatalogService(db,identities,files,validation,deployments,accounts,machineManagement,auditContinuity,new ConsoleIdentityService(db,machines,credentials,vault,identities));
        var account=identities.bootstrap("operator",LOGIN,OPERATION);
        secret=identities.beginEnrollment("operator",LOGIN,account.enrollmentToken(),"127.0.0.1").secret();
        identities.completeEnrollment("operator",LOGIN,account.enrollmentToken(),otp(),"127.0.0.1");
        clock.advance(30);session=identities.login("operator",LOGIN,otp(),"127.0.0.1").token();
        db.transaction(tx -> { machines.provision(tx,"node",MachineService.Kind.NODE,Set.of("production"),NODE);machines.provision(tx,"worker",MachineService.Kind.WORKER,Set.of("production"),WORKER);signing.initialize(tx);return null; });
        execute(ActionIntent.Action.GROUP_CREATE,0,0,List.of(),new GroupInput("General",true));
        execute(ActionIntent.Action.PLUGIN_CREATE,1,0,List.of(),new PluginInput("Example"));
    }
    @Test void uploadLookupIsReadOnlyAndRejectsAnotherSessionEvenForTheSameAdministrator() throws Exception {
        byte[] bytes="original bytes".getBytes(StandardCharsets.UTF_8);
        var receipt=execute(ActionIntent.Action.UPLOAD,1,1,List.of(new ActionIntent.FileIntent("sample.jar",Hashes.sha256(bytes),bytes.length)),Map.of());
        var before=catalog.uploadStatus(session,receipt.resourceId());
        assertThat(catalog.uploadStatus(session,receipt.resourceId())).isEqualTo(before);
        clock.advance(30);String another=identities.login("operator",LOGIN,otp(),"127.0.0.1").token();
        assertThatThrownBy(()->catalog.uploadStatus(another,receipt.resourceId())).hasMessage("forbidden");
        clock.advance(901);
        var expired=catalog.uploadStatus(session,receipt.resourceId());
        assertThat(expired.state()).isEqualTo("RECEIVING");assertThat(expired.resumable()).isFalse();
        assertThat(expired.artifactIds()).isEmpty();
    }
    @Test void singleUserUploadValidationPrepareAndActivationRunRealJar() throws Exception {
        byte[] jar=jar();String hash=Hashes.sha256(jar);
        var receipt=execute(ActionIntent.Action.UPLOAD,1,1,List.of(new ActionIntent.FileIntent("example.jar",hash,jar.length)),Map.of());
        String upload=receipt.resourceId();catalog.uploadChunk(session,upload,0,0,jar);
        var pendingUpload=catalog.uploadStatus(session,upload);
        assertThat(pendingUpload.state()).isEqualTo("RECEIVING");assertThat(pendingUpload.resumable()).isTrue();
        assertThat(pendingUpload.files()).containsExactly(new ActionIntent.FileIntent("example.jar",hash,jar.length));
        assertThat(pendingUpload.artifactIds()).isEmpty();
        byte[] changed=jar.clone();changed[0]^=1;
        assertThatThrownBy(() -> catalog.uploadChunk(session,upload,0,0,changed)).hasMessage("chunk-content-conflict");
        var artifact=catalog.completeUpload(session,upload).getFirst();
        var completed=catalog.uploadStatus(session,upload);
        assertThat(completed.state()).isEqualTo("COMPLETE");assertThat(completed.resumable()).isFalse();
        assertThat(completed.artifactIds()).containsExactly(artifact.id());
        assertThat(new String(ProtocolJson.write(completed),StandardCharsets.UTF_8)).doesNotContain("sessionId","actor",session);
        assertThat(catalog.completeUpload(session,upload)).hasSize(1);
        execute(ActionIntent.Action.VALIDATE,1,1,List.of(),artifact.id());
        assertThatThrownBy(() -> validation.claim(NODE,"production")).hasMessage("unauthenticated");
        var assignment=validation.claim(WORKER,"production");assertThat(assignment.artifactSha256()).isEqualTo(hash);
        var inspected=ArchiveSnapshot.inspect(jar,ResourcePolicy.defaults(),Map.of());
        var scan=new ScanReport(hash,"fixture-scanner","test-only",clock.instant(),clock.instant(),true,List.of(),inspected.inventory().stream().sorted().toList());
        validation.complete(WORKER,assignment.job().id(),new ValidationResult(artifact.id(),hash,inspected.manifest(),scan,inspected.inventory(),"IMPORT",clock.instant()));
        var release=new Release(1,0,1,"deployment",new Bundle("bundle-one",List.of(new Member("example",artifact.id(),hash))),null,0,"cohort",Set.of(),Set.of("node"),Hashes.sha256(new byte[0]));
        execute(ActionIntent.Action.PUBLISH,1,0,List.of(),release);
        var pending=deployments.desired(NODE,"production","deployment",Set.of());
        assertThat(pending.state()).isEqualTo("PREPARING");assertThat(pending.activation()).isNull();
        Map<String,DocumentSignatures.TrustedKey> trust=new java.util.HashMap<>();
        for(var key:db.transaction(tx -> signing.publicKeys(tx))) trust.put(key.id(),new DocumentSignatures.TrustedKey(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(java.util.Base64.getDecoder().decode(key.publicKey()))),key.purposes(),key.notBefore(),key.notAfter()));
        var checkpoint=new RuntimeCheckpoint() { long revision;public long revision(String id){return revision;}public void advance(String id,long expected,long next){assertThat(revision).isEqualTo(expected);revision=next;} };
        var host=new RuntimeHost() {
            public void authorize(String p,ArtifactManifest.Entry e,InvocationIdentity i) { assertThat(i.tenantId()).isEqualTo(1); }
            public byte[] capability(String p,String v,String id,InvocationIdentity i,String c,byte[] j) { throw new AssertionError("No capability was granted"); }
            public void metric(String p,String v,String m,long n) {} public long durableReferences(String h) {return 0;}
        };
        try(var runtime=new PluginRuntime("security","host","production","node",clock,ResourcePolicy.defaults(),trust,host,checkpoint)) {
            runtime.renewLease(pending.lease());
            runtime.prepare(jar,pending.artifacts().getFirst().proof(),pending.preparation());
            assertThatThrownBy(() -> runtime.select("deployment",new InvocationIdentity(1,"42",InvocationIdentity.Kind.USER))).hasMessage("deployment-unavailable");
            deployments.ready(NODE,"production",new Ready("deployment",pending.preparationId(),1,Hashes.sha256(ProtocolJson.write(release)),Set.of(hash)));
            var active=deployments.desired(NODE,"production","deployment",Set.of());
            runtime.renewLease(active.lease());runtime.activate(active.activation());
            var identity=new InvocationIdentity(1,"42",InvocationIdentity.Kind.USER);
            byte[] response=runtime.invoke(runtime.select("deployment",identity),"example","execute",identity,null,"{}".getBytes(StandardCharsets.UTF_8),clock.instant().plusSeconds(30)).json();
            assertThat(new String(response,StandardCharsets.UTF_8)).isEqualTo("\"loaded\"");
        }
        var next=new Release(2,1,1,"deployment",release.stable(),null,0,"cohort",Set.of(),Set.of("node"),release.configurationSha256());
        execute(ActionIntent.Action.PUBLISH,1,1,List.of(),next);
        identities.logout(session);
        var retained=deployments.desired(NODE,"production","deployment",Set.of());
        assertThat(retained.state()).isEqualTo("ACTIVE");
        var retainedClaims=ProtocolJson.read(DocumentSignatures.decode(retained.activation().payload()),SignedClaims.class);
        assertThat(ProtocolJson.read(retainedClaims.body(),Release.class).revision()).isEqualTo(1);
    }
    @Test void wrongWholeFileHashNeverBecomesAnArtifactAndChangedIntentCannotReuseGrant() throws Exception {
        byte[] jar=jar();
        var upload=execute(ActionIntent.Action.UPLOAD,1,1,List.of(new ActionIntent.FileIntent("example.jar","a".repeat(64),jar.length)),Map.of()).resourceId();
        catalog.uploadChunk(session,upload,0,0,jar);
        assertThatThrownBy(() -> catalog.completeUpload(session,upload)).hasMessage("upload-hash-mismatch");
        assertThat(catalog.artifacts(session,"example","production")).isEmpty();
        byte[] parameters=ProtocolJson.write(new GroupInput("Renamed",true));
        var intent=intent(ActionIntent.Action.GROUP_UPDATE,1,0,List.of(),parameters);
        var challenge=identities.challenge(session,intent);clock.advance(30);
        var grant=identities.authorize(session,challenge.id(),OPERATION,otp());
        var command=new Command("rename-once",grant.token(),intent,parameters);
        var first=catalog.execute(session,command);assertThat(catalog.execute(session,command)).isEqualTo(first);
        var changed=ProtocolJson.write(new GroupInput("Tampered",true));
        assertThatThrownBy(() -> catalog.execute(session,new Command("rename-once",grant.token(),intent,changed))).hasMessage("action-parameters-mismatch");
    }
    @Test void sourceBuildHasDistinctOutputHashBoundProvenanceAndSeparatePublishAuthorization() throws Exception {
        byte[] built=jar();byte[] source=Files.readAllBytes(dir.resolve("Example.java"));
        var snapshot=ArchiveSnapshot.inspect(built,ResourcePolicy.defaults(),Map.of());var plan=new SourceBuildPlan(snapshot.manifest());
        var upload=execute(ActionIntent.Action.UPLOAD,1,1,List.of(new ActionIntent.FileIntent("Example.java",Hashes.sha256(source),source.length)),plan).resourceId();
        catalog.uploadChunk(session,upload,0,0,source);var sourceArtifact=catalog.completeUpload(session,upload).getFirst();
        execute(ActionIntent.Action.VALIDATE,1,1,List.of(),sourceArtifact.id());
        var job=validation.claim(WORKER,"production");assertThat(job.build()).isNotNull();
        assertThat(job.build().sourceDigest()).isEqualTo(SourceBuildPlan.Assignment.digest(plan,job.build().inputs()));
        try(var downloaded=validation.source(WORKER,job.job().id(),sourceArtifact.id())) {assertThat(downloaded.readAllBytes()).isEqualTo(source);}
        assertThatThrownBy(() -> validation.source(WORKER,job.job().id(),"not-a-source")).hasMessage("build-input-not-authorized");
        var request=new OutputRequest(snapshot.sha256(),built.length,"a".repeat(64));
        assertThatThrownBy(() -> validation.beginOutput(NODE,job.job().id(),request)).hasMessage("unauthenticated");
        var output=validation.beginOutput(WORKER,job.job().id(),request);
        assertThat(validation.beginOutput(WORKER,job.job().id(),request)).isEqualTo(output);
        validation.outputChunk(WORKER,job.job().id(),0,built);var artifact=validation.finishOutput(WORKER,job.job().id());
        assertThat(artifact.sha256()).isNotEqualTo(sourceArtifact.sha256()).isEqualTo(snapshot.sha256());
        assertThat(validation.finishOutput(WORKER,job.job().id()).id()).isEqualTo(artifact.id());
        var scan=new ScanReport(snapshot.sha256(),"fixture-scanner","test-only",clock.instant(),clock.instant(),true,List.of(),snapshot.inventory().stream().sorted().toList());
        validation.complete(WORKER,job.job().id(),new ValidationResult(artifact.id(),snapshot.sha256(),snapshot.manifest(),scan,snapshot.inventory(),"BUILD",clock.instant()));
        var provenance=db.transaction(tx -> tx.get("build-provenance",artifact.id(),BuildProvenance.class).orElseThrow());
        assertThat(provenance.sourceDigest()).isEqualTo(job.build().sourceDigest());
        var release=new Release(1,0,1,"source-deployment",new Bundle("source-bundle",List.of(new Member("example",artifact.id(),artifact.sha256()))),null,0,"cohort",Set.of(),Set.of("node"),Hashes.sha256(new byte[0]));
        assertThatThrownBy(() -> deployments.desired(NODE,"production","source-deployment",Set.of())).hasMessage("deployment-not-found");
        execute(ActionIntent.Action.PUBLISH,1,0,List.of(),release);
        var desired=deployments.desired(NODE,"production","source-deployment",Set.of());
        var claims=ProtocolJson.read(DocumentSignatures.decode(desired.artifacts().getFirst().proof().payload()),SignedClaims.class);
        var proof=ProtocolJson.read(claims.body(),ArtifactProof.class);
        assertThat(proof.validationKind()).isEqualTo("BUILD");assertThat(proof.provenanceSha256()).isEqualTo(Hashes.sha256(ProtocolJson.write(provenance)));
    }
    @Test void accountCreationDeliversEnrollmentOnceAndLastAdminCannotBeDisabled()throws Exception {
        var created=execute(ActionIntent.Action.USER_CREATE,0,0,List.of(),new AccountManagement.Create("developer","DeveloperLoginPass!193","DeveloperOperationPass!278",false));
        var account=accounts.delivery(session,created.resourceId());assertThat(account.enrollmentToken()).isNotBlank();
        assertThatThrownBy(() -> accounts.delivery(session,created.resourceId())).hasMessage("enrollment-ticket-unavailable");
        var list=accounts.accounts(session,0,100);assertThat(list).hasSize(2);
        var admin=list.stream().filter(AccountManagement.Account::securityAdmin).findFirst().orElseThrow();
        assertThatThrownBy(() -> execute(ActionIntent.Action.USER_UPDATE,0,0,List.of(),new AccountManagement.Change(admin.id(),admin.credentialVersion(),false,false))).hasMessage("last-security-admin-protected");
        var audit=db.transaction(tx -> tx.auditPage(0,200));assertThat(audit).isNotEmpty();
        for(var entry:audit)assertThat(entry.entryHash()).isEqualTo(Hashes.sha256(ProtocolJson.write(entry.entry())));
    }
    @Test void signingKeyRotationRequiresInstalledFingerprintThenRemovalBeforeRetirement()throws Exception {
        var initial=db.transaction(tx -> signing.publicKeys(tx)).stream().filter(k -> k.purposes().contains(SignedClaims.Purpose.LEASE)).findFirst().orElseThrow();
        execute(ActionIntent.Action.TRUST_UPDATE,0,0,List.of(),new SigningService.Rotation("stage","execution","execution-next"));
        assertThatThrownBy(() -> execute(ActionIntent.Action.TRUST_UPDATE,0,0,List.of(),new SigningService.Rotation("activate","execution","execution-next"))).hasMessage("key-not-installed-on-all-nodes");
        var installed=db.transaction(tx -> signing.publicKeys(tx)).stream().collect(java.util.stream.Collectors.toMap(SigningService.PublicTrustKey::id,k -> Hashes.sha256(java.util.Base64.getDecoder().decode(k.publicKey()))));
        deployments.acknowledgeKeys(NODE,"production",installed);
        execute(ActionIntent.Action.TRUST_UPDATE,0,0,List.of(),new SigningService.Rotation("activate","execution","execution-next"));
        var envelope=deployments.policy(NODE,"production");assertThat(envelope.keyId()).isEqualTo("execution-next");
        assertThatThrownBy(() -> execute(ActionIntent.Action.TRUST_UPDATE,0,0,List.of(),new SigningService.Rotation("retire","execution",initial.id()))).hasMessage("old-key-still-installed");
        installed.remove(initial.id());deployments.acknowledgeKeys(NODE,"production",installed);
        execute(ActionIntent.Action.TRUST_UPDATE,0,0,List.of(),new SigningService.Rotation("retire","execution",initial.id()));
        assertThat(db.transaction(tx -> signing.publicKeys(tx)).stream().filter(k -> k.id().equals(initial.id())).findFirst().orElseThrow().revoked()).isTrue();
    }
    @Test void cancelledAndExpiredPreparationsCannotReuseReadyReceipts()throws Exception {
        var release=validatedRelease(Set.of("node"));
        var first=execute(ActionIntent.Action.PUBLISH,1,0,List.of(),release);
        var stale=ready(release,first.id());
        execute(ActionIntent.Action.CANCEL_RELEASE,1,0,List.of(),new DeploymentService.Cancel("deployment",first.id(),1,"replace candidate"));
        assertThat(catalog.action(session,first.id()).state()).isEqualTo("CANCELLED");
        assertThat(deployments.desired(NODE,"production","deployment",Set.of()).state()).isEqualTo("UNAVAILABLE");
        var retry=execute(ActionIntent.Action.PUBLISH,1,0,List.of(),release);
        assertThatThrownBy(() -> deployments.ready(NODE,"production",stale)).hasMessage("ready-context-mismatch");
        var current=ready(release,retry.id());
        clock.advance(601);
        assertThatThrownBy(() -> deployments.ready(NODE,"production",current)).hasMessage("preparation-unavailable");
        // The rejected ready call still commits expiry and its action receipt.
        assertThat(catalog.action(session,retry.id()).state()).isEqualTo("EXPIRED");
        var finalAttempt=execute(ActionIntent.Action.PUBLISH,1,0,List.of(),release);
        var finalReady=ready(release,finalAttempt.id());
        assertThat(deployments.ready(NODE,"production",finalReady).revision()).isEqualTo(1);
        assertThat(deployments.ready(NODE,"production",finalReady).revision()).isEqualTo(1);
        assertThat(catalog.action(session,finalAttempt.id()).state()).isEqualTo("ACTIVE");
    }
    @Test void disablingPreviouslyReadyNodePreventsMultiNodeActivation()throws Exception {
        execute(ActionIntent.Action.MACHINE_CREATE,0,0,List.of(),new MachineManagement.Create("node-two",MachineService.Kind.NODE,Set.of("production")));
        var second=machineManagement.delivery(session,"node-two");
        var release=validatedRelease(Set.of("node","node-two"));var action=execute(ActionIntent.Action.PUBLISH,1,0,List.of(),release);
        var ready=ready(release,action.id());
        assertThat(deployments.ready(NODE,"production",ready).active()).isNull();
        execute(ActionIntent.Action.MACHINE_UPDATE,0,0,List.of(),new MachineManagement.Change("node",1,Set.of("production"),false));
        assertThatThrownBy(() -> deployments.ready(second.token(),"production",ready)).hasMessage("preparation-unavailable");
        assertThat(catalog.action(session,action.id()).state()).isEqualTo("FAILED");
        assertThatThrownBy(() -> deployments.policy(NODE,"production")).hasMessage("unauthenticated");
        assertThat(deployments.desired(second.token(),"production","deployment",Set.of()).activation()).isNull();
    }
    @Test void workloadTokensAreDeliveredOnceAndRotationIsStagedAndExplicitlyActivated()throws Exception {
        execute(ActionIntent.Action.MACHINE_CREATE,0,0,List.of(),new MachineManagement.Create("new-worker",MachineService.Kind.WORKER,Set.of("test")));
        var original=machineManagement.delivery(session,"new-worker");
        assertThatThrownBy(() -> machineManagement.delivery(session,"new-worker")).hasMessage("machine-token-unavailable");
        assertThatThrownBy(() -> validation.claim(original.token(),"production")).hasMessage("forbidden");
        assertThatThrownBy(() -> deployments.policy(original.token(),"test")).hasMessage("unauthenticated");
        execute(ActionIntent.Action.MACHINE_ROTATE,0,0,List.of(),new MachineManagement.Rotate("new-worker",1));
        var staged=machineManagement.list(session,0,100).stream().filter(m -> m.id().equals("new-worker")).findFirst().orElseThrow();
        assertThatThrownBy(() -> execute(ActionIntent.Action.MACHINE_ACTIVATE,0,0,List.of(),new MachineManagement.Activate("new-worker",1,staged.rotationId()))).hasMessage("machine-token-not-delivered");
        var rotated=machineManagement.delivery(session,"new-worker");
        assertThat(validation.claim(original.token(),"test")).isNull();
        assertThatThrownBy(() -> validation.claim(rotated.token(),"test")).hasMessage("unauthenticated");
        execute(ActionIntent.Action.MACHINE_ROTATE,0,0,List.of(),new MachineManagement.Rotate("new-worker",1));
        var replacement=machineManagement.delivery(session,"new-worker");
        assertThatThrownBy(() -> execute(ActionIntent.Action.MACHINE_ACTIVATE,0,0,List.of(),new MachineManagement.Activate("new-worker",1,staged.rotationId()))).hasMessage("machine-rotation-context-mismatch");
        var current=machineManagement.list(session,0,100).stream().filter(m -> m.id().equals("new-worker")).findFirst().orElseThrow();
        execute(ActionIntent.Action.MACHINE_ACTIVATE,0,0,List.of(),new MachineManagement.Activate("new-worker",1,current.rotationId()));
        assertThatThrownBy(() -> validation.claim(original.token(),"test")).hasMessage("unauthenticated");
        assertThatThrownBy(() -> validation.claim(rotated.token(),"test")).hasMessage("unauthenticated");
        assertThat(validation.claim(replacement.token(),"test")).isNull();
        assertThat(machineManagement.list(session,0,100).stream().filter(m -> m.id().equals("new-worker")).findFirst().orElseThrow().revision()).isEqualTo(2);
    }
    @Test void canaryPrivilegeCannotReplaceStableAndPromotionMustUseCurrentCandidate()throws Exception {
        var first=validatedRelease(Set.of("node"));var initial=execute(ActionIntent.Action.PUBLISH,1,0,List.of(),first);
        deployments.ready(NODE,"production",ready(first,initial.id()));
        var candidate=new Bundle("candidate",first.stable().members());
        var bypass=new Release(2,1,1,"deployment",candidate,first.stable(),1000,"cohort",Set.of(),Set.of("node"),first.configurationSha256());
        assertThatThrownBy(() -> execute(ActionIntent.Action.CANARY,1,1,List.of(),bypass)).hasMessage("canary-must-preserve-stable-cohort");
        var canary=new Release(2,1,1,"deployment",first.stable(),candidate,1000,"cohort",Set.of(),Set.of("node"),first.configurationSha256());
        var started=execute(ActionIntent.Action.CANARY,1,1,List.of(),canary);deployments.ready(NODE,"production",ready(canary,started.id()));
        var invalid=new Release(3,2,1,"deployment",first.stable(),null,0,"cohort",Set.of(),Set.of("node"),first.configurationSha256());
        assertThatThrownBy(() -> execute(ActionIntent.Action.PROMOTE,1,2,List.of(),invalid)).hasMessage("promotion-target-mismatch");
        var promoted=new Release(3,2,1,"deployment",candidate,null,0,"cohort",Set.of(),Set.of("node"),first.configurationSha256());
        assertThatThrownBy(() -> execute(ActionIntent.Action.PROMOTE,1,2,List.of(),promoted)).hasMessage("canary-guard-required");
        execute(ActionIntent.Action.GUARD_CONFIGURE,1,2,List.of(),new CanaryGuard.Request("deployment",2,10,100,100,false,clock.instant().plusSeconds(900)));
        clock.advance(120);
        // Command authorization advances the fixture clock by 30s; deliver a report for its resulting full minute.
        long reportMinute=(clock.instant().plusSeconds(30).getEpochSecond()/60)-1;
        if(reportMinute>=clock.instant().getEpochSecond()/60)clock.advance(30);
        reportMinute=clock.instant().getEpochSecond()/60-1;
        deployments.observe(NODE,"production",new CanaryObservation("deployment",2,candidate.id(),reportMinute,100,0,0,true));
        // Keep the next OTP increment inside the same minute used by the gate.
        if(clock.instant().getEpochSecond()%60>=30){clock.advance(30);deployments.observe(NODE,"production",new CanaryObservation("deployment",2,candidate.id(),clock.instant().getEpochSecond()/60-1,100,0,0,true));}
        var promotedAction=execute(ActionIntent.Action.PROMOTE,1,2,List.of(),promoted);deployments.ready(NODE,"production",ready(promoted,promotedAction.id()));
        var rollback=new Release(4,3,1,"deployment",first.stable(),null,0,"cohort",Set.of(),Set.of("node"),first.configurationSha256());
        var rolled=execute(ActionIntent.Action.ROLLBACK,1,3,List.of(),rollback);deployments.ready(NODE,"production",ready(rollback,rolled.id()));
        assertThat(deployments.view(session,"production","deployment").revision()).isEqualTo(4);
    }
    @Test void canaryGuardRejectsReplayAndPreauthorizedRollbackStillRequiresNodePreparation()throws Exception {
        var initial=validatedRelease(Set.of("node"));var action=execute(ActionIntent.Action.PUBLISH,1,0,List.of(),initial);deployments.ready(NODE,"production",ready(initial,action.id()));
        var canary=new Release(2,1,1,"deployment",initial.stable(),new Bundle("candidate",initial.stable().members()),500,"cohort",Set.of(),Set.of("node"),initial.configurationSha256());
        var candidate=execute(ActionIntent.Action.CANARY,1,1,List.of(),canary);deployments.ready(NODE,"production",ready(canary,candidate.id()));
        execute(ActionIntent.Action.GUARD_CONFIGURE,1,2,List.of(),new CanaryGuard.Request("deployment",2,10,100,200,true,clock.instant().plusSeconds(900)));
        clock.advance(120);long minute=clock.instant().getEpochSecond()/60-1;
        assertThat(deployments.guard(session,"production","deployment").observation().state()).isEqualTo("MISSING_NODES");
        assertThatThrownBy(() -> deployments.observe(WORKER,"production",new CanaryObservation("deployment",2,"candidate",minute,100,0,0,true))).hasMessage("unauthenticated");
        var failed=new CanaryObservation("deployment",2,"candidate",minute,100,5,0,true);deployments.observe(NODE,"production",failed);deployments.observe(NODE,"production",failed);
        assertThatThrownBy(() -> deployments.observe(NODE,"production",new CanaryObservation("deployment",2,"candidate",minute,100,0,0,true))).hasMessage("observation-replay-conflict");
        var guard=deployments.guard(session,"production","deployment");assertThat(guard.state()).isEqualTo("ROLLBACK_PREPARING");
        var view=deployments.view(session,"production","deployment");assertThat(view.revision()).isEqualTo(2);assertThat(view.pending().release().revision()).isEqualTo(3);
        assertThat(view.pending().release().canary()).isNull();assertThat(view.pending().release().stable()).isEqualTo(initial.stable());
        deployments.ready(NODE,"production",ready(view.pending().release(),view.pending().actionId()));assertThat(deployments.view(session,"production","deployment").revision()).isEqualTo(3);
    }
    @Test void pendingPreparationStillAllowsIndependentServingLeaseRenewal()throws Exception {
        var initial=validatedRelease(Set.of("node"));var published=execute(ActionIntent.Action.PUBLISH,1,0,List.of(),initial);
        deployments.ready(NODE,"production",ready(initial,published.id()));
        var first=deployments.desired(NODE,"production","deployment",new Retention(Set.of(),Set.of()),true);
        var initialClaims=ProtocolJson.read(DocumentSignatures.decode(first.lease().payload()),SignedClaims.class);
        clock.advance(840);
        var next=new Release(2,1,1,"deployment",initial.stable(),null,0,"cohort",Set.of(),Set.of("node"),initial.configurationSha256());
        execute(ActionIntent.Action.PUBLISH,1,1,List.of(),next);
        var preparation=deployments.desired(NODE,"production","deployment",Set.of());assertThat(preparation.state()).isEqualTo("PREPARING");
        clock.advance(60);assertThat(clock.instant()).isAfter(initialClaims.expiresAt());
        var serving=deployments.desired(NODE,"production","deployment",new Retention(Set.of(),Set.of()),true);
        assertThat(serving.state()).isEqualTo("ACTIVE");
        var claims=ProtocolJson.read(DocumentSignatures.decode(serving.lease().payload()),SignedClaims.class);
        assertThat(ProtocolJson.read(claims.body(),Lease.class).releaseRevision()).isEqualTo(1);
        assertThat(claims.expiresAt()).isAfter(clock.instant());
        int leases=db.transaction(tx -> tx.list("node-lease",NodeLeaseRetention.class).size());assertThat(leases).isEqualTo(2);
    }
    private Release validatedRelease(Set<String> nodes)throws Exception {
        byte[] bytes=jar();var snapshot=ArchiveSnapshot.inspect(bytes,ResourcePolicy.defaults(),Map.of());
        var upload=execute(ActionIntent.Action.UPLOAD,1,1,List.of(new ActionIntent.FileIntent("example.jar",snapshot.sha256(),bytes.length)),Map.of()).resourceId();
        catalog.uploadChunk(session,upload,0,0,bytes);var artifact=catalog.completeUpload(session,upload).getFirst();
        execute(ActionIntent.Action.VALIDATE,1,1,List.of(),artifact.id());var assignment=validation.claim(WORKER,"production");
        var scan=new ScanReport(snapshot.sha256(),"fixture-scanner","test-only",clock.instant(),clock.instant(),true,List.of(),snapshot.inventory().stream().sorted().toList());
        validation.complete(WORKER,assignment.job().id(),new ValidationResult(artifact.id(),snapshot.sha256(),snapshot.manifest(),scan,snapshot.inventory(),"IMPORT",clock.instant()));
        return new Release(1,0,1,"deployment",new Bundle("initial-bundle",List.of(new Member("example",artifact.id(),snapshot.sha256()))),null,0,"cohort",Set.of(),nodes,Hashes.sha256(new byte[0]));
    }
    private Ready ready(Release release,String preparationId) {
        return new Ready(release.deploymentId(),preparationId,release.revision(),Hashes.sha256(ProtocolJson.write(release)),
                release.bundles().stream().flatMap(b -> b.members().stream()).map(Member::sha256).collect(java.util.stream.Collectors.toSet()));
    }
    private ActionReceipt execute(ActionIntent.Action action,long groupRevision,long expected,List<ActionIntent.FileIntent> files,Object body) throws Exception {
        byte[] parameters=ProtocolJson.write(body);var intent=intent(action,groupRevision,expected,files,parameters);
        var challenge=identities.challenge(session,intent);clock.advance(30);
        var grant=identities.authorize(session,challenge.id(),OPERATION,otp());
        return catalog.execute(session,new Command("cmd-"+UUID.randomUUID(),grant.token(),intent,parameters));
    }
    private ActionIntent intent(ActionIntent.Action action,long groupRevision,long expected,List<ActionIntent.FileIntent> files,byte[] parameters) {
        return new ActionIntent(action,"production","general",groupRevision,"example",expected,1,files,Hashes.sha256(parameters));
    }
    private String otp() throws Exception {return String.format(java.util.Locale.ROOT,"%06d",new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30),6).generateOneTimePassword(new SecretKeySpec(new Base32().decode(secret),"HmacSHA1"),clock.instant()));}
    private byte[] jar() throws Exception {
        Path source=dir.resolve("Example.java");Files.writeString(source,"package example; import com.sma.extension.api.*; public class Example implements BusinessPlugin { public PluginResult execute(PluginCall c) { return new PluginResult(\"\\\"loaded\\\"\".getBytes(java.nio.charset.StandardCharsets.UTF_8)); } }");
        String sdk=Path.of(BusinessPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        assertThat(ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","21","-proc:none","-classpath",sdk,"-d",dir.toString(),source.toString())).isZero();
        var manifest=new ArtifactManifest(1,"example","1.0.0",1,"example.Example",List.of(new ArtifactManifest.Entry("execute",ArtifactManifest.EntryKind.SERVICE,"v1","extension:invoke:example",Set.of("USER"),false)),List.of(),Set.of(),Set.of());
        var bytes=new ByteArrayOutputStream();try(var jar=new JarOutputStream(bytes)) {
            jar.putNextEntry(new JarEntry(ArtifactManifest.PATH));jar.write(ProtocolJson.write(manifest));jar.closeEntry();
            jar.putNextEntry(new JarEntry("example/Example.class"));jar.write(Files.readAllBytes(dir.resolve("example/Example.class")));jar.closeEntry();
        }return bytes.toByteArray();
    }
    private static final class MutableClock extends Clock {
        private Instant value=Instant.parse("2026-11-11T00:00:00Z");void advance(long seconds){value=value.plusSeconds(seconds);}
        public ZoneId getZone(){return ZoneId.of("UTC");}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return value;}
    }
}
