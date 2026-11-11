package com.sma.admin.extension.internal.service;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;
import com.sma.admin.common.BizException;
import com.sma.admin.common.extension.ExtensionCapability;
import com.sma.admin.common.extension.ExtensionJobPolicy;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.common.security.RuntimePermissionRegistrar;
import com.sma.admin.extension.BusinessExtensions;
import com.sma.admin.extension.ExtensionErrorCode;
import com.sma.admin.extension.ExtensionWork;
import com.sma.extension.api.*;
import com.sma.extension.protocol.*;
import com.sma.extension.protocol.ExecutionDocuments.*;
import com.sma.extension.runtime.*;

@Service
public class ExtensionRuntimeService implements BusinessExtensions {
    private final ExtensionProperties properties;
    private final NodeControlClient control;
    private final HostCheckpoint checkpoint;
    private final ExtensionWorkJournal journal;
    private final RuntimeAdmission admission;
    private final RuntimePermissionRegistrar permissionRegistrar;
    private final Map<String,String> registeredPermissions=new ConcurrentHashMap<>();
    private final Map<String,ExtensionCapability> capabilities;
    private final Map<String,ExtensionJobPolicy> jobPolicies;
    private final ExtensionTelemetry telemetry;
    private volatile PluginRuntime runtime;
    private volatile String lastSync="NOT_CONFIGURED";
    private volatile Instant lastSuccess;
    private String trustDigest;
    public ExtensionRuntimeService(ExtensionProperties properties,NodeControlClient control,HostCheckpoint checkpoint,ExtensionWorkJournal journal,RuntimeAdmission admission,RuntimePermissionRegistrar permissionRegistrar,List<ExtensionCapability> capabilities,List<ExtensionJobPolicy> jobPolicies,ExtensionTelemetry telemetry) {
        this.properties=properties;this.control=control;this.checkpoint=checkpoint;this.journal=journal;this.admission=admission;this.permissionRegistrar=permissionRegistrar;this.telemetry=telemetry;
        this.capabilities=capabilities.stream().collect(Collectors.toUnmodifiableMap(ExtensionCapability::id,c -> c));
        this.jobPolicies=jobPolicies.stream().collect(Collectors.toUnmodifiableMap(ExtensionJobPolicy::id,p -> p));
    }
    public record PublicKeySpec(String id,String environment,String algorithm,String publicKey,Instant notBefore,Instant notAfter,Set<SignedClaims.Purpose> purposes,boolean revoked) {}
    public record Status(boolean enabled,String lastSync,Instant lastSuccess,List<PluginRuntime.Status> versions) {}
    public Status status() { var current=runtime;return new Status(properties.enabled(),lastSync,lastSuccess,current==null?List.of():current.statuses()); }
    public ExtensionTelemetry.Snapshot metrics(){return telemetry.snapshot();}
    public synchronized void synchronize() {
        if(!properties.enabled()) return;
        try {
            var running=runtime();
            refreshTrust(running);
            control.acknowledgeKeys(running.trustFingerprints());
            admission.sample();
            running.applyRevocations(control.revocations());
            running.applyPolicy(control.policy());var policy=running.policy();
            for(String deployment:properties.deployments()) {
                // Renew/restore serving traffic first, including a node returning near its old lease deadline.
                // Pending preparation must never consume the only renewal opportunity of the stable release.
                var serving=control.serving(deployment,journal.retained(deployment));
                if(serving.activation()!=null){preparePackage(running,policy,deployment,serving);running.activate(serving.activation());}
                var desired=control.desired(deployment,journal.retained(deployment));
                Checks.require(desired.lease()!=null && desired.preparation()!=null,"deployment-unavailable");
                preparePackage(running,policy,deployment,desired);
                if(desired.state().equals("PREPARING")) {
                    // Verified preparation is read again to produce an exact ready receipt; it is never an activation permit.
                    var releaseClaims=ProtocolJson.read(DocumentSignatures.decode(desired.preparation().payload()),SignedClaims.class);
                    var release=ProtocolJson.read(releaseClaims.body(),Release.class);
                    control.ready(new NodeControlClient.Ready(deployment,desired.preparationId(),release.revision(),Hashes.sha256(ProtocolJson.write(release)),release.bundles().stream().flatMap(b -> b.members().stream()).map(Member::sha256).collect(Collectors.toSet())));
                    desired=control.desired(deployment,journal.retained(deployment));running.renewLease(desired.lease());
                }
                if(desired.activation()!=null) {
                    running.activate(desired.activation());
                    var active=ProtocolJson.read(ProtocolJson.read(DocumentSignatures.decode(desired.activation().payload()),SignedClaims.class).body(),Release.class);
                    if(active.canary()!=null){var candidates=active.canary().members().stream().map(Member::sha256).collect(Collectors.toSet());
                        boolean idle=running.statuses().stream().noneMatch(s -> candidates.contains(s.sha256()) && s.inFlight()>0);
                        control.observe(telemetry.observation(deployment,active.revision(),active.canary().id(),idle));}
                }
            }
            running.reap();
            registerPermissions(running);
            lastSync="SUCCESS";lastSuccess=Instant.now();
        } catch(InterruptedException interrupted) { Thread.currentThread().interrupt();lastSync="INTERRUPTED"; }
        catch(Exception | LinkageError failure) { lastSync=failure instanceof ProtocolException protocol?protocol.code():"CONTROL_UNAVAILABLE"; }
    }
    private void preparePackage(PluginRuntime running,ResourcePolicy policy,String deployment,NodeControlClient.NodePackage desired)throws Exception {
        running.renewLease(desired.lease());
        for(var artifact:desired.artifacts()) {
            if(running.statuses().stream().noneMatch(s -> s.sha256().equals(artifact.sha256())))admission.requireCapacity(artifact.bytes(),policy);
            running.prepare(control.download(deployment,artifact,policy),artifact.proof(),artifact.preparation()==null?desired.preparation():artifact.preparation());
        }
    }
    @Override public byte[] invoke(String deploymentId,String pluginId,String entryId,byte[] json) {
        CurrentUser.require();return invokeScoped(deploymentId,pluginId,entryId,json);
    }
    byte[] invokeScoped(String deploymentId,String pluginId,String entryId,byte[] json) {
        var identity=WorkCaller.current().identity();
        try {
            var running=runtime();
            if(identity.kind()==InvocationIdentity.Kind.USER)registerPermissions(running);
            var selection=running.select(deploymentId,identity);
            return running.invoke(selection,pluginId,entryId,identity,null,json,Instant.now().plusSeconds(30)).json();
        } catch(ProtocolException failure) { throw new BizException(ExtensionErrorCode.REJECTED,failure.code()); }
        catch(BizException business) {throw business;}
        catch(org.springframework.security.access.AccessDeniedException denied) { throw denied; }
        catch(Exception failure) { throw new BizException(ExtensionErrorCode.EXECUTION_FAILED); }
    }
    @Override public ExtensionWork invokeWork(String deploymentId,String pluginId,String entryId,String workKey,byte[] json) {
        var user=WorkCaller.current();
        try{ProtocolJson.validateValue(json);Checks.id(deploymentId);Checks.id(pluginId);Checks.id(entryId);Checks.text(workKey,200);}
        catch(ProtocolException failure){throw new BizException(ExtensionErrorCode.REJECTED,failure.code());}
        String inputHash=Hashes.sha256(json);
        var identity=user.identity();
        try {
            var existing=journal.find(deploymentId,pluginId,entryId,workKey);
            if(existing!=null) {
                Checks.require(existing.inputHash().equals(inputHash),"work-input-conflict");
                if(!existing.state().equals("RECEIVED"))return journal.view(existing);
            }
            var running=runtime();if(user.kind()==InvocationIdentity.Kind.USER)registerPermissions(running);ExtensionWorkJournal.Saved saved=existing;
            if(saved==null) {
                var selection=running.select(deploymentId,identity);
                try {saved=running.registerWork(selection,pluginId,entryId,identity,(members,entry) -> journal.accept(deploymentId,pluginId,entryId,entry.permission(),workKey,inputHash,selection,members));}
                catch(org.springframework.dao.DuplicateKeyException raced) {
                    saved=journal.find(deploymentId,pluginId,entryId,workKey);Checks.require(saved!=null && saved.inputHash().equals(inputHash),"work-input-conflict");
                }
            }
            var claimed=journal.claim(saved.id());
            if(claimed.claimId()==null || !claimed.state().equals("RUNNING"))return journal.view(claimed);
            try {
                byte[] result=running.invoke(claimed.selection(),pluginId,entryId,identity,"work:"+claimed.id(),json,Instant.now().plusSeconds(30)).json();
                journal.succeeded(claimed.id(),claimed.claimId(),result);
            } catch(Exception failure) {
                // No automatic re-execution after an uncertain external side effect or commit.
                journal.unknown(claimed.id(),claimed.claimId(),failure instanceof ProtocolException protocol?protocol.code():"execution-outcome-unknown");
            }
            return journal.view(journal.get(claimed.id()));
        } catch(ProtocolException failure){throw new BizException(ExtensionErrorCode.REJECTED,failure.code());}
        catch(BizException business){throw business;}
        catch(org.springframework.security.access.AccessDeniedException denied){throw denied;}
        catch(Exception failure){throw new BizException(ExtensionErrorCode.EXECUTION_FAILED);}
    }
    @Override public ExtensionWork work(long id){try{return journal.view(journal.get(id));}catch(ProtocolException failure){throw new BizException(ExtensionErrorCode.REJECTED,failure.code());}}
    private void registerPermissions(PluginRuntime running) {
        var keys=running.permissionKeys();String digest=Hashes.sha256(ProtocolJson.write(keys));
        String context=CurrentUser.tenantId()+":"+CurrentUser.portal();
        if(digest.equals(registeredPermissions.get(context)))return;
        permissionRegistrar.extensionEntries(keys);
        if(registeredPermissions.size()>=1000)registeredPermissions.clear();registeredPermissions.put(context,digest);
    }
    @Override public ExtensionWork invokeJob(String policyId,long trustedTenantId,String objectId) {
        Checks.id(policyId);Checks.text(objectId,200);var policy=jobPolicies.get(policyId);Checks.require(policy!=null,"job-policy-unavailable");
        return WorkCaller.job(trustedTenantId,policyId,policy.permissions(),() -> {
            var job=policy.resolve(objectId);Checks.require(job!=null,"job-object-unavailable");
            return invokeWork(job.deploymentId(),job.pluginId(),job.entryId(),job.workKey(),job.input());
        });
    }
    private synchronized PluginRuntime runtime() throws Exception {
        if(!properties.enabled()) throw new BizException(ExtensionErrorCode.UNAVAILABLE);
        if(runtime!=null) return runtime;
        byte[] trustBytes=readTrust();var trust=parseTrust(trustBytes);trustDigest=Hashes.sha256(trustBytes);
        runtime=new PluginRuntime(properties.issuer(),properties.audience(),properties.environment(),properties.nodeId(),Clock.systemUTC(),ResourcePolicy.defaults(),trust,new RuntimeHost() {
            @Override public void authorize(String plugin,ArtifactManifest.Entry entry,InvocationIdentity identity) {
                var user=WorkCaller.current();
                if(!user.identity().equals(identity) || !user.permissions().contains(entry.permission()))
                    throw new org.springframework.security.access.AccessDeniedException("Extension entry permission required");
            }
            @Override public byte[] capability(String plugin,String version,String invocation,InvocationIdentity identity,String capability,byte[] input) throws Exception {
                var adapter=capabilities.get(capability);Checks.require(adapter!=null,"capability-unavailable");
                return adapter.invoke(plugin,version,invocation,identity,input);
            }
            @Override public void metric(String plugin,String version,String metric,long increment) { telemetry.metric(plugin,version,metric,increment); }
            @Override public void observe(InvocationObservation observation){telemetry.observe(observation);}
            @Override public long durableReferences(String hash) { return journal.references(hash); }
        },checkpoint);
        return runtime;
    }
    private byte[] readTrust()throws Exception {
        Checks.require(Files.isRegularFile(properties.trustFile(),LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(properties.trustFile())
                && Files.size(properties.trustFile())<=65536,"invalid-trust-file");
        try(var input=Files.newInputStream(properties.trustFile(),LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes=input.readNBytes(65537);Checks.require(bytes.length<=65536,"invalid-trust-file");return bytes;
        }
    }
    private Map<String,DocumentSignatures.TrustedKey> parseTrust(byte[] bytes)throws Exception {
        var keys=ProtocolJson.read(bytes,PublicKeySpec[].class);var trust=new java.util.HashMap<String,DocumentSignatures.TrustedKey>();
        Checks.require(keys.length<=100,"invalid-trust-file");
        for(var key:keys) {
            Checks.require("Ed25519".equals(key.algorithm()) && !trust.containsKey(key.id()),"invalid-trust-file");
            if(key.revoked() || !properties.environment().equals(key.environment()))continue;
            var publicKey=KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(java.util.Base64.getDecoder().decode(key.publicKey())));
            trust.put(key.id(),new DocumentSignatures.TrustedKey(publicKey,key.purposes(),key.notBefore(),key.notAfter()));
        }
        Checks.require(!trust.isEmpty(),"invalid-trust-file");return Map.copyOf(trust);
    }
    private void refreshTrust(PluginRuntime running)throws Exception {
        byte[] bytes=readTrust();String digest=Hashes.sha256(bytes);if(digest.equals(trustDigest))return;
        running.replaceTrustedKeys(parseTrust(bytes));trustDigest=digest;
    }
    @PreDestroy public synchronized void close() throws Exception { if(runtime!=null) runtime.close(); }
}
