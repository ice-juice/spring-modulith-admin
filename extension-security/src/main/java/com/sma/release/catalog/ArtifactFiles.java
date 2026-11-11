package com.sma.release.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.springframework.stereotype.Component;
import com.sma.extension.protocol.Checks;
import com.sma.extension.protocol.Hashes;
import com.sma.extension.protocol.ProtocolException;
import com.sma.release.ReleaseProperties;

/** Paths are generated exclusively from service-owned identifiers. No uploaded filename is used as a filesystem path. */
@Component
public class ArtifactFiles {
    public static final int CHUNK_BYTES = 1024 * 1024;
    private final Path root;
    public ArtifactFiles(ReleaseProperties properties) throws IOException {
        root = properties.directory().toAbsolutePath().normalize();
        Files.createDirectories(root); requireSafe(root);
        for (String child : List.of("chunks", "artifacts", "work")) { Files.createDirectories(root.resolve(child)); requireSafe(root.resolve(child)); }
    }
    public Path root() { return root; }
    public void writeChunk(String uploadId, int fileIndex, int index, byte[] bytes) throws IOException {
        Checks.require(bytes.length > 0 && bytes.length <= CHUNK_BYTES, "invalid-chunk-size");
        Path file = chunkPath(uploadId, fileIndex, index);
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            requireSafe(file);
            try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                Checks.require(Hashes.sha256(input, CHUNK_BYTES).sha256().equals(Hashes.sha256(bytes)), "chunk-content-conflict");
            }
            return;
        }
        try (var channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            var data = java.nio.ByteBuffer.wrap(bytes); while (data.hasRemaining()) channel.write(data); channel.force(true);
        }
    }
    public Path assemble(String artifactId, String uploadId, int fileIndex, List<CatalogRecords.Chunk> chunks,
            long expectedBytes, String expectedHash) throws IOException {
        Path target = artifactPath(artifactId); requireSafe(target.getParent());
        // A crash after fsync but before DB commit may leave the complete file; verify it rather than append or overwrite.
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            requireSafe(target);
            try (var input = Files.newInputStream(target, LinkOption.NOFOLLOW_LINKS)) {
                var actual = Hashes.sha256(input, expectedBytes);
                Checks.require(actual.bytes() == expectedBytes && actual.sha256().equals(expectedHash), "artifact-content-conflict");
            }
            return target;
        }
        Path temporary = Files.createTempFile(root.resolve("work"), "assemble-", ".partial");
        try {
            try (var output = FileChannel.open(temporary, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                for (int index=0; index<chunks.size(); index++) {
                    var chunk = chunks.get(index);
                    Checks.require(chunk.index() == index && chunk.fileIndex() == fileIndex && chunk.uploadId().equals(uploadId), "missing-upload-chunk");
                    Path source = chunkPath(uploadId, fileIndex, index); requireSafe(source);
                    byte[] bytes;
                    try (var input = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(CHUNK_BYTES+1); }
                    Checks.require(bytes.length == chunk.bytes() && Hashes.sha256(bytes).equals(chunk.sha256()), "chunk-content-mismatch");
                    var buffer = java.nio.ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) output.write(buffer);
                }
                output.force(true);
            }
            try (var input = Files.newInputStream(temporary, LinkOption.NOFOLLOW_LINKS)) {
                var actual = Hashes.sha256(input, expectedBytes);
                Checks.require(actual.bytes() == expectedBytes && Hashes.equalHex(actual.sha256(), expectedHash), "upload-hash-mismatch");
            }
            // Same volume, no REPLACE_EXISTING. Data is never discovered by scanning this directory.
            Files.move(temporary, target);
            return target;
        } finally { Files.deleteIfExists(temporary); }
    }
    public InputStream openArtifact(String artifactId) throws IOException {
        Path file = artifactPath(artifactId); requireSafe(file); return Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS);
    }
    public void deleteChunk(String uploadId,int fileIndex,int index) throws IOException {
        Path file=chunkPath(uploadId,fileIndex,index); requireSafe(file.getParent());
        Checks.require(!Files.isSymbolicLink(file),"linked-storage-path");Files.deleteIfExists(file);
    }
    public void deleteUnpublishedArtifact(String artifactId) throws IOException {
        Path file=artifactPath(artifactId);requireSafe(file.getParent());
        Checks.require(!Files.isSymbolicLink(file),"linked-storage-path");Files.deleteIfExists(file);
    }
    public Path artifactPath(String artifactId) { Checks.id(artifactId); return root.resolve("artifacts").resolve(artifactId + ".bin"); }
    private Path chunkPath(String uploadId, int fileIndex, int index) {
        Checks.id(uploadId); Checks.require(fileIndex >= 0 && fileIndex < 20 && index >= 0 && index < 10000, "invalid-chunk-index");
        Path path = root.resolve("chunks").resolve(uploadId + "-" + fileIndex + "-" + index);
        requireSafe(path.getParent()); return path;
    }
    private void requireSafe(Path path) {
        try {
            Checks.require(path.toAbsolutePath().normalize().startsWith(root), "unsafe-storage-path");
            Path current = path;
            while (current != null) {
                Checks.require(!Files.isSymbolicLink(current), "linked-storage-path");
                if (Files.exists(current, LinkOption.NOFOLLOW_LINKS))
                    Checks.require(!Files.readAttributes(current, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther(), "special-storage-path");
                current = current.getParent();
            }
            if (Files.exists(path)) Checks.require(path.toRealPath().startsWith(root.toRealPath()), "storage-path-escape");
        } catch (IOException failure) { throw new ProtocolException("storage-unavailable", failure); }
    }
}
