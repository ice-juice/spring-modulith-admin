package com.sma.admin.common.internal.security;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import jakarta.annotation.PreDestroy;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import com.sma.admin.common.BizException;
import com.sma.admin.common.CommonErrorCode;
import com.sma.admin.common.security.SecretValue;
import com.sma.admin.common.security.TenantConfigurationRef;
import com.sma.admin.common.security.TenantSecretCodec;

/** AES-256-GCM plus HKDF-SHA256 tenant/module key separation. No plaintext, envelope or key in errors/logs. */
@Component
@EnableConfigurationProperties(TenantCredentialProperties.class)
public class TenantCredentialCrypto implements TenantSecretCodec {
    private static final int MAX_PLAINTEXT_BYTES = 65536;
    private static final int MAX_ENVELOPE_CHARS = 90000;
    private final Map<String, byte[]> keys = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final String activeId;

    public TenantCredentialCrypto(TenantCredentialProperties settings) {
        if (settings.keyFile().isEmpty()) { activeId = ""; return; }
        try {
            Path path = Path.of(settings.keyFile());
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 16384) throw new IllegalArgumentException();
            Map<String, String> entries = new HashMap<>();
            for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int separator = line.indexOf('=');
                if (separator < 1 || entries.putIfAbsent(line.substring(0, separator).trim(), line.substring(separator + 1).trim()) != null) {
                    throw new IllegalArgumentException();
                }
            }
            activeId = entries.remove("active");
            if (activeId == null || !activeId.matches("[A-Za-z0-9_-]{1,64}") || entries.isEmpty() || entries.size() > 16) {
                throw new IllegalArgumentException();
            }
            for (var entry : entries.entrySet()) {
                if (!entry.getKey().matches("key\\.[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException();
                byte[] key = Base64.getDecoder().decode(entry.getValue());
                if (key.length != 32) throw new IllegalArgumentException();
                keys.put(entry.getKey().substring(4), key);
            }
            if (!keys.containsKey(activeId)) throw new IllegalArgumentException();
        } catch (Exception failure) {
            destroy();
            throw new IllegalStateException("Invalid tenant credential key ring");
        }
    }

    @Override public boolean configured() { return keys.containsKey(activeId); }

