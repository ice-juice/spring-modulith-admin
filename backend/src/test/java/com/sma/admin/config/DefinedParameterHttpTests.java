package com.sma.admin.config;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.util.*;
import java.util.concurrent.*;
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
import com.sma.admin.common.PageQuery;
import com.sma.admin.common.security.*;
import com.sma.admin.config.internal.persistence.ParameterMapper;
import com.sma.admin.config.internal.persistence.ParameterEntity;
import com.sma.admin.config.internal.service.*;
import com.sma.admin.config.internal.service.ConfigurationCatalogRegistry.Kind;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc @Import(DefinedParameterTests.CatalogConfiguration.class)
@TestPropertySource(properties={"sma.tenant.platform-admin-user-ids=991","sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","sma.security.requests.minimum-write-interval=100ms"})
class DefinedParameterHttpTests {
    private static final String ROOT="/api/config/effective-parameters";
    private static final Set<String> EDIT=Set.of("config:parameter:view","config:parameter:update","config:parameter:create");
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired SharedConfigurationService shared;
    @Autowired DefinedParameterService layers;
    @Autowired ParameterMapper rows;
    @Autowired ConfigNames names;
    private SmaPrincipal actor(long user,long tenant,Set<String> permissions) {return new SmaPrincipal(user,"effective-http",tenant,"admin",null,false,DataAccess.unrestricted(),permissions,"UTC");}
    private MockHttpServletResponse send(MockHttpServletRequestBuilder request,SmaPrincipal actor) throws Exception {
        return mvc.perform(request.contentType("application/json").header("Accept-Language","en-US").with(Users.request(actor))).andReturn().getResponse();
    }
    private JsonNode data(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        var body=json.readTree(response.getContentAsString());assertThat(body.path("code").asInt()).as(response.getContentAsString()).isZero();return body.path("data");
    }
    private String input(JsonNode before,String value) {
        var body=new LinkedHashMap<String,Object>();body.put("expectedPlatformRevision",before.at("/revision/platform").asText());
        body.put("expectedTenantRevision",before.at("/revision/tenant").asText());body.put("value",value);return json.writeValueAsString(body);
    }
    private String inheritInput(JsonNode before) {return json.writeValueAsString(Map.of("expectedPlatformRevision",before.at("/revision/platform").asText(),"expectedTenantRevision",before.at("/revision/tenant").asText()));}
    private String error(MockHttpServletResponse response) throws Exception {return json.readTree(response.getContentAsString()).path("error").asText();}

