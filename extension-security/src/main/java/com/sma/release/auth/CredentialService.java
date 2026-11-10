package com.sma.release.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base32;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;
import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.sma.extension.protocol.Checks;
import com.sma.extension.protocol.Hashes;
import com.sma.extension.protocol.ProtocolException;
import com.sma.release.crypto.SecretVault;

@Component
public class CredentialService {
    private final SecureRandom random = new SecureRandom();
    private final Argon2PasswordEncoder passwords = new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
    private final TimeBasedOneTimePasswordGenerator totp = new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30), 6);
    private final SecretVault vault;
    private final String dummyHash;
    public CredentialService(SecretVault vault) { this.vault = vault; dummyHash = passwords.encode(randomToken()); }
    public String passwordHash(String password) {
        Checks.require(password != null && password.length() >= 12 && password.length() <= 128 && !password.isBlank(), "weak-password");
        String normalized = password.toLowerCase(java.util.Locale.ROOT);
        Checks.require(!List.of("password123456", "123456789012", "qwerty12345678", "administrator").contains(normalized), "weak-password");
        return passwords.encode(password);
    }
    public boolean passwordMatches(String password, String hash) {
        if (password == null || password.length() > 128) return false;
        return passwords.matches(password, hash == null ? dummyHash : hash) && hash != null;
    }
    public String randomToken() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public String tokenHash(String token) {
        Checks.require(token != null && token.length() >= 32 && token.length() <= 128, "invalid-token");
        return Hashes.sha256(token.getBytes(StandardCharsets.UTF_8));
    }
    public String newSealedTotp(String userId) {
        byte[] secret = new byte[20]; random.nextBytes(secret);
        return vault.seal("totp:" + userId, secret);
    }
    public String totpSecret(String userId, String sealed) {
        byte[] secret = vault.open("totp:" + userId, sealed);
        try { return new Base32().encodeToString(secret).replace("=", ""); }
        finally { java.util.Arrays.fill(secret, (byte)0); }
    }
    public long validUnusedTotpStep(SecurityRecords.User user, String code, Instant now) {
        if (code == null || !code.matches("[0-9]{6}")) return -1;
        byte[] secret = vault.open("totp:" + user.id(), user.sealedTotp());
        try {
            long current = Math.floorDiv(now.getEpochSecond(), 30);
            for (long step = current - 1; step <= current + 1; step++) {
                if (step <= user.usedTotpStep()) continue;
                String expected = String.format(java.util.Locale.ROOT, "%06d", totp.generateOneTimePassword(new SecretKeySpec(secret, "HmacSHA1"), Instant.ofEpochSecond(step*30)));
                if (java.security.MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), code.getBytes(StandardCharsets.US_ASCII))) return step;
            }
            return -1;
        } catch (java.security.InvalidKeyException failure) { throw new ProtocolException("totp-unavailable", failure); }
        finally { java.util.Arrays.fill(secret, (byte)0); }
    }
    public List<String> recoveryCodes() {
        List<String> values = new ArrayList<>(); for (int i = 0; i < 10; i++) values.add(randomToken()); return List.copyOf(values);
    }
}
