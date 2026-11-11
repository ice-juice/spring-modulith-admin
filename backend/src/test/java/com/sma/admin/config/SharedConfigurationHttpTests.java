package com.sma.admin.config;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.PageQuery;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;
import com.sma.admin.config.internal.service.ConfigurationCatalogRegistry.Kind;
import com.sma.admin.config.internal.service.SharedConfigurationService;

@IntegrationTest @AutoConfigureMockMvc
@Import(SharedConfigurationTests.CatalogConfiguration.class)
@TestPropertySource(properties={"sma.tenant.platform-admin-user-ids=991", "sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "sma.security.requests.minimum-write-interval=100ms"})
class SharedConfigurationHttpTests {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired SharedConfigurationService shared;
    private static final Set<String> PERMISSIONS = Set.of("config:shared:view", "config:shared:edit", "config:shared:publish");
    private SmaPrincipal actor(long user, long tenant, Set<String> permissions) { return new SmaPrincipal(user, "operator", tenant, "admin", null, false, DataAccess.unrestricted(), permissions, "UTC"); }
    private SharedConfigurationService.Detail detail(String key) {
        return Users.as(actor(991, 1, PERMISSIONS), () -> {
            var result = shared.page(new PageQuery(1, 20, null, null), Kind.PARAMETER, key);
            return shared.get(result.items().stream().filter(item -> item.key().equals(key)).findFirst().orElseThrow().id());
        });
    }
    private MockHttpServletResponse send(MockHttpServletRequestBuilder request, SmaPrincipal actor) throws Exception {
        return mvc.perform(request.contentType("application/json").with(Users.request(actor))).andReturn().getResponse();
    }

    @Test void platformIdentityAndActionsAreRequiredAndTheDirectoryDoesNotReturnValues() throws Exception {
        assertThat(mvc.perform(get("/api/config/shared-configurations")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(send(get("/api/config/shared-configurations"), actor(992, 1, PERMISSIONS)).getStatus()).isEqualTo(403);
        assertThat(send(get("/api/config/shared-configurations"), actor(991, 8160, PERMISSIONS)).getStatus()).isEqualTo(403);
        assertThat(send(get("/api/config/shared-configurations"), actor(991, 1, Set.of())).getStatus()).isEqualTo(403);
        var viewer = actor(991, 1, Set.of("config:shared:view"));
        var page = send(get("/api/config/shared-configurations").param("kind", "PARAMETER").param("keyword", "config.s5."), viewer);
        assertThat(page.getStatus()).as(page.getContentAsString()).isEqualTo(200);
        assertThat(json.readTree(page.getContentAsString()).at("/code").asInt()).isZero();
        assertThat(page.getContentAsString()).doesNotContain("draftJson", "parameterDraft", "seed", "contentJson");
        var before = detail("config.s5.authority");
        String content = json.writeValueAsString(Map.of("expectedVersion", before.draftVersion(), "content", Map.of("schemaVersion", 1, "label", Map.of("text", "Denied"), "value", "Denied")));
        assertThat(send(put("/api/config/shared-configurations/" + before.id() + "/parameter-draft").header("Idempotency-Key", UUID.randomUUID().toString()).content(content), viewer).getStatus()).isEqualTo(403);
        assertThat(detail("config.s5.authority").draftVersion()).isEqualTo(before.draftVersion());
    }

    @Test void draftAndPublicationUseLocalOwnedReceiptsAndReplayNeverPublishesTwice() throws Exception {
        var operator = actor(991, 1, PERMISSIONS); var before = detail("config.s5.draft");
        String path = "/api/config/shared-configurations/" + before.id();
        String draft = json.writeValueAsString(Map.of("expectedVersion", Long.toString(before.draftVersion()), "content", Map.of("schemaVersion", 1, "label", Map.of("text", "HTTP label"), "value", "HTTP draft")));
        assertThat(send(put(path + "/parameter-draft").content(draft), operator).getStatus()).isEqualTo(400);
        String draftKey = UUID.randomUUID().toString();
        var saved = send(put(path + "/parameter-draft").header("Idempotency-Key", draftKey).content(draft), operator);
        assertThat(saved.getStatus()).as(saved.getContentAsString()).isEqualTo(200); assertThat(saved.getHeader("X-Operation-Id")).isNotBlank();
        var savedData = json.readTree(saved.getContentAsString()).path("data");
        assertThat(savedData.path("draftVersion").isString()).isTrue();
        assertThat(Users.as(operator, () -> shared.published(Kind.PARAMETER, before.key()).orElseThrow().parameter().value())).isEqualTo("seed");
        var draftReplay = send(put(path + "/parameter-draft").header("Idempotency-Key", draftKey).content(draft), operator);
        assertThat(draftReplay.getStatus()).isEqualTo(409); assertThat(draftReplay.getContentAsString()).doesNotContain("HTTP draft");
        Thread.sleep(120);
        String publishKey = UUID.randomUUID().toString();
        String publish = json.writeValueAsString(Map.of("expectedDraftVersion", savedData.path("draftVersion").asText(), "expectedPublishedRevisionId", savedData.path("publishedRevisionId").asText()));
        var published = send(post(path + "/publications").header("Idempotency-Key", publishKey).content(publish), operator);
        assertThat(published.getStatus()).as(published.getContentAsString()).isEqualTo(200);
        String operation = published.getHeader("X-Operation-Id"); assertThat(operation).isNotBlank();
        String revisionId = json.readTree(published.getContentAsString()).at("/data/revisionId").asText(); assertThat(revisionId).isNotBlank();
        var replay = send(post(path + "/publications").header("Idempotency-Key", publishKey).content(publish), operator);
        assertThat(replay.getStatus()).isEqualTo(409); assertThat(json.readTree(replay.getContentAsString()).at("/data/operationId").asText()).isEqualTo(operation);
        assertThat(replay.getContentAsString()).doesNotContain("HTTP draft", "revisionId");
        assertThat(send(post(path + "/publications").header("Idempotency-Key", publishKey).content(publish), actor(991, 1, Set.of("config:shared:view"))).getStatus()).isEqualTo(403);
        assertThat(send(post(path + "/publications").header("Idempotency-Key", publishKey).content(publish), actor(992, 1, PERMISSIONS)).getStatus()).isEqualTo(403);
        var lookup = send(post("/api/common/requests/lookup").content(json.writeValueAsString(Map.of("key", publishKey))), operator);
        assertThat(lookup.getStatus()).isEqualTo(200); assertThat(json.readTree(lookup.getContentAsString()).at("/data/state").asText()).isEqualTo("SUCCEEDED");
        var history = send(get(path + "/revisions"), operator);
        assertThat(json.readTree(history.getContentAsString()).at("/data/total").asText()).isEqualTo("2");
        assertThat(history.getContentAsString()).doesNotContain("HTTP draft");
        var snapshot = send(get(path + "/revisions/" + revisionId), operator);
        assertThat(json.readTree(snapshot.getContentAsString()).at("/data/parameter/value").asText()).isEqualTo("HTTP draft");
        Thread.sleep(120);
        var stale = send(put(path + "/parameter-draft").header("Idempotency-Key", UUID.randomUUID().toString()).content(draft), operator);
        assertThat(json.readTree(stale.getContentAsString()).path("error").asText()).isEqualTo("config.error.shared-stale");
    }
}
