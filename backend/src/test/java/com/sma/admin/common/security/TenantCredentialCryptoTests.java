package com.sma.admin.common.security;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.common.BizException;
import com.sma.admin.common.internal.security.TenantCredentialCrypto;
import com.sma.admin.common.internal.security.TenantCredentialProperties;
import tools.jackson.databind.json.JsonMapper;

class TenantCredentialCryptoTests {
    @TempDir Path directory;
    private final TenantConfigurationRef a = new TenantConfigurationRef(23, "mail", 42, 1, 1);

    private Path ring(String active, String first, String second) throws Exception {
        Path path = directory.resolve(UUID.randomUUID() + ".keys");
        Files.writeString(path, "active=" + active + "\nkey.First=" + first + (second == null ? "" : "\nkey.Second=" + second));
        return path;
    }
    private String key() { byte[] value = new byte[32]; new SecureRandom().nextBytes(value); return Base64.getEncoder().encodeToString(value); }
    private TenantCredentialCrypto codec(Path file) { return new TenantCredentialCrypto(new TenantCredentialProperties(file.toString())); }

    @Test void ciphertextIsRandomizedAndRoundTripsOnlyInItsOwnedContext() throws Exception {
        var codec = codec(ring("First", key(), null));
        ActorContext.withinJob(23, "mail:1", () -> {
            char[] input = "测试-SMTP-password".toCharArray();
            String first = codec.encrypt(a, "smtp-password", input);
            String second = codec.encrypt(a, "smtp-password", input);
            assertThat(first).isNotEqualTo(second).doesNotContain(new String(input));
            try (var secret = codec.decrypt(a, "smtp-password", first)) { assertThat(secret.copy()).containsExactly(input); }
            assertThat(input).containsExactly("测试-SMTP-password".toCharArray());
            return null;
        });
    }

    @Test void crossTenantCopiesAndChangedConfigurationVersionsCannotDecrypt() throws Exception {
        var codec = codec(ring("First", key(), null));
        String envelope = ActorContext.withinJob(23, "mail:1", () -> codec.encrypt(a, "smtp-password", "secret".toCharArray()));
        ActorContext.withinJob(24, "mail:2", () -> {
            assertThatThrownBy(() -> codec.decrypt(a, "smtp-password", envelope)).isInstanceOf(AccessDeniedException.class);
            var other = new TenantConfigurationRef(24, "mail", 42, 1, 1);
            assertThatThrownBy(() -> codec.decrypt(other, "smtp-password", envelope)).isInstanceOf(BizException.class);
            return null;
        });
        ActorContext.withinJob(23, "mail:3", () -> {
            for (var ref : java.util.List.of(new TenantConfigurationRef(23, "file", 42, 1, 1),
                    new TenantConfigurationRef(23, "mail", 43, 1, 1), new TenantConfigurationRef(23, "mail", 42, 2, 1),
                    new TenantConfigurationRef(23, "mail", 42, 1, 2))) {
                assertThatThrownBy(() -> codec.decrypt(ref, "smtp-password", envelope)).isInstanceOf(BizException.class);
            }
            assertThatThrownBy(() -> codec.decrypt(a, "other-purpose", envelope)).isInstanceOf(BizException.class);
            return null;
        });
    }

    @Test void rotationReadsOldMaterialAndReencryptsWithNewKeyButRetiredKeyIsRejected() throws Exception {
        String firstKey = key(), secondKey = key();
        var old = codec(ring("First", firstKey, null));
        var rotating = codec(ring("Second", firstKey, secondKey));
        Path retiredRing = directory.resolve("retired.keys");
        Files.writeString(retiredRing, "active=Second\nkey.Second=" + secondKey);
        var retired = codec(retiredRing);
        ActorContext.withinJob(23, "mail:1", () -> {
            String before = old.encrypt(a, "smtp-password", "secret".toCharArray());
            String after = rotating.reencrypt(a, "smtp-password", before);
            assertThat(after).startsWith("v1.Second.");
            try (var value = retired.decrypt(a, "smtp-password", after)) { assertThat(value.copy()).containsExactly("secret".toCharArray()); }
            assertThatThrownBy(() -> retired.decrypt(a, "smtp-password", before)).isInstanceOf(BizException.class);
            return null;
        });
    }

