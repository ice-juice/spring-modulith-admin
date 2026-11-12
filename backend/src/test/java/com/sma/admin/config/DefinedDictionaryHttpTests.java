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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc @Import(DefinedDictionaryTests.CatalogConfiguration.class)
@TestPropertySource(properties={"sma.tenant.platform-admin-user-ids=991","sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","sma.security.requests.minimum-write-interval=100ms"})
class DefinedDictionaryHttpTests {
    private static final String ROOT="/api/config/effective-dictionaries";
    private static final Set<String> EDIT=Set.of("config:dictionary:view","config:dictionary:update","config:dictionary:create");
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    private SmaPrincipal actor(long user,long tenant,Set<String> permissions){return new SmaPrincipal(user,"dictionary-http",tenant,"admin",null,false,DataAccess.unrestricted(),permissions,"UTC");}
    private MockHttpServletResponse send(MockHttpServletRequestBuilder request,SmaPrincipal actor) throws Exception {
        return mvc.perform(request.contentType("application/json").header("Accept-Language","en-US").with(Users.request(actor))).andReturn().getResponse();
    }
    private JsonNode data(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        var body=json.readTree(response.getContentAsString());assertThat(body.path("code").asInt()).as(response.getContentAsString()).isZero();return body.path("data");
    }
    private String error(MockHttpServletResponse response) throws Exception {return json.readTree(response.getContentAsString()).path("error").asText();}
    private Map<String,Object> versions(JsonNode view){return Map.of("expectedPlatformRevision",view.at("/revision/platform").asText(),"expectedTenantRevision",view.at("/revision/tenant").asText());}
    private String key(){return UUID.randomUUID().toString();}
    private JsonNode entry(JsonNode view,String code){for(var item:view.path("entries"))if(item.path("code").asText().equals(code))return item;throw new AssertionError("missing "+code);}

