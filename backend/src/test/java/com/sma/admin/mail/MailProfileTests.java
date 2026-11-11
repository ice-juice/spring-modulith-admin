package com.sma.admin.mail;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.BizException;
import com.sma.admin.common.PageQuery;
import com.sma.admin.common.security.*;
import com.sma.admin.mail.internal.persistence.MailProfileRevisionEntity;
import com.sma.admin.mail.internal.persistence.MailProfileRevisionMapper;
import com.sma.admin.mail.internal.service.MailProfileService;
import com.sma.admin.support.IntegrationTest;
import com.sma.admin.support.Users;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest @AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties="sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class MailProfileTests {
    private static Path keyFile;
    @DynamicPropertySource static void credentials(DynamicPropertyRegistry properties) throws Exception {
        if (keyFile == null) {
            byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
            keyFile = Files.createTempFile("sma-p09-mail-test-", ".keys");
            Files.writeString(keyFile, "active=test\nkey.test=" + Base64.getEncoder().encodeToString(key));
        }
        properties.add("sma.common.credentials.key-file", () -> keyFile.toString());
    }
    @AfterAll static void removeKey() throws Exception { if (keyFile != null) Files.deleteIfExists(keyFile); }

    @Autowired MailProfileService service;
    @Autowired MailProfileRevisionMapper revisions;
    @MockitoSpyBean TenantSecretCodec secrets;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;

    private String code() { return "mail-" + UUID.randomUUID().toString().substring(0, 8); }
    private SmaPrincipal admin(long tenant, String... permissions) {
        return new SmaPrincipal(801, "mail-config-admin", tenant, "admin", null, false, DataAccess.none(), Set.of(permissions), "UTC");
    }
    private <T> T as(long tenant, Supplier<T> action) {
        return Users.as(admin(tenant, "mail:profile:view", "mail:profile:manage"), action);
    }
    private MailProfileService.Input input(String code, String host, char[] password) {
        return new MailProfileService.Input(code, "Tenant mail", host, 587, MailProfileService.TlsMode.STARTTLS,
                "mailer@example.test", password, "sender@example.test", "Example sender", Set.of("shop"), 100, 10, true);
    }

    @Test void identicalCodesInTwoTenantsHaveIndependentSecretsAndCounts() {
        String code = code();
        var a = as(911, () -> service.create(input(code, "smtp.a.example.test", "tenant-a".toCharArray())));
        var b = as(912, () -> service.create(input(code, "smtp.b.example.test", "tenant-b".toCharArray())));
        as(911, () -> {
            assertThat(service.get(a.id()).host()).isEqualTo("smtp.a.example.test");
            assertThat(service.page(new PageQuery(1, 20, null, null), null).total()).isEqualTo(1);
            assertThatThrownBy(() -> service.get(b.id())).isInstanceOfSatisfying(BizException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(MailErrorCode.PROFILE_NOT_FOUND));
            assertThatThrownBy(() -> service.disable(b.id(), 1)).isInstanceOf(BizException.class);
            assertThatThrownBy(() -> service.update(b.id(), 1, input(code, "smtp.a.example.test", null))).isInstanceOf(BizException.class);
            var stored = revision(a.id(), 1);
            assertThat(stored.getSecretEnvelope()).doesNotContain("tenant-a");
            try (var secret = secrets.decrypt(ref(911, a), "smtp-password", stored.getSecretEnvelope())) {
                assertThat(secret.copy()).containsExactly("tenant-a".toCharArray());
            }
            return null;
        });
        as(912, () -> {
            assertThat(service.get(b.id()).enabled()).isTrue();
            try (var secret = secrets.decrypt(ref(912, b), "smtp-password", revision(b.id(), 1).getSecretEnvelope())) {
                assertThat(secret.copy()).containsExactly("tenant-b".toCharArray());
            }
            return null;
        });
    }

    @Test void updatingWithoutPasswordPreservesItButCreatesBoundImmutableRevision() {
        as(913, () -> {
            var a = service.create(input(code(), "smtp.old.example.test", "keep-me".toCharArray()));
            String original = revision(a.id(), 1).getSecretEnvelope();
            var b = service.update(a.id(), 1, input(a.code(), "smtp.new.example.test", null));
            assertThat(b.configurationVersion()).isEqualTo(2);
            assertThat(b.credentialVersion()).isEqualTo(1);
            assertThat(revision(a.id(), 1).getHost()).isEqualTo("smtp.old.example.test");
            assertThat(revision(a.id(), 1).getSecretEnvelope()).isEqualTo(original);
            String updated = revision(a.id(), 2).getSecretEnvelope();
            assertThat(updated).isNotEqualTo(original);
            try (var secret = secrets.decrypt(ref(913, b), "smtp-password", updated)) {
                assertThat(secret.copy()).containsExactly("keep-me".toCharArray());
            }
            assertThatThrownBy(() -> secrets.decrypt(ref(913, b), "smtp-password", original)).isInstanceOf(BizException.class);
            var rotated = service.update(a.id(), 2, input(a.code(), "smtp.new.example.test", "replacement".toCharArray()));
            assertThat(rotated.credentialVersion()).isEqualTo(2);
            assertThat(rotated.configurationVersion()).isEqualTo(3);
            return null;
        });
    }

    @Test void disablingAdvancesVersionAndStaleEditsCannotReenable() {
        as(914, () -> {
            var a = service.create(input(code(), "smtp.example.test", "secret".toCharArray()));
            org.mockito.Mockito.doThrow(new BizException(com.sma.admin.common.CommonErrorCode.SERVICE_UNAVAILABLE))
                    .when(secrets).decrypt(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
            var disabled = service.disable(a.id(), 1);
            assertThat(disabled.enabled()).isFalse();
            assertThat(disabled.version()).isEqualTo(2);
            assertThat(disabled.configurationVersion()).isEqualTo(1);
            assertThatThrownBy(() -> service.update(a.id(), 1, input(a.code(), "smtp.example.test", null)))
                    .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MailErrorCode.VERSION_CONFLICT));
            assertThat(service.get(a.id()).enabled()).isFalse();
            return null;
        });
    }

    @Test void duplicateAndInvalidRequestsLeaveNoPartialVersions() {
        as(915, () -> {
            String code = code();
            var a = service.create(input(code, "smtp.example.test", "secret".toCharArray()));
            assertThatThrownBy(() -> service.create(input(code, "smtp.other.test", "other".toCharArray())))
                    .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MailErrorCode.DUPLICATE_CODE));
            assertThat(revisions.selectCount(new LambdaQueryWrapper<MailProfileRevisionEntity>())).isEqualTo(1);
            assertThatThrownBy(() -> service.update(a.id(), 1, input(code, "smtp.example.test\r\nInjected: x", null)))
                    .isInstanceOf(BizException.class);
            assertThat(service.get(a.id()).configurationVersion()).isEqualTo(1);
            assertThatThrownBy(() -> service.create(input(code(), "smtp.example.test", null))).isInstanceOf(BizException.class);
            return null;
        });
    }

    @Test void actualHttpAuthorizationAndResponsesDoNotExposePasswords() throws Exception {
        var a = as(916, () -> service.create(input(code(), "smtp.example.test", "no-response-secret".toCharArray())));
        mvc.perform(get("/api/mail/profiles/" + a.id())).andExpect(status().isUnauthorized());
        var reader = admin(916, "mail:profile:view");
        var result = mvc.perform(get("/api/mail/profiles/" + a.id()).with(Users.request(reader)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(result).contains("\"passwordConfigured\":true").doesNotContain("no-response-secret", "secretEnvelope", "\"password\":");
        mvc.perform(post("/api/mail/profiles/" + a.id() + "/disable").with(Users.request(reader))
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isForbidden());
        Users.as(reader, () -> {
            assertThatThrownBy(() -> service.disable(a.id(), 1)).isInstanceOf(AccessDeniedException.class); return null;
        });
    }

    @Test void httpCreateAndUpdateAcceptWriteOnlyPasswordAndKeepTenantOwnership() throws Exception {
        var manager = admin(917, "mail:profile:view", "mail:profile:manage");
        var configuration = json.createObjectNode();
        configuration.put("code", code()).put("name", "HTTP mail").put("host", "smtp.example.test").put("port", 587)
                .put("tlsMode", "STARTTLS").put("username", "mailer@example.test").put("password", "write-only-secret")
                .put("fromAddress", "sender@example.test").put("fromName", "Sender").put("dailyQuota", 100)
                .put("perMinuteLimit", 10).put("enabled", true);
        configuration.putArray("applications").add("shop");
        String createKey=java.util.UUID.randomUUID().toString();
        var created = mvc.perform(post("/api/mail/profiles").header("Idempotency-Key",createKey).with(Users.request(manager)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(configuration))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(created).doesNotContain("write-only-secret", "secretEnvelope");
        String id = json.readTree(created).path("data").path("id").asText();
        assertThat(created).contains("\"id\":\"" + id + "\"");
        mvc.perform(post("/api/mail/profiles").header("Idempotency-Key",createKey).with(Users.request(manager)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(configuration))).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("common.request.completed"));
        configuration.put("password","different-write-only-secret");
        mvc.perform(post("/api/mail/profiles").header("Idempotency-Key",createKey).with(Users.request(manager)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(configuration))).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("common.request.conflict"));
        configuration.remove("password"); configuration.put("host", "smtp.updated.example.test");
        var update = json.createObjectNode().put("expectedVersion", 1); update.set("configuration", configuration);
        mvc.perform(put("/api/mail/profiles/" + id).header("Idempotency-Key",java.util.UUID.randomUUID().toString()).with(Users.request(manager)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(update))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.host").value("smtp.updated.example.test"));
        var other = admin(918, "mail:profile:view");
        mvc.perform(get("/api/mail/profiles/" + id).with(Users.request(other))).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.error").value("mail.error.profile-not-found"))
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/mail/profiles/"+id).header("Host","t917-admin.test").with(Users.request(other))).andExpect(status().isForbidden());
    }

    private MailProfileRevisionEntity revision(long profile, long version) {
        return revisions.selectOne(new LambdaQueryWrapper<MailProfileRevisionEntity>()
                .eq(MailProfileRevisionEntity::getProfileId, profile)
                .eq(MailProfileRevisionEntity::getConfigurationVersion, version));
    }
    private TenantConfigurationRef ref(long tenant, MailProfileService.View view) {
        return new TenantConfigurationRef(tenant, "mail", view.id(), view.configurationVersion(), view.credentialVersion());
    }
}