    @Override public String encrypt(TenantConfigurationRef reference, String purpose, char[] plaintext) {
        authorize(reference, purpose);
        if (plaintext == null || plaintext.length == 0 || plaintext.length > MAX_PLAINTEXT_BYTES) throw unavailable();
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(plaintext));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        if (encoded.hasArray()) Arrays.fill(encoded.array(), (byte) 0);
        byte[] derived = null;
        try {
            if (bytes.length > MAX_PLAINTEXT_BYTES) throw unavailable();
            derived = derive(activeId, reference);
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(derived, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(activeId, reference, purpose));
            var encoder = Base64.getUrlEncoder().withoutPadding();
            return "v1." + activeId + "." + encoder.encodeToString(nonce) + "." + encoder.encodeToString(cipher.doFinal(bytes));
        } catch (GeneralSecurityException failure) { throw unavailable(); }
        finally {
            Arrays.fill(bytes, (byte) 0);
            if (derived != null) Arrays.fill(derived, (byte) 0);
        }
    }

    @Override public SecretValue decrypt(TenantConfigurationRef reference, String purpose, String envelope) {
        authorize(reference, purpose);
        byte[] derived = null;
        byte[] plaintext = null;
        try {
            if (envelope == null || envelope.length() > MAX_ENVELOPE_CHARS) throw unavailable();
            String[] parts = envelope.split("\\.", -1);
            if (parts.length != 4 || !parts[0].equals("v1") || !parts[1].matches("[A-Za-z0-9_-]{1,64}")) throw unavailable();
            byte[] nonce = Base64.getUrlDecoder().decode(parts[2]);
            byte[] encrypted = Base64.getUrlDecoder().decode(parts[3]);
            if (nonce.length != 12 || encrypted.length < 17 || encrypted.length > MAX_PLAINTEXT_BYTES + 16) throw unavailable();
            derived = derive(parts[1], reference);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(derived, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(parts[1], reference, purpose));
            plaintext = cipher.doFinal(encrypted);
            CharBuffer decoded = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(plaintext));
            char[] characters = new char[decoded.remaining()]; decoded.get(characters);
            try { return new SecretValue(characters); }
            finally {
                Arrays.fill(characters, '\0');
                if (decoded.hasArray()) Arrays.fill(decoded.array(), '\0');
            }
        } catch (GeneralSecurityException | IllegalArgumentException failure) { throw unavailable(); }
        finally {
            if (derived != null) Arrays.fill(derived, (byte) 0);
            if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
        }
    }

    @Override public String reencrypt(TenantConfigurationRef reference, String purpose, String envelope) {
        try (var secret = decrypt(reference, purpose, envelope)) {
            char[] characters = secret.copy();
            try { return encrypt(reference, purpose, characters); }
            finally { Arrays.fill(characters, '\0'); }
        }
    }

    @Override public String fingerprint(TenantConfigurationRef reference, String purpose, char[] plaintext) {
        authorize(reference,purpose);
        return "h1."+activeId+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(digest(activeId,reference,purpose,plaintext));
    }

    @Override public boolean matchesFingerprint(TenantConfigurationRef reference,String purpose,char[] plaintext,String fingerprint) {
        authorize(reference,purpose);
        if(fingerprint==null || fingerprint.length()>160) return false;
        String[] parts=fingerprint.split("\\.",-1);
        if(parts.length!=3 || !parts[0].equals("h1") || !parts[1].matches("[A-Za-z0-9_-]{1,64}")) return false;
        try {return java.security.MessageDigest.isEqual(digest(parts[1],reference,purpose,plaintext),Base64.getUrlDecoder().decode(parts[2]));}
        catch(IllegalArgumentException malformed){return false;}
    }

    private byte[] digest(String keyId,TenantConfigurationRef reference,String purpose,char[] plaintext) {
        if(plaintext==null || plaintext.length>MAX_PLAINTEXT_BYTES) throw unavailable();
        byte[] master=keys.get(keyId);if(master==null)throw unavailable();
        byte[] derived=new byte[32];var hkdf=new HKDFBytesGenerator(new SHA256Digest());
        hkdf.init(new HKDFParameters(master,null,("sma:fingerprint:v1:"+reference.partitionKey()+":"+purpose).getBytes(StandardCharsets.UTF_8)));
        hkdf.generateBytes(derived,0,32);
        ByteBuffer encoded=StandardCharsets.UTF_8.encode(CharBuffer.wrap(plaintext));byte[] bytes=new byte[encoded.remaining()];encoded.get(bytes);
        if(encoded.hasArray())Arrays.fill(encoded.array(),(byte)0);
        try {if(bytes.length>MAX_PLAINTEXT_BYTES)throw unavailable();var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(derived,"HmacSHA256"));return mac.doFinal(bytes);}
        catch(GeneralSecurityException error){throw unavailable();}
        finally {Arrays.fill(derived,(byte)0);Arrays.fill(bytes,(byte)0);}
    }

    private void authorize(TenantConfigurationRef reference, String purpose) {
        if (reference == null) throw unavailable();
        reference.requireCurrentTenant();
        if (purpose == null || !purpose.matches("[a-z][a-z0-9.-]{0,63}")) throw unavailable();
        if (!configured()) throw unavailable();
    }

    private byte[] derive(String keyId, TenantConfigurationRef reference) {
        byte[] master = keys.get(keyId);
        if (master == null) throw unavailable();
        var hkdf = new HKDFBytesGenerator(new SHA256Digest());
        hkdf.init(new HKDFParameters(master, null,
                ("sma:credentials:v1:" + reference.tenantId() + ":" + reference.module()).getBytes(StandardCharsets.UTF_8)));
        byte[] derived = new byte[32]; hkdf.generateBytes(derived, 0, derived.length); return derived;
    }

    private byte[] aad(String keyId, TenantConfigurationRef reference, String purpose) {
        return ("v1|" + keyId + "|" + reference.partitionKey() + "|" + purpose).getBytes(StandardCharsets.UTF_8);
    }

    private static BizException unavailable() { return new BizException(CommonErrorCode.SERVICE_UNAVAILABLE); }

    @PreDestroy public void destroy() { keys.values().forEach(key -> Arrays.fill(key, (byte) 0)); keys.clear(); }
}
