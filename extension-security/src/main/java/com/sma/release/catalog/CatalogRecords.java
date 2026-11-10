package com.sma.release.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import com.sma.extension.protocol.ActionIntent;
import com.sma.extension.protocol.ArtifactManifest;
import com.sma.extension.protocol.ScanReport;

public final class CatalogRecords {
    private CatalogRecords() {}
    public record Group(String id, String name, long revision, boolean enabled) {}
    public record Plugin(String id, String groupId, long revision, String name) {}
    public record Upload(String id, String actor, String sessionId, String groupId, long groupRevision, String pluginId,
            long pluginRevision, String environment, long policyRevision, List<ActionIntent.FileIntent> files,
            Instant createdAt, Instant expiresAt, String state) {
        public Upload { files = List.copyOf(files); }
        public long reservedBytes() { return files.stream().mapToLong(ActionIntent.FileIntent::bytes).sum(); }
        public Upload state(String value) { return new Upload(id, actor, sessionId, groupId, groupRevision, pluginId, pluginRevision, environment, policyRevision, files, createdAt, expiresAt, value); }
    }
    public record Chunk(String id, String uploadId, int fileIndex, int index, String sha256, int bytes) {}
    public record Artifact(String id, String uploadId, int fileIndex, String groupId, String pluginId, String environment,
            String sha256, long bytes, String name, String state, ArtifactManifest manifest, ScanReport report, Instant createdAt) {}
    public record ActionReceipt(String id, String userId, String intentHash, String state, String resourceId, Instant createdAt) {}
    public record Command(String idempotencyKey, String grant, ActionIntent intent, byte[] parameters) {
        public Command { parameters = parameters.clone(); }
        @Override public byte[] parameters() { return parameters.clone(); }
    }
    public record GroupInput(String name, boolean enabled) {}
    public record PluginInput(String name) {}
    public record MoveInput(String targetGroupId, long targetGroupRevision) {}
    public record ScopeInput(String userId, Set<ActionIntent.Action> actions, boolean read) { public ScopeInput { actions = Set.copyOf(actions); } }
    public record DependencyInput(String coordinate, String sha256) {}
}
