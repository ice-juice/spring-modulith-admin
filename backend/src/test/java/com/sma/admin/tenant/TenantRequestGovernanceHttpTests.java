package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;
import com.sma.admin.tenant.internal.service.BrandDraftService;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest @AutoConfigureMockMvc
@TestPropertySource(properties={"sma.security.requests.fingerprint-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "sma.security.requests.minimum-write-interval=100ms","sma.tenant.platform-admin-user-ids=991"})
class TenantRequestGovernanceHttpTests {
    @Autowired MockMvc mvc;@Autowired JsonMapper json;@Autowired BrandDraftService drafts;
    private static final Set<String> DIRECTORY=Set.of("tenant:tenant:view","tenant:tenant:create","tenant:tenant:update","tenant:domain:create","tenant:domain:view");
    private static final Set<String> BRAND=Set.of("tenant:brand:view","tenant:brand:edit","tenant:brand:publish","tenant:brand:rollback");
    private SmaPrincipal actor(long user,long tenant,Set<String> permissions){return new SmaPrincipal(user,"operator",tenant,"admin",null,false,DataAccess.unrestricted(),permissions,"UTC");}
    @Test void platformDirectoryRequiresDeploymentIdentityAndDuplicatesHaveOnlyOwnedReceipts()throws Exception{
        var operator=actor(991,1,DIRECTORY);String key=UUID.randomUUID().toString();
        String body=json.writeValueAsString(Map.of("code","secured-"+key.substring(0,8),"name","Governed tenant"));
        assertThat(send(post("/api/tenant/tenants").content(body),operator).getStatus()).isEqualTo(400);
        var first=send(post("/api/tenant/tenants").header("Idempotency-Key",key).content(body),operator);
        assertThat(first.getStatus()).as(first.getContentAsString()).isEqualTo(200);
        String id=json.readTree(first.getContentAsString()).at("/data/id").asText();assertThat(id).isNotBlank();
        var replay=send(post("/api/tenant/tenants").header("Idempotency-Key",key).content(body),operator);
        assertThat(replay.getStatus()).isEqualTo(409);assertThat(json.readTree(replay.getContentAsString()).at("/data/state").asText()).isEqualTo("SUCCEEDED");
        assertThat(replay.getContentAsString()).doesNotContain("Governed tenant");
        assertThat(send(post("/api/tenant/tenants").header("Idempotency-Key",key).content(body),actor(991,79901,DIRECTORY)).getStatus()).isEqualTo(403);
        assertThat(send(post("/api/tenant/tenants").header("Idempotency-Key",key).content(body),actor(992,1,DIRECTORY)).getStatus()).isEqualTo(403);
        assertThat(send(post("/api/tenant/tenants").header("Idempotency-Key",key).content(body),actor(991,1,Set.of())).getStatus()).isEqualTo(403);
        String statusKey=UUID.randomUUID().toString(),statusBody="{\"status\":\"ACTIVE\",\"expectedGeneration\":1}";
        assertThat(send(put("/api/tenant/tenants/"+id+"/status").header("Idempotency-Key",statusKey).content(statusBody),operator).getStatus()).isEqualTo(200);
        var statusReplay=send(put("/api/tenant/tenants/"+id+"/status").header("Idempotency-Key",statusKey).content(statusBody),operator);
        assertThat(statusReplay.getStatus()).isEqualTo(409);
        String domainKey=UUID.randomUUID().toString(),domain=json.writeValueAsString(Map.of("tenantId",id,"hostname","new-"+key.substring(0,8)+".example.test","portal","admin"));
        var registered=send(post("/api/tenant/domains").header("Idempotency-Key",domainKey).content(domain),operator);
        assertThat(registered.getStatus()).as(registered.getContentAsString()).isEqualTo(200);
        assertThat(json.readTree(registered.getContentAsString()).at("/code").asInt()).isZero();
        assertThat(send(post("/api/tenant/domains").header("Idempotency-Key",domainKey).content(domain),operator).getStatus()).isEqualTo(409);
    }
    @Test void draftUsesLocalReceiptButPublishUsesNativeImmutableRevisionAndPreviewDoesNotWrite()throws Exception{
        var actor=actor(101,79902,BRAND);String key=UUID.randomUUID().toString();
        String draft=json.writeValueAsString(Map.of("expectedVersion",0,"content",BrandContent.defaults("Governed brand")));
        var first=send(put("/api/tenant/brands/draft").header("Idempotency-Key",key).content(draft),actor);
        assertThat(first.getStatus()).as(first.getContentAsString()).isEqualTo(200);assertThat(first.getHeader("X-Operation-Id")).isNotBlank();
        assertThat(send(put("/api/tenant/brands/draft").header("Idempotency-Key",key).content(draft),actor).getStatus()).isEqualTo(409);
        String op=UUID.randomUUID().toString(),publish=json.writeValueAsString(Map.of("operationId",op,"expectedDraftVersion",1));
        var publication=send(post("/api/tenant/brands/publications").content(publish),actor);
        assertThat(publication.getStatus()).as(publication.getContentAsString()).isEqualTo(200);
        assertThat(publication.getHeader("X-Request-Policy")).isEqualTo("NATIVE");assertThat(publication.getHeader("X-Operation-Id")).isNull();
        String revision=json.readTree(publication.getContentAsString()).at("/data/revisionId").asText();assertThat(revision).isNotBlank();
        Thread.sleep(150);
        var repeated=send(post("/api/tenant/brands/publications").content(publish),actor);
        assertThat(repeated.getStatus()).as(repeated.getContentAsString()).isEqualTo(200);
        assertThat(json.readTree(repeated.getContentAsString()).at("/data/revisionId").asText()).isEqualTo(revision);
        var preview=send(post("/api/tenant/brands/previews").content(draft.replace("\"expectedVersion\":0","\"expectedVersion\":1")),actor);
        assertThat(preview.getStatus()).as(preview.getContentAsString()).isEqualTo(200);assertThat(preview.getHeader("X-Request-Policy")).isEqualTo("QUERY");
        Users.as(actor,()->{assertThat(drafts.get().version()).isEqualTo(1);assertThat(drafts.get().publishedRevisionId()).isEqualTo(Long.parseLong(revision));return null;});
    }
    private org.springframework.mock.web.MockHttpServletResponse send(MockHttpServletRequestBuilder request,SmaPrincipal actor)throws Exception{
        return mvc.perform(request.contentType("application/json").with(Users.request(actor))).andReturn().getResponse();
    }
}
