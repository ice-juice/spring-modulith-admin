package com.sma.extension.protocol;

import java.util.List;

/** Exact immutable object authenticated by the human. Lists are sorted before the challenge is created. */
public record ActionIntent(Action action, String environment, String groupId, long groupRevision, String pluginId,
        long expectedRevision, long policyRevision, List<FileIntent> files, String parametersSha256) {
    public enum Action {
        GROUP_CREATE, GROUP_UPDATE, GROUP_MOVE, GROUP_GRANT, PLUGIN_CREATE, UPLOAD, VALIDATE,
        PUBLISH, CANARY, PROMOTE, ROLLBACK, QUARANTINE, REVOKE, DELETE, POLICY_UPDATE,
        DEPENDENCY_REGISTER, EXCEPTION_CREATE, TRUST_UPDATE, USER_CREATE, USER_UPDATE, FACTOR_RESET
    }
    public ActionIntent {
        Checks.require(action != null && groupRevision >= 0 && expectedRevision >= 0 && policyRevision > 0, "invalid-action");
        Checks.id(environment); Checks.id(groupId); Checks.id(pluginId); Checks.hash(parametersSha256);
        files = List.copyOf(files); Checks.unique(files.stream().map(FileIntent::name).toList(), 20);
        Checks.require(files.equals(files.stream().sorted(java.util.Comparator.comparing(FileIntent::name)).toList()), "unsorted-file-intent");
    }
    public String digest() { return Hashes.sha256(ProtocolJson.write(this)); }
    public record FileIntent(String name, String sha256, long bytes) {
        public FileIntent {
            Checks.require(name != null && name.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,159}") && bytes > 0, "invalid-file-intent");
            Checks.hash(sha256);
        }
    }
}
