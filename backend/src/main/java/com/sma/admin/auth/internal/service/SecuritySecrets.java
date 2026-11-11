package com.sma.admin.auth.internal.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import com.sma.admin.auth.AuthErrorCode;
import com.sma.admin.common.BizException;

@Component
@EnableConfigurationProperties(AccountSecurityProperties.class)
public class SecuritySecrets {
    private final Map<String, byte[]> encryption = new HashMap<>();
    private final Map<String, byte[]> peppers = new HashMap<>();
    private final String encryptionId;
    private final String pepperId;
    private final SecureRandom random = new SecureRandom();

    public SecuritySecrets(AccountSecurityProperties settings) {
        var properties = new Properties();
        if (!settings.keyFile().isBlank()) {
            try (InputStream stream = Files.newInputStream(Path.of(settings.keyFile()))) { properties.load(stream); }
            catch (Exception failure) { throw new IllegalStateException("Cannot load auth security key file"); }
        }
        encryptionId = properties.getProperty("encryption.active", "");
        pepperId = properties.getProperty("pepper.active", "");
        load(properties, "encryption", encryption);
        load(properties, "pepper", peppers);
        if (!settings.keyFile().isBlank() && (!encryption.containsKey(encryptionId) || !peppers.containsKey(pepperId))) {
            throw new IllegalStateException("Auth security key file needs both active key rings");
        }
        for (byte[] key : encryption.values()) for (byte[] pepper : peppers.values()) {
            if (MessageDigest.isEqual(key, pepper)) throw new IllegalStateException("Encryption and pepper keys must differ");
        }
    }

    private static void load(Properties properties, String prefix, Map<String, byte[]> ring) {
        for (String name : properties.stringPropertyNames()) {
            if (!name.startsWith(prefix + ".") || name.equals(prefix + ".active")) continue;
            String id = name.substring(prefix.length() + 1);
            if (!id.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalStateException("Invalid security key identifier");
            try {
                byte[] key = Base64.getDecoder().decode(properties.getProperty(name));
                if (key.length != 32) throw new IllegalArgumentException();
                ring.put(id, key);
            } catch (IllegalArgumentException invalid) { throw new IllegalStateException("Auth security keys must be base64 encoded 32-byte keys"); }
        }
    }

    public boolean configured() { return encryption.containsKey(encryptionId) && peppers.containsKey(pepperId); }
    public String encryptionId() { key(encryption, encryptionId); return encryptionId; }
    public String reencrypt(String envelope, String context) {
        String plaintext = decrypt(envelope, context);
        return envelope.startsWith("v1." + encryptionId() + ".") ? envelope : encrypt(plaintext, context);
    }
    public String pepperId() { key(peppers, pepperId); return pepperId; }
    private byte[] key(Map<String, byte[]> ring, String id) {
        byte[] value = ring.get(id);
        if (value == null) throw new BizException(AuthErrorCode.SECURITY_UNAVAILABLE);
        return value;
    }

    /** The envelope carries its key ID; AAD binds it to one tenant/account/purpose. */
    public String encrypt(String plaintext, String context) {
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(encryption, encryptionId), "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(("v1:" + encryptionId + ":" + context).getBytes(StandardCharsets.UTF_8));
            return "v1." + encryptionId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce) + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) { throw new BizException(AuthErrorCode.SECURITY_UNAVAILABLE); }
    }

    public String decrypt(String envelope, String context) {
        try {
            String[] parts = envelope.split("\\.", -1);
            if (parts.length != 4 || !parts[0].equals("v1")) throw new IllegalArgumentException();
            byte[] nonce = Base64.getUrlDecoder().decode(parts[2]);
            if (nonce.length != 12) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(encryption, parts[1]), "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(("v1:" + parts[1] + ":" + context).getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.getUrlDecoder().decode(parts[3])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException | NullPointerException failure) {
            throw new BizException(AuthErrorCode.SECURITY_UNAVAILABLE);
        }
    }

    public String prehash(String password, String id) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key(peppers, id), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(password.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) { throw new BizException(AuthErrorCode.SECURITY_UNAVAILABLE); }
    }
}
