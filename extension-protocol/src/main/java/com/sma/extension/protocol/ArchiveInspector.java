package com.sma.extension.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.zip.CRC32;
import java.util.zip.ZipFile;

/** Bounded data-only inspection. Never creates a class loader or runs an uploaded initializer. */
public final class ArchiveInspector {
    private static final List<String> RESERVED = List.of("java/", "javax/", "jdk/", "sun/", "com/sun/", "jakarta/",
            "org/springframework/", "tools/jackson/", "com/fasterxml/", "org/slf4j/", "ch/qos/logback/", "com/sma/");
    private ArchiveInspector() {}
    public record EntryDigest(String path, String sha256, long bytes) {}
    public record Inspection(String sha256, long bytes, ArtifactManifest manifest, List<EntryDigest> files, long expandedBytes) {
        public Inspection { files = List.copyOf(files); }
    }
    public static Inspection inspect(Path jar, ResourcePolicy policy, Map<String, String> allowedDependencies) throws IOException {
        Checks.require(Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(jar), "invalid-artifact-file");
        Hashes.DigestResult digest;
        try (var stream = Files.newInputStream(jar)) { digest = Hashes.sha256(stream, policy.jarBytes()); }
        long started = System.nanoTime(), total = 0, compressedTotal = 0;
        List<EntryDigest> files = new ArrayList<>(); Set<String> names = new HashSet<>(); Set<String> classes = new HashSet<>();
        byte[] pluginJson = null; int count = 0;
        try (var zip = new ZipFile(jar.toFile())) {
            var items = zip.entries();
            while (items.hasMoreElements()) {
                checkTime(started, policy);
                var item = items.nextElement(); String name = item.getName();
                Checks.require(++count <= policy.entries(), "too-many-archive-entries");
                validatePath(name);
                Checks.require(names.add(name.toLowerCase(Locale.ROOT)), "duplicate-archive-entry");
                Checks.require(item.getSize() >= 0 && item.getCompressedSize() >= 0, "unknown-archive-size");
                if (item.isDirectory()) { Checks.require(item.getSize() == 0, "invalid-directory-entry"); continue; }
                Checks.require(item.getSize() <= policy.expandedBytes() - total, "expanded-size-exceeded");
                Checks.require(item.getSize() <= Math.max(1L, item.getCompressedSize()) * policy.compressionRatio(), "compression-ratio-exceeded");
                boolean metadata = name.equals(ArtifactManifest.PATH) || name.equalsIgnoreCase("META-INF/MANIFEST.MF");
                if (metadata) Checks.require(item.getSize() <= 65536, "metadata-too-large");
                Checks.require(!name.startsWith("META-INF/versions/") && !name.startsWith("META-INF/services/")
                        && !name.startsWith("BOOT-INF/") && !name.equals("module-info.class"), "unsupported-archive-layout");
                String lower = name.toLowerCase(Locale.ROOT);
                Checks.require(!lower.matches(".*\\.(dll|so|dylib|exe|bat|cmd|ps1|sh)$"), "native-or-executable-entry");
                if (name.endsWith(".class")) {
                    Checks.require(RESERVED.stream().noneMatch(name::startsWith), "reserved-package");
                    classes.add(name.substring(0, name.length()-6).replace('/', '.'));
                }
                var crc = new CRC32(); var capture = metadata ? new ByteArrayOutputStream() : null;
                var sha = java.security.MessageDigest.getInstance("SHA-256"); long size = 0;
                try (var input = zip.getInputStream(item)) {
                    byte[] buffer = new byte[65536];
                    for (int n; (n = input.read(buffer)) != -1;) {
                        checkTime(started, policy);
                        Checks.require(n <= policy.expandedBytes() - total, "expanded-size-exceeded");
                        total += n; size += n;
                        Checks.require(size <= item.getSize(), "archive-size-mismatch");
                        crc.update(buffer, 0, n); sha.update(buffer, 0, n); if (capture != null) capture.write(buffer, 0, n);
                    }
                }
                Checks.require(size == item.getSize() && crc.getValue() == item.getCrc(), "corrupt-archive-entry");
                compressedTotal += item.getCompressedSize();
                files.add(new EntryDigest(name, java.util.HexFormat.of().formatHex(sha.digest()), size));
                if (name.equals(ArtifactManifest.PATH)) pluginJson = capture.toByteArray();
                else if (name.equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                    var attributes = new Manifest(new java.io.ByteArrayInputStream(capture.toByteArray())).getMainAttributes();
                    for (String forbidden : List.of("Class-Path", "Premain-Class", "Agent-Class", "Launcher-Agent-Class", "Main-Class"))
                        Checks.require(attributes.getValue(forbidden) == null, "agent-or-external-classpath");
                }
            }
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        Checks.require(total <= Math.max(1L, compressedTotal) * policy.compressionRatio(), "compression-ratio-exceeded");
        Checks.require(pluginJson != null, "plugin-manifest-required");
        var manifest = ProtocolJson.read(pluginJson, ArtifactManifest.class);
        Checks.require(classes.contains(manifest.implementationClass()), "implementation-class-missing");
        for (var dependency : manifest.dependencies()) {
            Checks.require(dependency.sha256().equals(allowedDependencies.get(dependency.coordinate())), "dependency-not-allowed");
            Checks.require(files.stream().anyMatch(f -> f.path().equals(dependency.path()) && f.sha256().equals(dependency.sha256())), "dependency-content-mismatch");
        }
        for (var entry : files) {
            if (entry.path().toLowerCase(Locale.ROOT).endsWith(".jar"))
                Checks.require(manifest.dependencies().stream().anyMatch(d -> d.path().equals(entry.path())), "undeclared-nested-dependency");
        }
        return new Inspection(digest.sha256(), digest.bytes(), manifest, files, total);
    }
    public static void validatePath(String name) {
        Checks.require(name != null && !name.isEmpty() && name.length() <= 240 && !name.startsWith("/") && !name.contains("\\")
                && !name.contains(":") && !name.contains("\u0000"), "unsafe-archive-path");
        for (String part : name.split("/", -1)) {
            Checks.require(!part.equals(".") && !part.equals("..") && !part.endsWith(".") && !part.endsWith(" ")
                    && part.codePoints().noneMatch(Character::isISOControl), "unsafe-archive-path");
            String base = part.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
            Checks.require(!base.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"), "unsafe-archive-path");
        }
        Checks.require(!name.contains("//"), "unsafe-archive-path");
    }
    private static void checkTime(long started, ResourcePolicy policy) {
        Checks.require(System.nanoTime() - started <= Duration.ofSeconds(policy.parserSeconds()).toNanos(), "archive-parse-timeout");
    }
}
