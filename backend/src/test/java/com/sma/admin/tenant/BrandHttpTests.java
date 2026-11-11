package com.sma.admin.tenant;
import static org.assertj.core.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;
import com.sma.admin.tenant.internal.service.BrandDraftService;

@IntegrationTest @AutoConfigureMockMvc
class BrandHttpTests {
    @Autowired MockMvcTester mvc;
    @Autowired BrandDraftService drafts;
    @Autowired JsonMapper json;
    @Test void previewIsAuthenticatedNoStoreAndDoesNotCreateADraft() {
        assertThat(mvc.post().uri("/api/tenant/brands/previews").contentType(MediaType.APPLICATION_JSON).content("{}")).hasStatus(401);
        assertThat(mvc.get().uri("/api/tenant/brands/operations/missing-operation-01")).hasStatus(401);
        var viewer=new SmaPrincipal(849,"viewer",7449,"admin",null,false,DataAccess.own(),Set.of("tenant:brand:view"),"UTC");
        Users.as(viewer,()->{
            assertThat(mvc.post().uri("/api/tenant/brands/previews").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(java.util.Map.of("expectedVersion",0,"content",BrandContent.defaults("Transient preview")))))
                    .hasStatusOk().hasHeader("Cache-Control","no-store").bodyJson().hasPathSatisfying("$.data.content.identity.systemName",v->v.assertThat().isEqualTo("Transient preview"));
            return null;
        });
        Users.as(viewer,()->{assertThat(drafts.get().version()).isZero();assertThat(drafts.get().publishedRevisionId()).isNull();return null;});
        Users.as(viewer,()->{
            assertThat(mvc.get().uri("/api/tenant/brands/operations/missing-operation-01")).hasStatusOk().bodyJson()
                    .hasPathSatisfying("$.data.status",v->v.assertThat().isEqualTo("NOT_OBSERVED"));return null;
        });
        var forbidden=new SmaPrincipal(850,"forbidden",7449,"admin",null,false,DataAccess.own(),Set.of(),"UTC");
        Users.as(forbidden,()->{
            assertThat(mvc.post().uri("/api/tenant/brands/previews").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(java.util.Map.of("expectedVersion",0,"content",BrandContent.defaults("Forbidden preview"))))).hasStatus(403);
            return null;
        });
        Users.as(forbidden,()->{assertThat(mvc.get().uri("/api/tenant/brands/operations/missing-operation-01")).hasStatus(403);return null;});
    }
    @Test void draftAndPublicationHttpPermissionsRemainSeparateAndAnonymousCannotReadDrafts() {
        assertThat(mvc.get().uri("/api/tenant/brands/draft")).hasStatus(401);
        var editor=new SmaPrincipal(841,"editor",7440,"admin",null,false,DataAccess.own(),Set.of("tenant:brand:view","tenant:brand:edit"),"UTC");
        Users.as(editor,()->{drafts.save(0,BrandContent.defaults("Private draft"));
            assertThat(mvc.post().uri("/api/tenant/brands/publications").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"operationId\":\"publication-test-01\",\"expectedDraftVersion\":1,\"expectedPublishedRevisionId\":null}")).hasStatus(403);return null;});
        Users.as(editor,()->{assertThat(mvc.get().uri("/api/tenant/brands/draft")).hasStatusOk().bodyJson().hasPathSatisfying("$.data.content.identity.systemName",v->v.assertThat().isEqualTo("Private draft"));return null;});
        var publisher=new SmaPrincipal(842,"publisher",7440,"admin",null,false,DataAccess.own(),Set.of("tenant:brand:publish"),"UTC");
        Users.as(publisher,()->{assertThat(mvc.post().uri("/api/tenant/brands/publications").contentType(MediaType.APPLICATION_JSON)
                .content("{\"operationId\":\"publication-test-01\",\"expectedDraftVersion\":1,\"expectedPublishedRevisionId\":null}")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.code",v->v.assertThat().isEqualTo(0)).hasPath("$.data.revisionId");return null;});
        Users.as(publisher,()->{assertThat(mvc.put().uri("/api/tenant/brands/draft").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("expectedVersion",1,"content",BrandContent.defaults("Forbidden edit"))))).hasStatus(403);return null;});
    }
}
