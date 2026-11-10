package com.sma.release.catalog;

import static com.sma.release.catalog.CatalogRecords.*;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.sma.extension.protocol.*;
import com.sma.release.auth.IdentityService;
import com.sma.release.auth.SecurityRecords;
import com.sma.release.store.ReleaseDatabase;

@Service
public class CatalogService {
    private final ReleaseDatabase database;
    private final IdentityService identities;
    private final ArtifactFiles files;
    public CatalogService(ReleaseDatabase database, IdentityService identities, ArtifactFiles files) {
        this.database = database; this.identities = identities; this.files = files;
    }
    public List<Group> groups(String session) {
        return database.transaction(tx -> { var p = identities.principal(tx, session); return tx.list("group", Group.class).stream().filter(g -> canRead(tx, p, g.id(), null)).toList(); });
    }
    public List<Plugin> plugins(String session, String groupId) {
        return database.transaction(tx -> { var p = identities.principal(tx, session); Checks.require(canRead(tx, p, groupId, null), "forbidden"); return tx.list("plugin", Plugin.class).stream().filter(v -> v.groupId().equals(groupId)).toList(); });
    }
    public List<Artifact> artifacts(String session, String pluginId, String environment) {
        return database.transaction(tx -> { var p = identities.principal(tx, session); var plugin = plugin(tx, pluginId);
            Checks.require(canRead(tx, p, plugin.groupId(), environment), "forbidden");
            return tx.list("artifact", Artifact.class).stream().filter(a -> a.pluginId().equals(pluginId) && a.environment().equals(environment)).toList(); });
    }
    public ResourcePolicy policy() { return database.transaction(this::policy); }
    public ActionReceipt action(String session, String actionId) {
        return database.transaction(tx -> { var p = identities.principal(tx, session);
            var receipt = tx.get("action", actionId, ActionReceipt.class).orElseThrow(() -> new ProtocolException("action-not-found"));
            Checks.require(receipt.userId().equals(p.userId()), "forbidden"); return receipt; });
    }
    public ActionReceipt execute(String session, Command command) {
        Checks.id(command.idempotencyKey());
        Checks.require(Hashes.sha256(command.parameters()).equals(command.intent().parametersSha256()), "action-parameters-mismatch");
        return database.transaction(tx -> {
            var p = identities.principal(tx, session); var intent = command.intent(); identities.requireScope(tx, p, intent);
            String actionId = "a-" + Hashes.sha256((p.userId() + ":" + command.idempotencyKey()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var existing = tx.get("action", actionId, ActionReceipt.class);
            if (existing.isPresent()) { Checks.require(existing.get().intentHash().equals(intent.digest()), "idempotency-conflict"); return existing.get(); }
            Checks.require(policy(tx).revision() == intent.policyRevision(), "policy-revision-conflict");
            // Failures roll back both grant consumption and the command. Failed proof attempts are committed separately.
            identities.consume(tx, session, command.grant(), intent, actionId);
            String resource = switch (intent.action()) {
                case GROUP_CREATE -> createGroup(tx, intent, command.parameters());
                case GROUP_UPDATE -> updateGroup(tx, intent, command.parameters());
                case PLUGIN_CREATE -> createPlugin(tx, intent, command.parameters());
                case GROUP_MOVE -> movePlugin(tx, p, intent, command.parameters());
                case GROUP_GRANT -> grant(tx, p, intent, command.parameters());
                case POLICY_UPDATE -> updatePolicy(tx, p, intent, command.parameters());
                case DEPENDENCY_REGISTER -> registerDependency(tx, p, intent, command.parameters());
                case EXCEPTION_CREATE -> registerException(tx, p, intent, command.parameters());
                case UPLOAD -> reserveUpload(tx, p, intent);
                default -> throw new ProtocolException("unsupported-command");
            };
            var receipt = new ActionReceipt(actionId, p.userId(), intent.digest(), "ACCEPTED", resource, tx.now());
            tx.put("action", actionId, receipt); return receipt;
        });
    }
    private String createGroup(ReleaseDatabase.Tx tx, ActionIntent intent, byte[] parameters) {
        Checks.require(intent.groupRevision() == 0 && tx.get("group", intent.groupId(), Group.class).isEmpty(), "group-already-exists");
        var input = ProtocolJson.read(parameters, GroupInput.class); Checks.text(input.name(), 100);
        tx.put("group", intent.groupId(), new Group(intent.groupId(), input.name(), 1, input.enabled())); return intent.groupId();
    }
    private String updateGroup(ReleaseDatabase.Tx tx, ActionIntent intent, byte[] parameters) {
        var group = group(tx, intent, false); var input = ProtocolJson.read(parameters, GroupInput.class); Checks.text(input.name(), 100);
        tx.put("group", group.id(), new Group(group.id(), input.name(), group.revision()+1, input.enabled())); return group.id();
    }
    private String createPlugin(ReleaseDatabase.Tx tx, ActionIntent intent, byte[] parameters) {
        group(tx, intent, true); Checks.require(tx.get("plugin", intent.pluginId(), Plugin.class).isEmpty(), "plugin-already-exists");
        var input = ProtocolJson.read(parameters, PluginInput.class); Checks.text(input.name(), 100);
        tx.put("plugin", intent.pluginId(), new Plugin(intent.pluginId(), intent.groupId(), 1, input.name())); return intent.pluginId();
    }
    private String movePlugin(ReleaseDatabase.Tx tx, SecurityRecords.Principal principal, ActionIntent intent, byte[] parameters) {
        group(tx, intent, true); var plugin = plugin(tx, intent.pluginId());
        Checks.require(plugin.groupId().equals(intent.groupId()) && plugin.revision() == intent.expectedRevision(), "plugin-revision-conflict");
        var input = ProtocolJson.read(parameters, MoveInput.class);
        var targetIntent = new ActionIntent(ActionIntent.Action.GROUP_MOVE, intent.environment(), input.targetGroupId(), input.targetGroupRevision(), intent.pluginId(), intent.expectedRevision(), intent.policyRevision(), intent.files(), intent.parametersSha256());
        identities.requireScope(tx, principal, targetIntent); group(tx, targetIntent, true);
        Checks.require(!input.targetGroupId().equals(plugin.groupId()), "same-group");
        Checks.require(pluginStorage(tx, plugin.id()) + groupStorage(tx, input.targetGroupId()) <= policy(tx).groupBytes(), "group-quota-exceeded");
        // Artifact group is a historical snapshot; quota and access use the current authoritative plugin ownership.
        tx.put("plugin", plugin.id(), new Plugin(plugin.id(), input.targetGroupId(), plugin.revision()+1, plugin.name())); return plugin.id();
    }
    private String grant(ReleaseDatabase.Tx tx, SecurityRecords.Principal principal, ActionIntent intent, byte[] parameters) {
        Checks.require(principal.securityAdmin(), "forbidden"); group(tx, intent, false);
        var input = ProtocolJson.read(parameters, ScopeInput.class);
        Checks.require(tx.get("user", input.userId(), SecurityRecords.User.class).isPresent(), "user-not-found");
        String id = "g-" + Hashes.sha256((input.userId()+":"+intent.groupId()+":"+intent.environment()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        tx.put("scope", id, new SecurityRecords.ScopeGrant(id, input.userId(), intent.groupId(), intent.environment(), input.actions(), input.read())); return id;
    }
    private String updatePolicy(ReleaseDatabase.Tx tx, SecurityRecords.Principal principal, ActionIntent intent, byte[] parameters) {
        Checks.require(principal.securityAdmin(), "forbidden");
        var updated = ProtocolJson.read(parameters, ResourcePolicy.class);
        Checks.require(updated.revision() == policy(tx).revision()+1, "policy-revision-conflict");
        tx.put("policy", "resources", updated); return "resources";
    }
    private String registerDependency(ReleaseDatabase.Tx tx, SecurityRecords.Principal principal, ActionIntent intent, byte[] parameters) {
        Checks.require(principal.securityAdmin(), "forbidden");
        var input = ProtocolJson.read(parameters, DependencyInput.class); Checks.hash(input.sha256());
        Checks.require(input.coordinate() != null && input.coordinate().matches("[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.+-]+")
                && !input.coordinate().contains("SNAPSHOT") && !input.coordinate().endsWith(":LATEST") && !input.coordinate().endsWith(":RELEASE"), "invalid-dependency");
        String id = Hashes.sha256(input.coordinate().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        tx.put("dependency", id, input); return id;
    }
    private String registerException(ReleaseDatabase.Tx tx, SecurityRecords.Principal principal, ActionIntent intent, byte[] parameters) {
        Checks.require(principal.securityAdmin(), "forbidden"); group(tx, intent, true);
        var permit = ProtocolJson.read(parameters, VulnerabilityGate.ExceptionPermit.class);
        Checks.require(permit.pluginId().equals(intent.pluginId()) && permit.environment().equals(intent.environment())
                && permit.policyRevision() == intent.policyRevision() && !permit.createdAt().isAfter(tx.now())
                && Duration.between(permit.createdAt(), tx.now()).compareTo(Duration.ofMinutes(2)) <= 0 && permit.expiresAt().isAfter(tx.now()), "exception-context-mismatch");
        Checks.require(tx.get("exception", permit.id(), VulnerabilityGate.ExceptionPermit.class).isEmpty(), "exception-id-used");
        tx.put("exception", permit.id(), permit); return permit.id();
    }
    private String reserveUpload(ReleaseDatabase.Tx tx, SecurityRecords.Principal principal, ActionIntent intent) {
        var group = group(tx, intent, true); var plugin = plugin(tx, intent.pluginId()); var policy = policy(tx);
        Checks.require(plugin.groupId().equals(group.id()) && plugin.revision() == intent.expectedRevision(), "plugin-revision-conflict");
        Checks.require(!intent.files().isEmpty() && intent.files().size() <= policy.batchFiles(), "invalid-upload-files");
        long total = 0, sources = 0;
        for (var file : intent.files()) {
            boolean source = file.name().endsWith(".java");
            Checks.require(source || file.name().endsWith(".jar"), "unsupported-upload-type");
            Checks.require(file.bytes() <= (source ? policy.sourceBytes() : policy.jarBytes()), "file-quota-exceeded");
            Checks.require(file.bytes() <= policy.batchBytes() - total, "batch-quota-exceeded"); total += file.bytes(); if (source) sources += file.bytes();
        }
        Checks.require(sources <= policy.sourceBatchBytes(), "source-quota-exceeded");
        Checks.require(total <= policy.pluginBytes() - pluginStorage(tx, plugin.id()), "plugin-quota-exceeded");
        Checks.require(total <= policy.groupBytes() - groupStorage(tx, group.id()), "group-quota-exceeded");
        Checks.require(total <= policy.repositoryBytes() - repositoryStorage(tx), "repository-quota-exceeded");
        try { var disk = java.nio.file.Files.getFileStore(files.root()); policy.checkSpace(disk.getTotalSpace(), disk.getUsableSpace(), activeReservations(tx), Math.multiplyExact(total, 2)); }
        catch (IOException failure) { throw new ProtocolException("storage-unavailable", failure); }
        String id = "u-" + UUID.randomUUID();
        tx.put("upload", id, new Upload(id, principal.userId(), principal.sessionId(), group.id(), group.revision(), plugin.id(), plugin.revision(), intent.environment(), policy.revision(), intent.files(), tx.now(), tx.now().plusSeconds(900), "RECEIVING")); return id;
    }
    public Chunk uploadChunk(String session, String uploadId, int fileIndex, int index, byte[] bytes) {
        Checks.require(bytes != null && bytes.length > 0 && bytes.length <= ArtifactFiles.CHUNK_BYTES, "invalid-chunk-size");
        return database.transaction(tx -> {
            var p = identities.principal(tx, session); var upload = activeUpload(tx, p, uploadId);
            Checks.require(fileIndex >= 0 && fileIndex < upload.files().size() && index >= 0, "invalid-chunk-index");
            var expected = upload.files().get(fileIndex); long offset = (long)index * ArtifactFiles.CHUNK_BYTES;
            Checks.require(offset < expected.bytes() && bytes.length == Math.min(ArtifactFiles.CHUNK_BYTES, expected.bytes()-offset), "invalid-chunk-size");
            String id = uploadId + ":" + fileIndex + ":" + index, hash = Hashes.sha256(bytes);
            var existing = tx.get("chunk", id, Chunk.class);
            if (existing.isPresent()) { Checks.require(existing.get().sha256().equals(hash), "chunk-content-conflict"); return existing.get(); }
            try { files.writeChunk(uploadId, fileIndex, index, bytes); }
            catch (IOException failure) { throw new ProtocolException("chunk-storage-failed", failure); }
            var chunk = new Chunk(id, uploadId, fileIndex, index, hash, bytes.length); tx.put("chunk", id, chunk); return chunk;
        });
    }
    public List<Artifact> completeUpload(String session, String uploadId) {
        return database.transaction(tx -> {
            var p = identities.principal(tx, session); var stored = tx.get("upload", uploadId, Upload.class).orElseThrow(() -> new ProtocolException("upload-not-found"));
            Checks.require(stored.actor().equals(p.userId()) && stored.sessionId().equals(p.sessionId()), "forbidden");
            if (stored.state().equals("COMPLETE")) return tx.list("artifact", Artifact.class).stream().filter(a -> a.uploadId().equals(uploadId)).toList();
            var upload = activeUpload(tx, p, uploadId); var result = new java.util.ArrayList<Artifact>();
            for (int fileIndex=0; fileIndex<upload.files().size(); fileIndex++) {
                final int index = fileIndex; var expected = upload.files().get(fileIndex);
                var chunks = tx.list("chunk", Chunk.class).stream().filter(c -> c.uploadId().equals(uploadId) && c.fileIndex() == index).sorted(java.util.Comparator.comparingInt(Chunk::index)).toList();
                Checks.require(chunks.size() == (expected.bytes()+ArtifactFiles.CHUNK_BYTES-1)/ArtifactFiles.CHUNK_BYTES, "missing-upload-chunk");
                String artifactId = uploadId + "-" + fileIndex;
                try { files.assemble(artifactId, uploadId, fileIndex, chunks, expected.bytes(), expected.sha256()); }
                catch (IOException failure) { throw new ProtocolException("artifact-storage-failed", failure); }
                var artifact = new Artifact(artifactId, uploadId, fileIndex, upload.groupId(), upload.pluginId(), upload.environment(), expected.sha256(), expected.bytes(), expected.name(), "RECEIVED", null, null, tx.now());
                tx.put("artifact", artifactId, artifact); result.add(artifact);
            }
            tx.put("upload", uploadId, upload.state("COMPLETE"));
            tx.audit(p.userId(), "upload-complete", uploadId, "hash-verified", Hashes.sha256(ProtocolJson.write(upload.files())));
            return List.copyOf(result);
        });
    }
    private Upload activeUpload(ReleaseDatabase.Tx tx, SecurityRecords.Principal p, String id) {
        var upload = tx.get("upload", id, Upload.class).orElseThrow(() -> new ProtocolException("upload-not-found"));
        Checks.require(upload.actor().equals(p.userId()) && upload.sessionId().equals(p.sessionId()), "forbidden");
        Checks.require(upload.state().equals("RECEIVING") && tx.now().isBefore(upload.expiresAt()), "upload-unavailable");
        var intent = new ActionIntent(ActionIntent.Action.UPLOAD, upload.environment(), upload.groupId(), upload.groupRevision(), upload.pluginId(), upload.pluginRevision(), upload.policyRevision(), upload.files(), Hashes.sha256(new byte[0]));
        identities.requireScope(tx, p, intent); group(tx, intent, true);
        var plugin = plugin(tx, upload.pluginId());
        Checks.require(plugin.groupId().equals(upload.groupId()) && plugin.revision() == upload.pluginRevision() && policy(tx).revision() == upload.policyRevision(), "upload-context-changed");
        return upload;
    }
    public ResourcePolicy policy(ReleaseDatabase.Tx tx) { return tx.get("policy", "resources", ResourcePolicy.class).orElse(ResourcePolicy.defaults()); }
    private Group group(ReleaseDatabase.Tx tx, ActionIntent intent, boolean requireEnabled) {
        var group = tx.get("group", intent.groupId(), Group.class).orElseThrow(() -> new ProtocolException("group-not-found"));
        Checks.require(group.revision() == intent.groupRevision(), "group-revision-conflict");
        Checks.require(!requireEnabled || group.enabled(), "group-disabled"); return group;
    }
    private Plugin plugin(ReleaseDatabase.Tx tx, String id) { return tx.get("plugin", id, Plugin.class).orElseThrow(() -> new ProtocolException("plugin-not-found")); }
    private boolean canRead(ReleaseDatabase.Tx tx, SecurityRecords.Principal p, String group, String environment) {
        return p.securityAdmin() || tx.list("scope", SecurityRecords.ScopeGrant.class).stream().anyMatch(g -> g.read() && g.userId().equals(p.userId()) && g.groupId().equals(group) && (environment == null || g.environment().equals(environment)));
    }
    private long activeReservations(ReleaseDatabase.Tx tx) { return tx.list("upload", Upload.class).stream().filter(u -> u.state().equals("RECEIVING")).mapToLong(Upload::reservedBytes).sum(); }
    private long pluginStorage(ReleaseDatabase.Tx tx, String id) {
        return tx.list("artifact", Artifact.class).stream().filter(a -> a.pluginId().equals(id)).mapToLong(Artifact::bytes).sum()
                + tx.list("upload", Upload.class).stream().filter(u -> u.pluginId().equals(id) && u.state().equals("RECEIVING")).mapToLong(Upload::reservedBytes).sum();
    }
    private long groupStorage(ReleaseDatabase.Tx tx, String id) { return tx.list("plugin", Plugin.class).stream().filter(p -> p.groupId().equals(id)).mapToLong(p -> pluginStorage(tx, p.id())).sum(); }
    private long repositoryStorage(ReleaseDatabase.Tx tx) { return tx.list("artifact", Artifact.class).stream().mapToLong(Artifact::bytes).sum() + activeReservations(tx); }
}
