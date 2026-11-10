package com.sma.extension.protocol;

import java.util.List;
import java.util.Set;

/** META-INF/sma-plugin.json, inspected as data before a plugin class is defined. */
public record ArtifactManifest(int format, String pluginId, String version, int sdkMajor, String implementationClass,
        List<Entry> entries, List<Dependency> dependencies, Set<String> capabilities, Set<String> metrics) {
    public static final String PATH = "META-INF/sma-plugin.json";
    public ArtifactManifest {
        Checks.require(format == 1 && sdkMajor == 1, "unsupported-plugin-format");
        Checks.id(pluginId);
        Checks.require(version != null && version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[a-z0-9.-]+)?") && version.length() <= 80, "invalid-version");
        Checks.require(implementationClass != null && implementationClass.matches("[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)+")
                && implementationClass.length() <= 240, "invalid-entry-class");
        entries = List.copyOf(entries); dependencies = List.copyOf(dependencies);
        capabilities = Set.copyOf(capabilities); metrics = Set.copyOf(metrics);
        Checks.require(!entries.isEmpty(), "entries-required"); Checks.unique(entries.stream().map(Entry::id).toList(), 100);
        Checks.unique(dependencies.stream().map(Dependency::coordinate).toList(), 100);
        Checks.unique(dependencies.stream().map(Dependency::path).toList(), 100);
        Checks.unique(capabilities, 100); capabilities.forEach(Checks::id);
        Checks.unique(metrics, 100); metrics.forEach(Checks::id);
    }
    public enum EntryKind { HTTP, SERVICE, EVENT, TASK }
    public record Entry(String id, EntryKind kind, String contract, String permission, Set<String> identities, boolean stateful) {
        public Entry {
            Checks.id(id); Checks.require(kind != null, "entry-kind-required"); Checks.text(contract, 100);
            Checks.require(permission != null && permission.matches("extension:invoke:[a-z][a-z0-9-]{0,79}"), "invalid-entry-permission");
            identities = Set.copyOf(identities);
            Checks.require(!identities.isEmpty() && Set.of("USER", "APPLICATION", "JOB").containsAll(identities), "invalid-entry-identities");
        }
    }
    public record Dependency(String groupId, String artifactId, String version, String sha256, String path) {
        public Dependency {
            Checks.text(groupId, 120); Checks.text(artifactId, 120); Checks.text(version, 80); Checks.hash(sha256);
            Checks.require(!version.contains("SNAPSHOT") && !version.equalsIgnoreCase("LATEST") && !version.equalsIgnoreCase("RELEASE")
                    && version.matches("[a-zA-Z0-9_.+-]+"), "unlocked-dependency");
            Checks.require(path != null && path.matches("lib/[a-zA-Z0-9_.-]+\\.jar"), "invalid-dependency-path");
        }
        public String coordinate() { return groupId + ":" + artifactId + ":" + version; }
    }
}
