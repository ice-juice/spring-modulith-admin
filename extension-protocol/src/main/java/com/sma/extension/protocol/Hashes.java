package com.sma.extension.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Hashes {
    private Hashes() {}
    public static String sha256(byte[] bytes) { return HexFormat.of().formatHex(digest().digest(bytes)); }
    public static DigestResult sha256(InputStream input, long limit) throws IOException {
        Checks.require(limit >= 0, "invalid-size-limit");
        var digest = digest(); long total = 0;
        byte[] buffer = new byte[65536];
        for (int read; (read = input.read(buffer)) != -1;) {
            Checks.require(total <= limit - read, "size-limit-exceeded");
            total += read; digest.update(buffer, 0, read);
        }
        return new DigestResult(HexFormat.of().formatHex(digest.digest()), total);
    }
    public static boolean equalHex(String first, String second) {
        Checks.hash(first); Checks.hash(second);
        return MessageDigest.isEqual(HexFormat.of().parseHex(first), HexFormat.of().parseHex(second));
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    public record DigestResult(String sha256, long bytes) {}
}
