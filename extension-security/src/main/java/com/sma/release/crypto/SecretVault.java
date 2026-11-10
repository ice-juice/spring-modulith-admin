package com.sma.release.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import com.sma.release.ReleaseProperties;
import com.sma.extension.protocol.Checks;
import com.sma.extension.protocol.ProtocolException;

/** Deployment-supplied KEK, never copied to the host or database. Software protection, not a hardware vault. */
@Component
public final class SecretVault {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();
    public SecretVault(ReleaseProperties properties) {
        try {
            byte[] decoded = Base64.getDecoder().decode(properties.encryptionKey());
            Checks.require(decoded.length == 32, "invalid-release-encryption-key");
            key = new SecretKeySpec(decoded, "AES"); java.util.Arrays.fill(decoded, (byte)0);
        } catch (IllegalArgumentException failure) { throw new ProtocolException("invalid-release-encryption-key"); }
    }
    public String seal(String purpose, byte[] plaintext) {
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8)); byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] result = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, result, 0, nonce.length); System.arraycopy(ciphertext, 0, result, nonce.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception failure) { throw new ProtocolException("secret-encryption-failed", failure); }
    }
    public byte[] open(String purpose, String sealed) {
        try {
            byte[] bytes = Base64.getDecoder().decode(sealed); Checks.require(bytes.length >= 28 && bytes.length <= 65536, "invalid-sealed-secret");
            var cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, bytes, 0, 12));
            cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8)); return cipher.doFinal(bytes, 12, bytes.length-12);
        } catch (Exception failure) { throw new ProtocolException("secret-decryption-failed", failure); }
    }
}
