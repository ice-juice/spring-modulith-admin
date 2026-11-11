package com.sma.extension.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.zip.ZipInputStream;

/** Fully inspected in-memory bytes. The loader never reopens a mutable path after verifying its hash. */
public final class ArchiveSnapshot {
    private final String sha256;
    private final ArtifactManifest manifest;
    private final Map<String, byte[]> resources;
    private final Set<String> inventory;
    private final Map<String,String> dependencyCoordinates;
    private final long expandedBytes;
    private ArchiveSnapshot(String sha256, ArtifactManifest manifest, Map<String, byte[]> resources, Set<String> inventory, Map<String,String> dependencyCoordinates, long expandedBytes) {
        this.sha256 = sha256; this.manifest = manifest; this.resources = Map.copyOf(resources);
        this.inventory = Set.copyOf(inventory); this.dependencyCoordinates=Map.copyOf(dependencyCoordinates);this.expandedBytes = expandedBytes;
    }
    public String sha256() { return sha256; }
    public ArtifactManifest manifest() { return manifest; }
    public long expandedBytes() { return expandedBytes; }
    public Set<String> inventory() { return inventory; }
    public Map<String,String> dependencyCoordinates() { return dependencyCoordinates; }
    public Set<String> names() { return resources.keySet(); }
    public byte[] resource(String name) { byte[] value = resources.get(name); return value == null ? null : value.clone(); }
    public static ArchiveSnapshot inspect(byte[] jar, ResourcePolicy policy, Map<String, String> allowedDependencies) throws IOException {
        Checks.require(jar.length > 0 && jar.length <= policy.jarBytes(), "file-quota-exceeded");
        var reader = new Reader(policy, allowedDependencies); byte[] immutable = jar.clone();
        String hash = Hashes.sha256(immutable); reader.inventory.add(hash);
        var root = reader.read(immutable, 0);
        Checks.require(root.containsKey(ArtifactManifest.PATH), "plugin-manifest-required");
        var manifest = ProtocolJson.read(root.get(ArtifactManifest.PATH), ArtifactManifest.class);
        Checks.require(root.containsKey(manifest.implementationClass().replace('.', '/') + ".class"), "implementation-class-missing");
        for (var dependency : manifest.dependencies()) {
            Checks.require(dependency.sha256().equals(allowedDependencies.get(dependency.coordinate())), "dependency-not-allowed");
            byte[] bytes = root.get(dependency.path());
            Checks.require(bytes != null && Hashes.sha256(bytes).equals(dependency.sha256()), "dependency-content-mismatch");
        }
        for (var entry : root.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                Checks.require(manifest.dependencies().stream().anyMatch(d -> d.path().equals(entry.getKey())), "undeclared-nested-dependency");
                reader.addDependency(entry.getValue(), 1);
            } else reader.addResource(entry.getKey(), entry.getValue());
        }
        return new ArchiveSnapshot(hash, manifest, reader.resources, reader.inventory, reader.dependencyCoordinates, reader.total);
    }
    private static final class Reader {
        private final ResourcePolicy policy;
        private final Map<String, String> allowed;
        private final Map<String, byte[]> resources = new HashMap<>();
        private final Map<String,String> dependencyCoordinates = new HashMap<>();
        private final Set<String> resourceNames = new HashSet<>(), inventory = new HashSet<>();
        private final long started = System.nanoTime();
        private long total; private int entries;
        Reader(ResourcePolicy policy, Map<String, String> allowed) { this.policy = policy; this.allowed = Map.copyOf(allowed); }
        void checkBudget() {
            Checks.require(System.nanoTime() - started <= policy.parserSeconds() * 1_000_000_000L, "archive-parse-timeout");
        }
        Map<String, byte[]> read(byte[] archive, int depth) throws IOException {
            Checks.require(depth <= policy.archiveDepth(), "archive-depth-exceeded");
            Map<String, byte[]> result = new HashMap<>(); Set<String> names = new HashSet<>();
            long localExpanded = 0;
            try (var input = new ZipInputStream(new ByteArrayInputStream(archive))) {
                for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                    checkBudget(); Checks.require(++entries <= policy.entries(), "too-many-archive-entries");
                    String name = entry.getName(); ArchiveInspector.validatePath(name);
                    Checks.require(names.add(name.toLowerCase(Locale.ROOT)), "duplicate-archive-entry");
                    long size = 0; var output = new ByteArrayOutputStream(); byte[] buffer = new byte[65536];
                    for (int n; (n = input.read(buffer)) != -1;) {
                        checkBudget(); Checks.require(n <= policy.expandedBytes() - total, "expanded-size-exceeded");
                        total += n; size += n; localExpanded += n;
                        if (name.equals(ArtifactManifest.PATH) || name.equalsIgnoreCase("META-INF/MANIFEST.MF"))
                            Checks.require(size <= 65536, "metadata-too-large");
                        output.write(buffer, 0, n);
                    }
                    // ZipInputStream validates CRC/size before returning end-of-entry, including data descriptors.
                    input.closeEntry();
                    Checks.require(size == entry.getSize() && entry.getCompressedSize() >= 0, "corrupt-archive-entry");
                    Checks.require(size <= Math.max(1L, entry.getCompressedSize()) * policy.compressionRatio(), "compression-ratio-exceeded");
                    if (entry.isDirectory()) { Checks.require(size == 0, "invalid-directory-entry"); continue; }
                    validateEntry(name, output.toByteArray(),depth==0); result.put(name, output.toByteArray());
                }
            }
            Checks.require(!result.isEmpty() && localExpanded <= Math.max(1L, archive.length) * policy.compressionRatio(), "invalid-or-compressed-archive");
            return result;
        }
        void addDependency(byte[] archive, int depth) throws IOException {
            String hash = Hashes.sha256(archive);
            Checks.require(allowed.containsValue(hash), "dependency-not-allowed");
            var coordinates=allowed.entrySet().stream().filter(e -> e.getValue().equals(hash)).map(Map.Entry::getKey).toList();
            Checks.require(coordinates.size()==1 && coordinates.getFirst().split(":",-1).length==3,"ambiguous-dependency-coordinate");
            dependencyCoordinates.put(coordinates.getFirst(),hash);
            Checks.require(inventory.add(hash), "duplicate-dependency-content");
            var content = read(archive, depth);
            for(var entry:content.entrySet()) if(entry.getKey().startsWith("META-INF/maven/") && entry.getKey().endsWith("/pom.properties")) {
                var properties=new java.util.Properties();properties.load(new ByteArrayInputStream(entry.getValue()));
                String coordinate=properties.getProperty("groupId")+":"+properties.getProperty("artifactId")+":"+properties.getProperty("version");
                Checks.require(coordinate.equals(coordinates.getFirst()),"undeclared-shaded-dependency");
            }
            Checks.require(!content.containsKey(ArtifactManifest.PATH), "nested-plugin-not-allowed");
            for (var item : content.entrySet()) {
                if (item.getKey().toLowerCase(Locale.ROOT).endsWith(".jar")) addDependency(item.getValue(), depth+1);
                else if (!item.getKey().equalsIgnoreCase("META-INF/MANIFEST.MF")) addResource(item.getKey(), item.getValue());
            }
        }
        void addResource(String name, byte[] bytes) {
            // Duplicate resources/classes are ambiguous; do not silently choose an order-dependent winner.
            Checks.require(resourceNames.add(name.toLowerCase(Locale.ROOT)), "conflicting-plugin-resource");
            resources.put(name, bytes);
        }
        void validateEntry(String name, byte[] bytes,boolean pluginOwned) throws IOException {
            String lower = name.toLowerCase(Locale.ROOT);
            Checks.require(!lower.startsWith("meta-inf/versions/") && !lower.startsWith("meta-inf/services/")
                    && !lower.startsWith("boot-inf/") && !lower.equals("module-info.class"), "unsupported-archive-layout");
            Checks.require(!lower.matches(".*\\.(dll|so|dylib|exe|bat|cmd|ps1|sh)$"), "native-or-executable-entry");
            if (name.endsWith(".class")) {
                Checks.require(List.of("java/", "javax/", "jdk/", "sun/", "com/sun/", "jakarta/", "org/springframework/",
                        "tools/jackson/", "com/fasterxml/", "org/slf4j/", "ch/qos/logback/", "com/sma/").stream().noneMatch(name::startsWith), "reserved-package");
                Checks.require(bytes.length >= 8 && bytes[0] == (byte)0xca && bytes[1] == (byte)0xfe && bytes[2] == (byte)0xba && bytes[3] == (byte)0xbe
                        && ((bytes[6]&255)*256 + (bytes[7]&255)) <= 65, "invalid-class-format");
                BytecodePolicy.inspect(bytes,name.substring(0,name.length()-6),pluginOwned);
            }
            if (name.equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                var attrs = new Manifest(new ByteArrayInputStream(bytes)).getMainAttributes();
                for (String key : List.of("Class-Path", "Premain-Class", "Agent-Class", "Launcher-Agent-Class", "Main-Class"))
                    Checks.require(attrs.getValue(key) == null, "agent-or-external-classpath");
            }
        }
    }
}