    @Test void directoryRequiresPermissionIsBoundedAndNeverReadsPrivateRows() throws Exception {
        assertThat(mvc.perform(get(ROOT)).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(send(get(ROOT),actor(1800,7310,Set.of())).getStatus()).isEqualTo(403);
        var viewer=actor(1800,7310,Set.of("config:parameter:view"));
        var page=data(send(get(ROOT).param("keyword","CONFIG.LAYERS.CACHED").param("size","1"),viewer));
        assertThat(page.path("items").size()).isEqualTo(1);assertThat(page.path("total").asText()).isEqualTo("1");
        var item=page.path("items").get(0);assertThat(item.path("key").asText()).isEqualTo("config.layers.cached");
        assertThat(item.path("origin").asText()).isEqualTo("PLATFORM");assertThat(item.path("name").asText()).isEqualTo("English");
        assertThat(item.at("/revision/platform").isString()).isTrue();assertThat(item.at("/revision/tenant").isString()).isTrue();
        assertThat(data(send(get(ROOT).param("keyword","customer.privateCredential"),viewer)).path("items").isEmpty()).isTrue();
        assertThat(error(send(get(ROOT).param("size","51"),viewer))).isEqualTo("common.error.bad-request");
        assertThat(send(put(ROOT+"/config.layers.cached/override").header("Idempotency-Key",UUID.randomUUID().toString()).content(input(item,"denied")),viewer).getStatus()).isEqualTo(403);
    }

    @Test void explicitNullRequiresItsFieldAndVersionsWhileReplayAndRestoreKeepOwnedReceipts() throws Exception {
        var editor=actor(1801,7311,EDIT);String path=ROOT+"/config.layers.cached";
        var before=data(send(get(path),editor));String payload=input(before,null);
        assertThat(send(put(path+"/override").header("Idempotency-Key",UUID.randomUUID().toString()).content(inheritInput(before)),editor).getStatus()).isEqualTo(400);
        assertThat(send(put(path+"/override").header("Idempotency-Key",UUID.randomUUID().toString()).content("{\"value\":null}"),editor).getStatus()).isEqualTo(400);
        assertThat(send(put(path+"/override").content(payload),editor).getStatus()).isEqualTo(400);
        String key=UUID.randomUUID().toString();
        var response=send(put(path+"/override").header("Idempotency-Key",key).content(payload),editor);var saved=data(response);
        assertThat(saved.path("value").isNull()).isTrue();assertThat(saved.path("overridden").asBoolean()).isTrue();
        assertThat(saved.path("name").asText()).isEqualTo("English"); // A value edit must not erase platform translations.
        assertThat(saved.path("rowId").isString()).isTrue();String operation=response.getHeader("X-Operation-Id");assertThat(operation).isNotBlank();
        var repeat=send(put(path+"/override").header("Idempotency-Key",key).content(payload),editor);
        assertThat(repeat.getStatus()).isEqualTo(409);assertThat(error(repeat)).isEqualTo("common.request.completed");
        assertThat(json.readTree(repeat.getContentAsString()).at("/data/operationId").asText()).isEqualTo(operation);
        assertThat(json.readTree(repeat.getContentAsString()).at("/data/value").isMissingNode()).isTrue();
        assertThat(send(put(path+"/override").header("Idempotency-Key",key).content(payload),actor(1801,7311,Set.of("config:parameter:view"))).getStatus()).isEqualTo(403);
        var receipt=data(send(post("/api/common/requests/lookup").content(json.writeValueAsString(Map.of("key",key))),editor));assertThat(receipt.path("state").asText()).isEqualTo("SUCCEEDED");
        var other=data(send(get(path),actor(1802,7312,EDIT)));assertThat(other.path("value").asText()).isEqualTo("seed");assertThat(other.path("rowId").isNull()).isTrue();
        Thread.sleep(120);
        var restored=data(send(post(path+"/inherit").header("Idempotency-Key",UUID.randomUUID().toString()).content(inheritInput(saved)),editor));
        assertThat(restored.path("origin").asText()).isEqualTo("PLATFORM");assertThat(restored.path("rowId").asText()).isEqualTo(saved.path("rowId").asText());
        Thread.sleep(120);
        assertThat(error(send(put(path+"/override").header("Idempotency-Key",UUID.randomUUID().toString()).content(input(before,"stale")),editor))).isEqualTo("config.error.shared-stale");
    }

    @Test void lockedDefinitionsChangedPlatformVersionsAndLegacyWriteRoutesCannotBypassTheContract() throws Exception {
        var editor=actor(1803,7313,EDIT);String lockedPath=ROOT+"/config.layers.locked";
        var locked=data(send(get(lockedPath),editor));
        assertThat(error(send(put(lockedPath+"/override").header("Idempotency-Key",UUID.randomUUID().toString()).content(input(locked,"bad")),editor))).isEqualTo("config.error.shared-invalid");
        String key="config.layers.publication";var before=data(send(get(ROOT+"/"+key),editor));
        Users.as(actor(991,1,Set.of("config:shared:view","config:shared:edit","config:shared:publish")),()->{
            var header=shared.page(new PageQuery(1,20,null,null),Kind.PARAMETER,key).items().getFirst();
            var draft=shared.saveParameter(header.id(),header.draftVersion(),new SharedConfigurationService.ParameterContent(1,new ConfigurationLabel("New label",null,Map.of()),"new shared"));
            shared.publish(header.id(),draft.draftVersion(),draft.publishedRevisionId());return null;
        });
        Thread.sleep(120);
        assertThat(error(send(put(ROOT+"/"+key+"/override").header("Idempotency-Key",UUID.randomUUID().toString()).content(input(before,"old edit")),editor))).isEqualTo("config.error.shared-stale");
        Thread.sleep(120);
        String legacy=json.writeValueAsString(Map.of("paramKey",key,"groupKey","basic","name","Bypass","valueType","STRING","value","bypass","publicReadable",false,"secret",false,"sort",0));
        assertThat(error(send(post("/api/config/parameters").header("Idempotency-Key",UUID.randomUUID().toString()).content(legacy),editor))).isEqualTo("config.error.shared-invalid");
        assertThat(data(send(get(ROOT+"/"+key),editor)).path("value").asText()).isEqualTo("new shared");
    }

    @Test void migratedPresentationIsRetainedInTheEffectiveManagementView() throws Exception {
        var editor=actor(1804,7314,EDIT);String key="config.layers.pinned";
        long id=Users.as(editor,()->{
            var row=new ParameterEntity();row.setParamKey(key);row.setGroupKey("old-group");row.setName("Legacy custom");row.setValueType("STRING");row.setParamValue("retained");
            row.setBuiltin(true);row.setSecret(false);row.setPublicReadable(false);row.setSort(19);row.setBrandArchived(false);rows.insert(row);
            names.replace("parameter",row.getId(),Map.of("en-US","Legacy translation"));return row.getId();
        });
        try(var pool=Executors.newSingleThreadExecutor()){assertThat(pool.submit(()->TenantScope.within(7314,layers::migrateCurrentTenant)).get(30,TimeUnit.SECONDS)).isEqualTo(1);}
        var view=data(send(get(ROOT+"/"+key),editor));assertThat(view.path("name").asText()).isEqualTo("Legacy translation");
        assertThat(view.path("sort").asInt()).isEqualTo(19);assertThat(view.path("rowId").asText()).isEqualTo(Long.toString(id));
        assertThat(view.path("value").asText()).isEqualTo("retained");assertThat(view.path("origin").asText()).isEqualTo("TENANT_OVERRIDE");
    }
}
