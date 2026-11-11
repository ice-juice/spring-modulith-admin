package com.sma.admin.auth.internal.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Component;
import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;

@Component
public class TotpCodes {
    private final TimeBasedOneTimePasswordGenerator generator = new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30), 6);
    private final SecureRandom random = new SecureRandom();
    public String newSecret() { byte[] bytes = new byte[20]; random.nextBytes(bytes); return new Base32().encodeAsString(bytes); }
    public long acceptedStep(String secret, String code, Instant now, Long lastStep) {
        if (code == null || !code.matches("[0-9]{6}")) return -1;
        long step = Math.floorDiv(now.getEpochSecond(), 30);
        long matched = -1;
        for (long candidate = step - 1; candidate <= step + 1; candidate++) {
            if (candidate < 0 || (lastStep != null && candidate <= lastStep)) continue;
            try {
                String expected = generator.generateOneTimePasswordString(new SecretKeySpec(new Base32().decode(secret), "HmacSHA1"), Instant.ofEpochSecond(candidate * 30), Locale.ROOT);
                if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), code.getBytes(StandardCharsets.US_ASCII))) matched = candidate;
            } catch (GeneralSecurityException failure) { throw new IllegalStateException("TOTP provider unavailable"); }
        }
        return matched;
    }
}
