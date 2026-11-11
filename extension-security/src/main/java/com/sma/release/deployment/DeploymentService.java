package com.sma.release.deployment;

import static com.sma.release.deployment.DeploymentRecords.*;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.sma.extension.protocol.*;
import com.sma.extension.protocol.ExecutionDocuments.*;
import com.sma.release.auth.IdentityService;
import com.sma.release.auth.MachineService;
import com.sma.release.auth.SecurityRecords;
import com.sma.release.catalog.ArtifactFiles;
import com.sma.release.catalog.CatalogRecords.*;
import com.sma.release.crypto.SigningService;
import com.sma.release.store.ReleaseDatabase;

@Service
public class DeploymentService {
    private final ReleaseDatabase database;
    private final IdentityService identities;
    private final MachineService machines;
    private final SigningService signing;
    private final ValidationService validation;
    private final ArtifactFiles files;
    private final com.sma.release.store.AuditContinuity auditContinuity;
    public DeploymentService(ReleaseDatabase database, IdentityService identities, MachineService machines, SigningService signing, ValidationService validation, ArtifactFiles files,com.sma.release.store.AuditContinuity auditContinuity) {
        this.database=database; this.identities=identities; this.machines=machines; this.signing=signing; this.validation=validation; this.files=files;
        this.auditContinuity=auditContinuity;
    }
    public record PreparationBinding(String deploymentKey, long revision, java.util.Map<String,Long> groupRevisions, java.util.Map<String,Long> pluginRevisions,java.util.Map<String,Long> nodeRevisions) {
        public PreparationBinding { groupRevisions=java.util.Map.copyOf(groupRevisions); pluginRevisions=java.util.Map.copyOf(pluginRevisions);nodeRevisions=java.util.Map.copyOf(nodeRevisions); }
    }
    public String propose(ReleaseDatabase.Tx tx, SecurityRecords.Principal principal, ActionIntent intent, Release release, String actionId) {
        auditContinuity.requireHealthy(tx);
        Checks.require(release.previousRevision()==intent.expectedRevision() && release.policyRevision()==intent.policyRevision(),"release-context-mismatch");
        String key=key(intent.environment(),release.deploymentId());
        var old=tx.get("deployment",key,Deployment.class).orElse(new Deployment(release.deploymentId(),intent.environment(),0,null,null));
        old=reconcile(tx,old);
        Checks.require(old.revision()==release.previousRevision() && release.revision()==old.revision()+1,"release-revision-conflict");
        requireActionSemantics(tx,intent,old,release);
        Checks.require(old.pending()==null || !old.pending().state().equals("PREPARING"),"release-already-pending");
        Checks.require(tx.list("deployment",Deployment.class).stream().noneMatch(d -> d.environment().equals(intent.environment()) && d.pending()!=null
                && tx.now().isBefore(d.pending().expiresAt()) && d.pending().state().equals("PREPARING")),"environment-release-busy");
        var groupRevisions=new java.util.TreeMap<String,Long>(); var pluginRevisions=new java.util.TreeMap<String,Long>();
        boolean ownsPrimaryGroup=false;
        for (var member: members(release)) {
            var artifact=artifact(tx,member.artifactId());
            var plugin=tx.get("plugin",member.pluginId(),Plugin.class).orElseThrow(() -> new ProtocolException("plugin-not-found"));
            var group=tx.get("group",plugin.groupId(),Group.class).orElseThrow();
            Checks.require(group.enabled() && artifact.pluginId().equals(member.pluginId()) && artifact.sha256().equals(member.sha256())
                    && artifact.environment().equals(intent.environment()),"release-artifact-mismatch");
            if(group.id().equals(intent.groupId())) { Checks.require(group.revision()==intent.groupRevision(),"group-revision-conflict"); ownsPrimaryGroup=true; }
            identities.requireScope(tx,principal,new ActionIntent(intent.action(),intent.environment(),group.id(),group.revision(),plugin.id(),intent.expectedRevision(),intent.policyRevision(),List.of(),intent.parametersSha256()));
            requireValidated(tx,artifact);
            groupRevisions.put(group.id(),group.revision()); pluginRevisions.put(plugin.id(),plugin.revision());
        }
        Checks.require(ownsPrimaryGroup,"release-primary-group-mismatch");
        var nodeRevisions=new java.util.TreeMap<String,Long>();
        for(String node:release.targetNodes()) {
            var machine=tx.get("machine",node,MachineService.Machine.class).orElseThrow(() -> new ProtocolException("node-not-found"));
            Checks.require(machine.enabled() && machine.kind()==MachineService.Kind.NODE && machine.environments().contains(intent.environment()),"node-not-allowed");
            nodeRevisions.put(node,machine.revision());
        }
        // A bundle id cannot later mean a different vector, even across cold recovery.
        for(var bundle:release.bundles()) {
            var previous=tx.get("bundle",key(intent.environment(),bundle.id()),Bundle.class);
            Checks.require(previous.isEmpty() || previous.get().equals(bundle),"bundle-id-content-conflict");
            tx.put("bundle",key(intent.environment(),bundle.id()),bundle);
        }
        tx.put("preparation",key,new PreparationBinding(key,release.revision(),groupRevisions,pluginRevisions,nodeRevisions));
        tx.put("deployment",key,new Deployment(old.id(),old.environment(),old.revision(),old.active(),new Pending(release,principal.userId(),principal.sessionId(),principal.credentialVersion(),intent.action(),tx.now().plusSeconds(600),Set.of(),"PREPARING",actionId)));
        tx.audit(principal.userId(),"release-prepare",release.deploymentId(),"awaiting-nodes",Hashes.sha256(ProtocolJson.write(release))); return release.deploymentId();
    }
    public NodePackage desired(String token,String environment,String deploymentId,Set<String> retainedHashes) {
        return desired(token,environment,deploymentId,new Retention(retainedHashes,Set.of()));
    }
    public NodePackage desired(String token,String environment,String deploymentId,Retention retained) {
        return desired(token,environment,deploymentId,retained,false);
    }
    public NodePackage desired(String token,String environment,String deploymentId,Retention retained,boolean activeOnly) {
        return database.transaction(tx -> {
            var node=machines.authenticate(tx,token,MachineService.Kind.NODE,environment);
            var deployment=reconcile(tx,deployment(tx,environment,deploymentId));
            boolean preparing=!activeOnly && deployment.pending()!=null && deployment.pending().state().equals("PREPARING") && tx.now().isBefore(deployment.pending().expiresAt());
            Release release=preparing?deployment.pending().release():deployment.active();
            if(release==null || activeOnly && !release.targetNodes().contains(node.id())) return new NodePackage("UNAVAILABLE",null,null,null,null,List.of());
            Checks.require(release.targetNodes().contains(node.id()),"deployment-unavailable");
            var deliveries=new java.util.ArrayList<ArtifactDelivery>(); Set<String> hashes=new HashSet<>(); Instant expiry=tx.now().plusSeconds(900);
            for(var member:members(release)) {
                var artifact=artifact(tx,member.artifactId()); var result=requireValidated(tx,artifact);
                var gate=validation.gate(tx,artifact,result); if(gate.validUntil().isBefore(expiry)) expiry=gate.validUntil();
                deliveries.add(delivery(tx,artifact,result,null));
                hashes.add(artifact.sha256());
            }
            // Retention cannot authorize arbitrary uploaded code. Only previously activated vectors for this deployment qualify.
            var historical=tx.list("history",History.class).stream().filter(h -> h.environment().equals(environment) && h.release().deploymentId().equals(deploymentId))
                    .flatMap(h -> h.release().bundles().stream()).distinct().toList();
            var matching=retained.bundleIds().stream().map(id -> historical.stream().filter(b -> b.id().equals(id)).findFirst()
                    .orElseThrow(() -> new ProtocolException("unknown-retained-bundle"))).toList();
            Checks.require(matching.stream().flatMap(b -> b.members().stream()).map(Member::sha256).collect(java.util.stream.Collectors.toSet()).equals(retained.artifactHashes()),"retained-vector-mismatch");
                for(var bundle:matching) {
                    if(release.bundles().contains(bundle))continue;
                    // A stale/revoked old work version must not prevent the healthy current version from renewing its lease.
                    boolean available=bundle.members().stream().allMatch(m -> {
                        try{requireValidated(tx,artifact(tx,m.artifactId()));return true;}catch(ProtocolException denied){return false;}
                    });
                    if(!available)continue;
                    var restoration=new Release(release.revision(),release.previousRevision(),release.policyRevision(),release.deploymentId(),bundle,null,0,release.bucketSalt(),Set.of(),Set.of(node.id()),release.configurationSha256());
                    Instant until=tx.now().plusSeconds(900);
                    for(var member:bundle.members()) {
                        var artifact=artifact(tx,member.artifactId());var result=requireValidated(tx,artifact);var valid=validation.gate(tx,artifact,result).validUntil();
                        if(valid.isBefore(until))until=valid;
                    }
                    if(until.isBefore(expiry))expiry=until;
                    var preparation=signing.sign(tx,SignedClaims.Purpose.PREPARE,environment,restoration,until);
                    for(var member:bundle.members()) {
                        var artifact=artifact(tx,member.artifactId());var result=requireValidated(tx,artifact);
                        deliveries.add(delivery(tx,artifact,result,preparation));hashes.add(member.sha256());
                    }
            }
            var revoked=tx.list("revoked",Revoked.class).stream().map(Revoked::sha256).collect(java.util.stream.Collectors.toSet());
            var lease=new Lease(node.id(),deploymentId,release.revision(),hashes,revoked);
            tx.put("node-retention",key(environment,deploymentId)+":"+node.id(),new NodeRetention(node.id(),environment,deploymentId,retained,hashes,expiry));
            String leaseId=key(environment,deploymentId)+":"+node.id()+":"+release.revision();
            tx.put("node-lease",leaseId,new NodeLeaseRetention(leaseId,environment,hashes,expiry));
            Instant releaseExpiry=preparing && deployment.pending().expiresAt().isBefore(expiry)?deployment.pending().expiresAt():expiry;
            return new NodePackage(preparing?"PREPARING":"ACTIVE",preparing?deployment.pending().actionId():null, signing.sign(tx,SignedClaims.Purpose.PREPARE,environment,release,releaseExpiry),
                    preparing?null:signing.sign(tx,SignedClaims.Purpose.RELEASE,environment,release,releaseExpiry),
                    signing.sign(tx,SignedClaims.Purpose.LEASE,environment,lease,expiry),deliveries);
        });
    }
    public record History(String environment,Release release) {}
    public record PendingView(String actionId,String state,Instant expiresAt,Set<String> readyNodes,Release release) {}
    public record DeploymentView(String id,String environment,long revision,Release active,PendingView pending) {}
    public List<DeploymentView> list(String session,String environment,int offset,int limit) {
        Checks.id(environment);Checks.require(offset>=0 && offset<=100000 && limit>0 && limit<=200,"invalid-page");
        return database.transaction(tx -> {
            var actor=identities.principal(tx,session);
            return tx.list("deployment",Deployment.class).stream().filter(d -> d.environment().equals(environment) && readable(tx,actor,d)).skip(offset).limit(limit)
                    .map(d -> view(reconcile(tx,d))).toList();
        });
    }
    public DeploymentView view(String session,String environment,String id) {
        return database.transaction(tx -> {var actor=identities.principal(tx,session);var value=deployment(tx,environment,id);
            Checks.require(readable(tx,actor,value),"forbidden");return view(reconcile(tx,value));});
    }
    public List<Release> history(String session,String environment,String id) {
        return database.transaction(tx -> {var actor=identities.principal(tx,session);Checks.require(readable(tx,actor,deployment(tx,environment,id)),"forbidden");
            return tx.list("history",History.class).stream().filter(h -> h.environment().equals(environment) && h.release().deploymentId().equals(id))
                    .filter(h -> readable(tx,actor,new Deployment(id,environment,h.release().revision(),h.release(),null)))
                    .map(History::release).sorted(java.util.Comparator.comparingLong(Release::revision).reversed()).limit(200).toList();});
    }
    private static DeploymentView view(Deployment value) {
        var p=value.pending();return new DeploymentView(value.id(),value.environment(),value.revision(),value.active(),p==null?null:new PendingView(p.actionId(),p.state(),p.expiresAt(),p.readyNodes(),p.release()));
    }
    private static boolean readable(ReleaseDatabase.Tx tx,SecurityRecords.Principal actor,Deployment deployment) {
        if(actor.securityAdmin())return true;
        var grants=tx.list("scope",SecurityRecords.ScopeGrant.class).stream().filter(g -> g.userId().equals(actor.userId()) && g.environment().equals(deployment.environment()) && g.read()).map(SecurityRecords.ScopeGrant::groupId).collect(java.util.stream.Collectors.toSet());
        var releases=new java.util.ArrayList<Release>();if(deployment.active()!=null)releases.add(deployment.active());if(deployment.pending()!=null)releases.add(deployment.pending().release());
        return !releases.isEmpty() && releases.stream().flatMap(r -> members(r).stream()).allMatch(m -> tx.get("plugin",m.pluginId(),Plugin.class).map(p -> grants.contains(p.groupId())).orElse(false));
    }
    public SignedEnvelope revocations(String token,String environment) {
        return database.transaction(tx -> {
            machines.authenticate(tx,token,MachineService.Kind.NODE,environment);
            var hashes=tx.list("revoked",Revoked.class).stream().map(Revoked::sha256).collect(java.util.stream.Collectors.toSet());
            return signing.sign(tx,SignedClaims.Purpose.REVOCATIONS,environment,new RevocationList(hashes),tx.now().plusSeconds(900));
        });
    }
    public SignedEnvelope policy(String token,String environment) {
        return database.transaction(tx -> {machines.authenticate(tx,token,MachineService.Kind.NODE,environment);
            return signing.sign(tx,SignedClaims.Purpose.POLICY,environment,validation.policy(tx),tx.now().plusSeconds(900));});
    }
    public void acknowledgeKeys(String token,String environment,java.util.Map<String,String> fingerprints) {
        database.transaction(tx -> {var node=machines.authenticate(tx,token,MachineService.Kind.NODE,environment);signing.acknowledge(tx,node.id(),environment,fingerprints);return null;});
    }
    public String rotateKeys(ReleaseDatabase.Tx tx,com.sma.release.auth.SecurityRecords.Principal actor,String environment,SigningService.Rotation request) {
        Checks.require(actor.securityAdmin(),"forbidden");return signing.rotate(tx,environment,request);
    }
    public void initializeEnvironments(ReleaseDatabase.Tx tx){signing.initialize(tx);}
    public Deployment ready(String token,String environment,Ready ready) {
        var outcome=database.transaction(tx -> {
            var node=machines.authenticate(tx,token,MachineService.Kind.NODE,environment); var deployment=deployment(tx,environment,ready.deploymentId());
            // A timed-out response may be retried after activation. It must refer to the exact original action and vector.
            if(deployment.active()!=null && deployment.active().revision()==ready.revision()
                    && tx.get("activation-action",key(environment,deployment.id()),String.class).filter(ready.preparationId()::equals).isPresent()) {
                var active=deployment.active();
                Checks.require(active.targetNodes().contains(node.id()) && ready.releaseSha256().equals(Hashes.sha256(ProtocolJson.write(active)))
                        && ready.artifactHashes().equals(members(active).stream().map(Member::sha256).collect(java.util.stream.Collectors.toSet())),"ready-context-mismatch");
                return new ReadyOutcome(deployment,null);
            }
            deployment=reconcile(tx,deployment);
            if(deployment.pending()==null || !deployment.pending().state().equals("PREPARING"))return new ReadyOutcome(null,"preparation-unavailable");
            checkPending(tx,deployment); var pending=deployment.pending(); var release=pending.release();
            var required=members(release).stream().map(Member::sha256).collect(java.util.stream.Collectors.toSet());
            Checks.require(pending.actionId().equals(ready.preparationId()) && release.targetNodes().contains(node.id()) && ready.revision()==release.revision()
                    && ready.releaseSha256().equals(Hashes.sha256(ProtocolJson.write(release))) && ready.artifactHashes().equals(required),"ready-context-mismatch");
            for(var member:members(release)) requireValidated(tx,artifact(tx,member.artifactId()));
            Set<String> readyNodes=new HashSet<>(pending.readyNodes()); readyNodes.add(node.id());
            if(readyNodes.containsAll(release.targetNodes())) {
                auditContinuity.requireHealthy(tx);
                var active=new Deployment(deployment.id(),environment,release.revision(),release,null);
                tx.put("deployment",key(environment,deployment.id()),active);
                tx.put("history",key(environment,deployment.id())+":"+release.revision(),new History(environment,release));
                for(var member:members(release))tx.put("publication-time",environment+":"+member.artifactId(),tx.now());
                tx.put("activation-action",key(environment,deployment.id()),pending.actionId());
                var action=tx.get("action",pending.actionId(),ActionReceipt.class).orElseThrow();
                tx.put("action",action.id(),new ActionReceipt(action.id(),action.userId(),action.intentHash(),"ACTIVE",action.resourceId(),action.createdAt()));
                tx.audit(pending.actorId(),"release-activate",deployment.id(),"active",ready.releaseSha256()); return new ReadyOutcome(active,null);
            }
            var updated=new Deployment(deployment.id(),environment,deployment.revision(),deployment.active(),new Pending(release,pending.actorId(),pending.actorSessionId(),pending.actorCredentialVersion(),pending.action(),pending.expiresAt(),readyNodes,pending.state(),pending.actionId()));
            tx.put("deployment",key(environment,deployment.id()),updated); return new ReadyOutcome(updated,null);
        });
        if(outcome.failure()!=null)throw new ProtocolException(outcome.failure());return outcome.deployment();
    }
    private record ReadyOutcome(Deployment deployment,String failure) {}
    private static void requireActionSemantics(ReleaseDatabase.Tx tx,ActionIntent intent,Deployment old,Release next) {
        var active=old.active();
        switch(intent.action()) {
            case PUBLISH -> Checks.require(next.canary()==null,"publish-requires-stable-release");
            case CANARY -> Checks.require(active!=null && next.stable().equals(active.stable()) && next.canary()!=null
                    && next.bucketSalt().equals(active.bucketSalt()) && next.targetNodes().equals(active.targetNodes()),"canary-must-preserve-stable-cohort");
            case PROMOTE -> Checks.require(active!=null && active.canary()!=null && next.stable().equals(active.canary()) && next.canary()==null
                    && next.targetNodes().equals(active.targetNodes()),"promotion-target-mismatch");
            case ROLLBACK -> Checks.require(next.canary()==null && tx.list("history",History.class).stream().anyMatch(h -> h.environment().equals(intent.environment())
                    && h.release().deploymentId().equals(next.deploymentId()) && h.release().stable().equals(next.stable())),"rollback-target-not-previously-stable");
            default -> throw new ProtocolException("invalid-release-action");
        }
        if(intent.action()==ActionIntent.Action.PROMOTE || intent.action()==ActionIntent.Action.CANARY && next.canaryBasisPoints()>1000) {
            var guard=tx.get("canary-guard",key(intent.environment(),next.deploymentId()),CanaryGuard.Binding.class).orElseThrow(() -> new ProtocolException("canary-guard-required"));
            Checks.require(active!=null && guard.request().revision()==active.revision() && guard.state().equals("ARMED") && tx.now().isBefore(guard.request().expiresAt())
                    && Hashes.sha256(ProtocolJson.write(active)).equals(guard.releaseHash()),"canary-guard-unavailable");
            Checks.require(assessGuard(tx,guard,active).state().equals("HEALTHY"),"canary-gate-not-healthy");
            if(intent.action()==ActionIntent.Action.CANARY)Checks.require(java.util.Objects.equals(active.canary(),next.canary()),"new-candidate-requires-low-weight");
        }
    }
    public String configureGuard(ReleaseDatabase.Tx tx,SecurityRecords.Principal actor,ActionIntent intent,CanaryGuard.Request request,String actionId) {
        var deployment=deployment(tx,intent.environment(),request.deploymentId());var active=deployment.active();
        Checks.require(active!=null && active.canary()!=null && request.revision()==active.revision() && intent.expectedRevision()==active.revision(),"canary-guard-context-mismatch");
        Checks.require(tx.now().plusSeconds(120).isBefore(request.expiresAt()) && !request.expiresAt().isAfter(tx.now().plusSeconds(3600)),"invalid-guard-expiry");
        boolean primary=false;
        for(var member:members(active)) {
            var plugin=tx.get("plugin",member.pluginId(),Plugin.class).orElseThrow();var group=tx.get("group",plugin.groupId(),Group.class).orElseThrow();
            if(group.id().equals(intent.groupId())){Checks.require(group.revision()==intent.groupRevision(),"group-revision-conflict");primary=true;}
            for(var operation:request.automaticRollback()?List.of(ActionIntent.Action.GUARD_CONFIGURE,ActionIntent.Action.ROLLBACK):List.of(ActionIntent.Action.GUARD_CONFIGURE))
                identities.requireScope(tx,actor,new ActionIntent(operation,intent.environment(),group.id(),group.revision(),plugin.id(),active.revision(),intent.policyRevision(),List.of(),intent.parametersSha256()));
        }
        Checks.require(primary,"release-primary-group-mismatch");
        tx.put("canary-guard",key(intent.environment(),request.deploymentId()),new CanaryGuard.Binding(actionId,intent.environment(),request,actor,intent.groupId(),intent.groupRevision(),intent.policyRevision(),Hashes.sha256(ProtocolJson.write(active)),tx.now(),"ARMED",null));
        tx.audit(actor.userId(),"canary-guard-arm",request.deploymentId(),request.automaticRollback()?"rollback-preauthorized":"observe-only",intent.parametersSha256());return request.deploymentId();
    }
    public void observe(String token,String environment,CanaryObservation observation) {
        database.transaction(tx -> {
            var node=machines.authenticate(tx,token,MachineService.Kind.NODE,environment);var deployment=deployment(tx,environment,observation.deploymentId());var active=deployment.active();
            Checks.require(active!=null && active.canary()!=null && active.targetNodes().contains(node.id()) && active.revision()==observation.revision() && active.canary().id().equals(observation.bundleId()),"observation-context-mismatch");
            long now=tx.now().getEpochSecond()/60;Checks.require(observation.minute()>=now-2 && observation.minute()<now,"observation-window-invalid");
            String id="sample-"+Hashes.sha256((environment+":"+node.id()+":"+observation.deploymentId()+":"+observation.revision()+":"+observation.minute()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var previous=tx.get("canary-sample",id,CanaryGuard.Sample.class);
            if(previous.isPresent())Checks.require(previous.get().machineRevision()==node.revision() && previous.get().observation().equals(observation),"observation-replay-conflict");
            else tx.put("canary-sample",id,new CanaryGuard.Sample(id,node.id(),environment,node.revision(),observation,tx.now()));
            evaluateGuards(tx);return null;
        });
    }
    public record GuardView(CanaryGuard.Request configuration,String state,String rollbackAction,CanaryGuard.Result observation) {}
    public GuardView guard(String session,String environment,String id) {return database.transaction(tx -> {
        var actor=identities.principal(tx,session);var deployment=deployment(tx,environment,id);Checks.require(readable(tx,actor,deployment),"forbidden");
        var guard=tx.get("canary-guard",key(environment,id),CanaryGuard.Binding.class);if(guard.isEmpty())return null;
        var value=guard.get();String state=value.state();
        if(value.rollbackAction()!=null){var action=tx.get("action",value.rollbackAction(),ActionReceipt.class);if(action.isPresent() && !action.get().state().equals("ACCEPTED"))state="ROLLBACK_"+action.get().state();}
        return new GuardView(value.request(),state,value.rollbackAction(),deployment.active()==null?null:assessGuard(tx,value,deployment.active()));
    });}
    private static CanaryGuard.Result assessGuard(ReleaseDatabase.Tx tx,CanaryGuard.Binding guard,Release active) {
        long minute=tx.now().getEpochSecond()/60-1;
        if(minute*60<guard.createdAt().getEpochSecond())return new CanaryGuard.Result("WARMING_UP",minute,0,0,0,List.of());
        var samples=tx.list("canary-sample",CanaryGuard.Sample.class).stream().filter(s -> s.environment().equals(guard.environment()) && s.observation().deploymentId().equals(active.deploymentId())
                && s.observation().revision()==active.revision() && active.canary()!=null && s.observation().bundleId().equals(active.canary().id()) && s.observation().minute()==minute)
                .filter(s -> tx.get("machine",s.nodeId(),MachineService.Machine.class).map(m -> m.enabled() && m.revision()==s.machineRevision() && m.environments().contains(guard.environment())).orElse(false)).toList();
        return CanaryGuard.assess(guard.request(),minute,active.targetNodes().stream().sorted().toList(),samples);
    }
    public void evaluateGuards(ReleaseDatabase.Tx tx) {
        for(var guard:tx.list("canary-guard",CanaryGuard.Binding.class).stream().filter(g -> g.state().equals("ARMED")).limit(100).toList()) {
            var deployment=deployment(tx,guard.environment(),guard.request().deploymentId());var active=deployment.active();String state=null,rollbackAction=null;
            if(!tx.now().isBefore(guard.request().expiresAt()))state="EXPIRED";
            else if(active==null || active.revision()!=guard.request().revision() || !guard.releaseHash().equals(Hashes.sha256(ProtocolJson.write(active))))state="SUPERSEDED";
            else if(assessGuard(tx,guard,active).state().equals("BREACHED") && guard.request().automaticRollback()) {
                if(deployment.pending()!=null && deployment.pending().state().equals("PREPARING"))continue;
                try {
                    var actor=guard.actor();var user=tx.get("user",actor.userId(),SecurityRecords.User.class).orElseThrow(() -> new ProtocolException("actor-unavailable"));
                    Checks.require(user.enabled() && user.enrolled() && user.credentialVersion()==actor.credentialVersion() && user.securityAdmin()==actor.securityAdmin(),"actor-authorization-revoked");
                    Checks.require(tx.list("session",SecurityRecords.Session.class).stream().anyMatch(s -> s.id().equals(actor.sessionId()) && s.userId().equals(actor.userId()) && s.credentialVersion()==actor.credentialVersion() && tx.now().isBefore(s.expiresAt()) && com.sma.release.auth.ConsoleIdentityService.originActive(tx,s)),"actor-session-revoked");
                    Checks.require(validation.policy(tx).revision()==guard.policyRevision(),"policy-revision-conflict");
                    var rollback=new Release(active.revision()+1,active.revision(),guard.policyRevision(),active.deploymentId(),active.stable(),null,0,active.bucketSalt(),Set.of(),active.targetNodes(),active.configurationSha256());
                    String digest=Hashes.sha256(ProtocolJson.write(rollback));var member=active.stable().members().getFirst();
                    var intent=new ActionIntent(ActionIntent.Action.ROLLBACK,guard.environment(),guard.groupId(),guard.groupRevision(),member.pluginId(),active.revision(),guard.policyRevision(),List.of(),digest);
                    rollbackAction="a-"+Hashes.sha256((guard.id()+":rollback").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    propose(tx,actor,intent,rollback,rollbackAction);tx.put("action",rollbackAction,new ActionReceipt(rollbackAction,actor.userId(),intent.digest(),"ACCEPTED",active.deploymentId(),tx.now()));
                    state="ROLLBACK_PREPARING";
                } catch(ProtocolException rejected){state="AUTHORIZATION_OR_TARGET_INVALID";rollbackAction=null;}
            }
            if(state!=null) {
                tx.put("canary-guard",key(guard.environment(),guard.request().deploymentId()),new CanaryGuard.Binding(guard.id(),guard.environment(),guard.request(),guard.actor(),guard.groupId(),guard.groupRevision(),guard.policyRevision(),guard.releaseHash(),guard.createdAt(),state,rollbackAction));
                tx.audit("security-service","canary-guard-transition",guard.request().deploymentId(),state,guard.releaseHash());
            }
        }
        tx.list("canary-sample",CanaryGuard.Sample.class).stream().filter(s -> s.observation().minute()<tx.now().getEpochSecond()/60-3).limit(500).forEach(s -> tx.delete("canary-sample",s.id()));
    }
    public record Cancel(String deploymentId,String preparationId,long pendingRevision,String reason) {
        public Cancel {Checks.id(deploymentId);Checks.id(preparationId);Checks.require(pendingRevision>0,"invalid-release-revision");Checks.text(reason,300);}
    }
    public String cancel(ReleaseDatabase.Tx tx,SecurityRecords.Principal actor,ActionIntent intent,Cancel request) {
        var deployment=deployment(tx,intent.environment(),request.deploymentId());var pending=deployment.pending();
        Checks.require(pending!=null && pending.state().equals("PREPARING") && pending.actionId().equals(request.preparationId())
                && pending.release().revision()==request.pendingRevision() && deployment.revision()==intent.expectedRevision(),"cancel-context-mismatch");
        boolean primary=false;
        for(var member:members(pending.release())) {
            var plugin=tx.get("plugin",member.pluginId(),Plugin.class).orElseThrow();var group=tx.get("group",plugin.groupId(),Group.class).orElseThrow();
            if(group.id().equals(intent.groupId())){Checks.require(group.revision()==intent.groupRevision(),"group-revision-conflict");primary=true;}
            identities.requireScope(tx,actor,new ActionIntent(ActionIntent.Action.CANCEL_RELEASE,intent.environment(),group.id(),group.revision(),plugin.id(),intent.expectedRevision(),intent.policyRevision(),List.of(),intent.parametersSha256()));
        }
        Checks.require(primary,"release-primary-group-mismatch");finish(tx,deployment,"CANCELLED","operator-cancelled",actor.userId());return deployment.id();
    }
    /** Scheduled and request-time reconciliation use the same transaction, including the action receipt. */
    public void reconcileAll(ReleaseDatabase.Tx tx) {
        tx.list("deployment",Deployment.class).stream().filter(d -> d.pending()!=null && d.pending().state().equals("PREPARING")).limit(200).forEach(d -> reconcile(tx,d));
    }
    private Deployment reconcile(ReleaseDatabase.Tx tx,Deployment deployment) {
        var pending=deployment.pending();if(pending==null || !pending.state().equals("PREPARING"))return deployment;
        if(!tx.now().isBefore(pending.expiresAt()))return finish(tx,deployment,"EXPIRED","preparation-expired","security-service");
        try {checkPending(tx,deployment);for(var member:members(pending.release()))requireValidated(tx,artifact(tx,member.artifactId()));}
        catch(ProtocolException rejected){return finish(tx,deployment,"FAILED",rejected.code(),"security-service");}
        return deployment;
    }
    private Deployment finish(ReleaseDatabase.Tx tx,Deployment deployment,String state,String reason,String actor) {
        var p=deployment.pending();var ended=new Deployment(deployment.id(),deployment.environment(),deployment.revision(),deployment.active(),
                new Pending(p.release(),p.actorId(),p.actorSessionId(),p.actorCredentialVersion(),p.action(),p.expiresAt(),p.readyNodes(),state,p.actionId()));
        tx.put("deployment",key(deployment.environment(),deployment.id()),ended);
        var receipt=tx.get("action",p.actionId(),ActionReceipt.class).orElseThrow();
        tx.put("action",receipt.id(),new ActionReceipt(receipt.id(),receipt.userId(),receipt.intentHash(),state,receipt.resourceId(),receipt.createdAt()));
        tx.audit(actor,"release-"+state.toLowerCase(java.util.Locale.ROOT),deployment.id(),reason,receipt.intentHash());return ended;
    }
    public InputStream download(String token,String environment,String deploymentId,String artifactId) throws IOException {
        database.transaction(tx -> {
            var node=machines.authenticate(tx,token,MachineService.Kind.NODE,environment); var deployment=deployment(tx,environment,deploymentId);
            var releases=new java.util.ArrayList<Release>(); if(deployment.active()!=null) releases.add(deployment.active());
            if(deployment.pending()!=null && deployment.pending().state().equals("PREPARING") && tx.now().isBefore(deployment.pending().expiresAt())) { checkPending(tx,deployment); releases.add(deployment.pending().release()); }
            boolean currentTarget=releases.stream().anyMatch(r -> r.targetNodes().contains(node.id()));
            if(currentTarget) releases.addAll(tx.list("history",History.class).stream().filter(h -> h.environment().equals(environment) && h.release().deploymentId().equals(deploymentId)).map(History::release).toList());
            Checks.require(currentTarget && releases.stream().flatMap(r -> members(r).stream()).anyMatch(m -> m.artifactId().equals(artifactId)),"artifact-not-authorized-for-node");
            requireValidated(tx,artifact(tx,artifactId)); return null;
        });
        return files.openArtifact(artifactId);
    }
    public String revoke(ReleaseDatabase.Tx tx,SecurityRecords.Principal p,ActionIntent intent,String artifactId) {
        var artifact=artifact(tx,artifactId); var plugin=tx.get("plugin",artifact.pluginId(),Plugin.class).orElseThrow();
        Checks.require(plugin.groupId().equals(intent.groupId()) && plugin.id().equals(intent.pluginId()) && artifact.environment().equals(intent.environment()),"revoke-context-mismatch");
        tx.put("revoked",artifact.sha256(),new Revoked(artifact.sha256(),tx.now(),p.userId(),"operator-revoked"));
        tx.audit(p.userId(),"artifact-revoke",artifact.id(),"revoked",artifact.sha256()); return artifactId;
    }
    private void checkPending(ReleaseDatabase.Tx tx,Deployment deployment) {
        var pending=deployment.pending(); Checks.require(pending!=null && pending.state().equals("PREPARING") && tx.now().isBefore(pending.expiresAt()),"preparation-expired");
        var user=tx.get("user",pending.actorId(),SecurityRecords.User.class).orElseThrow(() -> new ProtocolException("actor-unavailable"));
        Checks.require(user.enabled() && user.enrolled() && user.credentialVersion()==pending.actorCredentialVersion(),"actor-authorization-revoked");
        Checks.require(tx.list("session",SecurityRecords.Session.class).stream().anyMatch(s -> s.id().equals(pending.actorSessionId())
                && s.userId().equals(user.id()) && s.credentialVersion()==user.credentialVersion() && tx.now().isBefore(s.expiresAt()) && com.sma.release.auth.ConsoleIdentityService.originActive(tx,s)),"actor-session-revoked");
        var binding=tx.get("preparation",key(deployment.environment(),deployment.id()),PreparationBinding.class).orElseThrow();
        Checks.require(binding.revision()==pending.release().revision() && pending.release().policyRevision()==validation.policy(tx).revision(),"preparation-context-changed");
        binding.groupRevisions().forEach((id,revision) -> { var group=tx.get("group",id,Group.class).orElseThrow(); Checks.require(group.enabled() && group.revision()==revision,"preparation-context-changed"); });
        binding.pluginRevisions().forEach((id,revision) -> Checks.require(tx.get("plugin",id,Plugin.class).orElseThrow().revision()==revision,"preparation-context-changed"));
        binding.nodeRevisions().forEach((id,revision) -> {
            var node=tx.get("machine",id,MachineService.Machine.class).orElseThrow(() -> new ProtocolException("node-not-found"));
            Checks.require(node.enabled() && node.kind()==MachineService.Kind.NODE && node.revision()==revision && node.environments().contains(deployment.environment()),"node-authorization-changed");
        });
        var principal=new SecurityRecords.Principal(user.id(),user.username(),pending.actorSessionId(),user.credentialVersion(),user.securityAdmin());
        for(var member:members(pending.release())) {
            var plugin=tx.get("plugin",member.pluginId(),Plugin.class).orElseThrow();
            identities.requireScope(tx,principal,new ActionIntent(pending.action(),deployment.environment(),plugin.groupId(),binding.groupRevisions().get(plugin.groupId()),plugin.id(),pending.release().previousRevision(),pending.release().policyRevision(),List.of(),Hashes.sha256(ProtocolJson.write(pending.release()))));
        }
    }
    private ValidationResult requireValidated(ReleaseDatabase.Tx tx,Artifact artifact) {
        Checks.require(Set.of("VERIFIED","BLOCKED").contains(artifact.state()),"artifact-not-validated");
        Checks.require(tx.get("revoked",artifact.sha256(),Revoked.class).isEmpty(),"artifact-revoked");
        var result=tx.get("validation",artifact.id(),ValidationResult.class).orElseThrow(() -> new ProtocolException("artifact-not-validated"));
        var allowed=validation.dependencies(tx);
        result.manifest().dependencies().forEach(d -> Checks.require(d.sha256().equals(allowed.get(d.coordinate())),"dependency-not-allowed"));
        Checks.require(result.inventory().stream().allMatch(hash -> hash.equals(artifact.sha256()) || allowed.containsValue(hash)),"dependency-not-allowed");
        Checks.require(validation.gate(tx,artifact,result).allowed(),"artifact-scan-blocked");
        return result;
    }
    private ArtifactDelivery delivery(ReleaseDatabase.Tx tx,Artifact artifact,ValidationResult result,SignedEnvelope preparation) {
        var plugin=tx.get("plugin",artifact.pluginId(),Plugin.class).orElseThrow();var group=tx.get("group",plugin.groupId(),Group.class).orElseThrow();
        var proof=new ArtifactProof(artifact.id(),group.id(),group.revision(),artifact.pluginId(),result.manifest().version(),artifact.sha256(),artifact.bytes(),
                Hashes.sha256(ProtocolJson.write(result.manifest())),validation.policy(tx).revision(),Hashes.sha256(ProtocolJson.write(result.scan())),result.validationKind(),
                validation.dependencies(tx).entrySet().stream().filter(e -> result.inventory().contains(e.getValue())).collect(java.util.stream.Collectors.toUnmodifiableMap(java.util.Map.Entry::getKey,java.util.Map.Entry::getValue)),
                result.validationKind().equals("BUILD")?Hashes.sha256(ProtocolJson.write(tx.get("build-provenance",artifact.id(),BuildProvenance.class).orElseThrow())):null);
        return new ArtifactDelivery(artifact.id(),artifact.sha256(),artifact.bytes(),signing.sign(tx,SignedClaims.Purpose.ARTIFACT,artifact.environment(),proof,validation.gate(tx,artifact,result).validUntil()),preparation);
    }
    private Artifact artifact(ReleaseDatabase.Tx tx,String id) { return tx.get("artifact",id,Artifact.class).orElseThrow(() -> new ProtocolException("artifact-not-found")); }
    private Deployment deployment(ReleaseDatabase.Tx tx,String environment,String id) { return tx.get("deployment",key(environment,id),Deployment.class).orElseThrow(() -> new ProtocolException("deployment-not-found")); }
    private static List<Member> members(Release release) { return release.bundles().stream().flatMap(b -> b.members().stream()).distinct().toList(); }
    private static String key(String environment,String id) { Checks.id(environment); Checks.id(id); return environment+":"+id; }
}