    @Test void directoryAndDetailRequireViewAndExposeOriginsWithoutPlatformDrafts() throws Exception {
        assertThat(mvc.perform(get(ROOT)).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(send(get(ROOT),actor(1900,7601,Set.of())).getStatus()).isEqualTo(403);
        var viewer=actor(1900,7601,Set.of("config:dictionary:view"));
        var page=data(send(get(ROOT).param("keyword","CONFIG_LAYER_TREE").param("size","1"),viewer));
        assertThat(page.path("total").asText()).isEqualTo("1");var summary=page.path("items").get(0);
        assertThat(summary.path("key").asText()).isEqualTo("config_layer_tree");assertThat(summary.path("mode").asText()).isEqualTo("EXTENDABLE");
        assertThat(summary.at("/revision/platform").isString()).isTrue();
        assertThat(error(send(get(ROOT).param("size","51"),viewer))).isEqualTo("common.error.bad-request");
        var view=data(send(get(ROOT+"/config_layer_tree"),viewer));
        assertThat(entry(view,"child").path("origin").asText()).isEqualTo("PLATFORM");assertThat(view.path("layer").isEmpty()).isTrue();
        assertThat(view.toString()).doesNotContain("draftJson","draftVersion");
        var body=json.writeValueAsString(Map.of("versions",versions(view),"code","child","hidden",true));
        assertThat(send(put(ROOT+"/config_layer_tree/item-overrides").header("Idempotency-Key",key()).content(body),viewer).getStatus()).isEqualTo(403);
    }

    @Test void overrideRestorePrivateAndEnabledUseVersionsAndOwnedReceipts() throws Exception {
        var editor=actor(1901,7602,EDIT);String path=ROOT+"/config_layer_private_codes";
        var before=data(send(get(path),editor));
        String body=json.writeValueAsString(Map.of("versions",versions(before),"code","child",
                "label",Map.of("text","Tenant child","translations",Map.of("en-US","Tenant child EN")),"sort",4));
        assertThat(send(put(path+"/item-overrides").content(body),editor).getStatus()).isEqualTo(400);
        String idempotency=key();var first=send(put(path+"/item-overrides").header("Idempotency-Key",idempotency).content(body),editor);var saved=data(first);
        assertThat(entry(saved,"child").path("label").asText()).isEqualTo("Tenant child EN");assertThat(entry(saved,"child").path("origin").asText()).isEqualTo("TENANT_OVERRIDE");
        assertThat(saved.path("layer").get(0).path("kind").asText()).isEqualTo("OVERRIDE");
        var replay=send(put(path+"/item-overrides").header("Idempotency-Key",idempotency).content(body),editor);
        assertThat(replay.getStatus()).isEqualTo(409);assertThat(error(replay)).isEqualTo("common.request.completed");
        assertThat(json.readTree(replay.getContentAsString()).at("/data/operationId").asText()).isEqualTo(first.getHeader("X-Operation-Id"));
        assertThat(replay.getContentAsString()).doesNotContain("Tenant child");
        assertThat(send(put(path+"/item-overrides").header("Idempotency-Key",idempotency).content(body),actor(1901,7602,Set.of("config:dictionary:view"))).getStatus()).isEqualTo(403);
        Thread.sleep(120);
        assertThat(error(send(put(path+"/item-overrides").header("Idempotency-Key",key()).content(body),editor))).isEqualTo("config.error.shared-stale");
        Thread.sleep(120);
        String exotic="Z\u00e9-\u4e2d\u6587 code";
        var added=data(send(put(path+"/private-items").header("Idempotency-Key",key()).content(json.writeValueAsString(Map.of("versions",versions(saved),
                "code",exotic,"parentCode","root","label",Map.of("text","Private"),"sort",1,"enabled",true))),editor));
        assertThat(entry(added,exotic).path("origin").asText()).isEqualTo("TENANT_ONLY");assertThat(entry(added,exotic).path("selectable").asBoolean()).isTrue();
        Thread.sleep(120);
        assertThat(error(send(put(path+"/private-items").header("Idempotency-Key",key()).content(json.writeValueAsString(Map.of("versions",versions(added),
                "code","root","label",Map.of("text","Taken"),"sort",0,"enabled",true))),editor))).isEqualTo("config.error.dictionary-code-conflict");
        Thread.sleep(120);
        var restored=data(send(post(path+"/item-overrides/restore").header("Idempotency-Key",key()).content(json.writeValueAsString(Map.of("versions",versions(added),"code","child"))),editor));
        assertThat(entry(restored,"child").path("origin").asText()).isEqualTo("PLATFORM");
        Thread.sleep(120);
        var disabled=data(send(put(path+"/enabled").header("Idempotency-Key",key()).content(json.writeValueAsString(Map.of("versions",versions(restored),"enabled",false))),editor));
        assertThat(disabled.path("enabled").asBoolean()).isFalse();
        var options=data(send(get("/api/config/dictionaries/config_layer_private_codes/options"),editor));assertThat(options.isEmpty()).isTrue();
        var other=data(send(get(path),actor(1902,7603,EDIT)));assertThat(other.path("enabled").asBoolean()).isTrue();assertThat(other.path("layer").isEmpty()).isTrue();
    }

    @Test void lockedDictionariesAndLegacyPrivateRoutesCannotBypassTheLayer() throws Exception {
        var editor=actor(1903,7604,EDIT);
        var locked=data(send(get(ROOT+"/config_layer_locked"),editor));
        assertThat(error(send(put(ROOT+"/config_layer_locked/item-overrides").header("Idempotency-Key",key()).content(json.writeValueAsString(Map.of(
                "versions",versions(locked),"code","a","hidden",true))),editor))).isEqualTo("config.error.shared-invalid");
        Thread.sleep(120);
        String legacy=json.writeValueAsString(Map.of("typeKey","config_layer_tree","name","Copy","tree",true,"enabled",true,"sort",0));
        assertThat(error(send(post("/api/config/dictionaries/types").header("Idempotency-Key",key()).content(legacy),editor))).isEqualTo("config.error.shared-invalid");
        Thread.sleep(120);
        assertThat(error(send(put(ROOT+"/config_layer_tree/item-overrides").header("Idempotency-Key",key()).content(json.writeValueAsString(Map.of(
                "versions",versions(locked),"code","child"))),editor))).isIn("config.error.shared-invalid","config.error.shared-stale");
    }
}