    @Test void tamperingAndMissingKeysFailWithoutLeakingSecretOrEnvelope() throws Exception {
        var codec = codec(ring("First", key(), null));
        ActorContext.withinJob(23, "mail:1", () -> {
            String original = codec.encrypt(a, "smtp-password", "do-not-log-this".toCharArray());
            String[] parts = original.split("\\.");
            byte[] data = Base64.getUrlDecoder().decode(parts[3]); data[0] ^= 1;
            String tampered = String.join(".", parts[0], parts[1], parts[2], Base64.getUrlEncoder().withoutPadding().encodeToString(data));
            for (String value : java.util.List.of(tampered, "invalid", original.replace(".First.", ".first."), "x".repeat(90001))) {
                assertThatThrownBy(() -> codec.decrypt(a, "smtp-password", value)).isInstanceOf(BizException.class)
                        .hasMessage("common.error.unavailable");
            }
            var disabled = new TenantCredentialCrypto(new TenantCredentialProperties(""));
            assertThat(disabled.configured()).isFalse();
            assertThatThrownBy(() -> disabled.encrypt(a, "smtp-password", "secret".toCharArray())).isInstanceOf(BizException.class);
            return null;
        });
        assertThatThrownBy(() -> codec.encrypt(a, "smtp-password", "secret".toCharArray())).isInstanceOf(AccessDeniedException.class);
    }

    @Test void secretIsRedactedInLogsAndJsonAndErasedAfterClose() throws Exception {
        char[] original = "private-token".toCharArray();
        var secret = new SecretValue(original);
        Arrays.fill(original, 'x');
        assertThat(secret.copy()).containsExactly("private-token".toCharArray());
        assertThat(secret.toString()).isEqualTo("[REDACTED]");
        assertThat(JsonMapper.builder().build().writeValueAsString(secret)).isEqualTo("\"[REDACTED]\"");
        secret.close(); secret.close();
        assertThatThrownBy(secret::copy).isInstanceOf(IllegalStateException.class);
    }

    @Test void malformedDuplicateAndUnboundedKeyFilesFailAtStartup() throws Exception {
        for (String content : java.util.List.of("active=missing\nkey.First=" + key(), "active=First\nkey.First=short",
                "active=First\nactive=Second\nkey.First=" + key(), "active=First\nkey.First=" + key() + "\nunknown=value")) {
            Path path = directory.resolve(UUID.randomUUID() + ".keys"); Files.writeString(path, content);
            assertThatThrownBy(() -> codec(path)).isInstanceOf(IllegalStateException.class)
                    .hasMessage("Invalid tenant credential key ring");
        }
        Path large = directory.resolve("large.keys"); Files.writeString(large, "x".repeat(16385));
        assertThatThrownBy(() -> codec(large)).isInstanceOf(IllegalStateException.class);
    }

    @Test void keyedRequestFingerprintsSurviveRotationAndRejectOtherScopes() throws Exception {
        String first=key(),second=key();var old=codec(ring("First",first,null));var rotated=codec(ring("Second",first,second));
        ActorContext.withinJob(23,"mail:1",()->{
            String hash=old.fingerprint(a,"delivery-request","123456".toCharArray());
            assertThat(hash).doesNotContain("123456");
            assertThat(rotated.matchesFingerprint(a,"delivery-request","123456".toCharArray(),hash)).isTrue();
            assertThat(rotated.matchesFingerprint(a,"delivery-request","000000".toCharArray(),hash)).isFalse();
            assertThat(rotated.matchesFingerprint(a,"other-purpose","123456".toCharArray(),hash)).isFalse();
            assertThat(rotated.matchesFingerprint(new TenantConfigurationRef(23,"mail",43,1,1),"delivery-request","123456".toCharArray(),hash)).isFalse();
            return null;
        });
    }
}
